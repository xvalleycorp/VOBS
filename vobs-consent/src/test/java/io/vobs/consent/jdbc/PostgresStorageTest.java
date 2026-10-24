package io.vobs.consent.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vobs.consent.AuditLog;
import io.vobs.consent.Consent;
import io.vobs.consent.ConsentScope;
import io.vobs.consent.ConsentService;
import io.vobs.consent.ThirdParty;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Kiểm thử lưu trữ trên PostgreSQL thật. Chỉ chạy khi có {@code VOBS_TEST_DATABASE_URL}, ví dụ
 * {@code jdbc:postgresql://localhost:5432/vobs_test?user=vobs}; CI chạy với một service PostgreSQL.
 */
@EnabledIfEnvironmentVariable(named = "VOBS_TEST_DATABASE_URL", matches = ".+")
class PostgresStorageTest {

    private static final ThirdParty FINTECH = new ThirdParty("fintech-a", ThirdParty.Type.OTHER);

    private final PGSimpleDataSource dataSource = new PGSimpleDataSource();

    @BeforeEach
    void cleanDatabase() throws SQLException {
        dataSource.setUrl(System.getenv("VOBS_TEST_DATABASE_URL"));
        ConsentSchema.migrate(dataSource);
        // Chạy lại không lỗi
        ConsentSchema.migrate(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // TRUNCATE không qua trigger chặn sửa, xóa từng dòng; chỉ dùng để dọn dữ liệu kiểm thử
            statement.execute("TRUNCATE consent, consent_audit_log");
        }
    }

    private ConsentService service(Clock clock) {
        return new ConsentService(new PostgresConsentRepository(dataSource),
                new AuditLog(new PostgresAuditStore(dataSource)), clock, ConsentService.TT64_MAX_VALIDITY);
    }

    @Test
    void consentAndAuditSurviveARestart() {
        ConsentService first = service(Clock.systemUTC());
        Consent requested = first.request(FINTECH, Set.of(ConsentScope.ACCOUNTS_READ, ConsentScope.TRANSACTIONS_READ),
                Duration.ofDays(90));
        assertNull(first.find(requested.consentId()).orElseThrow().customerId());
        first.authorise(requested.consentId(), "cif-1", Set.of("acc-1", "acc-2"));

        // Khởi động lại: đối tượng mới, cùng cơ sở dữ liệu
        ConsentService second = service(Clock.systemUTC());
        Consent loaded = second.find(requested.consentId()).orElseThrow();
        assertEquals(Consent.Status.AUTHORISED, loaded.status());
        assertEquals("cif-1", loaded.customerId());
        assertEquals(Set.of("acc-1", "acc-2"), loaded.accountIds());
        assertEquals(Set.of(ConsentScope.ACCOUNTS_READ, ConsentScope.TRANSACTIONS_READ), loaded.scopes());
        assertTrue(second.check(requested.consentId(), "fintech-a", ConsentScope.ACCOUNTS_READ, "acc-2").allowed());

        second.revokeByCustomer(requested.consentId(), "cif-1");
        AuditLog reopened = new AuditLog(new PostgresAuditStore(dataSource));
        List<AuditLog.Entry> entries = reopened.entries();
        assertEquals(List.of("consent.requested", "consent.authorised", "consent.revoked"),
                entries.stream().map(AuditLog.Entry::action).toList());
        assertEquals(List.of(1L, 2L, 3L), entries.stream().map(AuditLog.Entry::sequence).toList());
        assertTrue(reopened.verify(), "chuỗi hash phải khớp sau khi đọc lại từ PostgreSQL");
    }

    @Test
    void listsCustomerConsentsNewestFirstAndExpiresThem() {
        Instant start = Instant.parse("2026-10-24T03:00:00.123456789Z");
        ConsentService early = service(Clock.fixed(start, ZoneOffset.UTC));
        Consent older = early.request(FINTECH, Set.of(ConsentScope.ACCOUNTS_READ), Duration.ofDays(1));
        early.authorise(older.consentId(), "cif-1", Set.of("acc-1"));
        ConsentService later = service(Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC));
        Consent newer = later.request(FINTECH, Set.of(ConsentScope.TRANSACTIONS_READ), Duration.ofDays(1));
        later.authorise(newer.consentId(), "cif-1", Set.of("acc-1"));
        later.request(FINTECH, Set.of(ConsentScope.ACCOUNTS_READ), Duration.ofDays(1));

        assertEquals(List.of(newer.consentId(), older.consentId()),
                later.listForCustomer("cif-1").stream().map(Consent::consentId).toList());

        ConsentService nextWeek = service(Clock.fixed(start.plus(Duration.ofDays(7)), ZoneOffset.UTC));
        assertTrue(nextWeek.listForCustomer("cif-1").stream()
                .allMatch(consent -> consent.status() == Consent.Status.EXPIRED));
        assertTrue(new AuditLog(new PostgresAuditStore(dataSource)).verify());
    }

    @Test
    void auditLogRowsCannotBeChangedOrDeleted() throws SQLException {
        service(Clock.systemUTC()).request(FINTECH, Set.of(ConsentScope.ACCOUNTS_READ), Duration.ofDays(1));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class,
                    () -> statement.execute("UPDATE consent_audit_log SET actor = 'client:attacker'"));
            assertThrows(SQLException.class, () -> statement.execute("DELETE FROM consent_audit_log"));
        }
        assertTrue(new AuditLog(new PostgresAuditStore(dataSource)).verify());
    }
}
