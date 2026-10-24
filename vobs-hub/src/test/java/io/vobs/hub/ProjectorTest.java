package io.vobs.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vobs.model.Account;
import io.vobs.model.BankId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectorTest {

    private static final String ACCOUNTS = "CORE.ACCOUNTS";

    private final DebeziumEventParser parser = new DebeziumEventParser();
    private final ReadStore<Account> store = ReadStore.inMemory();
    private final Projector<Account> projector = new Projector<>(new AccountRowMapper(
            ACCOUNTS,
            BankId.ofBin("970436"),
            new AccountRowMapper.Columns("ACC_ID", "ACC_NO", "CIF", "PROD", "ACC_TYPE", "CCY", "STATUS", "OPEN_DT",
                    DateTimeFormatter.BASIC_ISO_DATE),
            Map.of("CA", Account.AccountType.CURRENT, "SA", Account.AccountType.SAVINGS),
            Map.of("A", Account.AccountStatus.ACTIVE, "C", Account.AccountStatus.CLOSED)),
            store);

    private static String event(String op, String before, String after, long tsMs) {
        return """
                {"schema": {"type": "struct"},
                 "payload": {"op": "%s", "before": %s, "after": %s, "ts_ms": %d,
                             "source": {"schema": "CORE", "table": "ACCOUNTS", "ts_ms": %d}}}
                """.formatted(op, before, after, tsMs + 50, tsMs);
    }

    private static String row(String status) {
        return """
                {"ACC_ID": "1001", "ACC_NO": "0011004567890 ", "CIF": "C001", "PROD": "CA01",
                 "ACC_TYPE": "CA", "CCY": "VND", "STATUS": "%s", "OPEN_DT": "20200115"}
                """.formatted(status);
    }

    private boolean apply(String json) {
        return projector.apply(parser.parse(json).orElseThrow());
    }

    @Test
    void projectsSnapshotAndUpdates() {
        assertTrue(apply(event("r", "null", row("A"), 1_000)));
        Account account = store.get("1001").orElseThrow().value();
        assertEquals("0011004567890", account.accountNumber());
        assertEquals(Account.AccountType.CURRENT, account.type());
        assertEquals(LocalDate.of(2020, 1, 15), account.openedOn());

        assertTrue(apply(event("u", row("A"), row("C"), 2_000)));
        assertEquals(Account.AccountStatus.CLOSED, store.get("1001").orElseThrow().value().status());
        assertEquals(Instant.ofEpochMilli(2_000), store.get("1001").orElseThrow().committedAt());
    }

    @Test
    void ignoresOutOfOrderReplay() {
        apply(event("u", row("A"), row("C"), 2_000));
        assertFalse(apply(event("r", "null", row("A"), 1_000)));
        assertEquals(Account.AccountStatus.CLOSED, store.get("1001").orElseThrow().value().status());
    }

    @Test
    void deletesRow() {
        apply(event("c", "null", row("A"), 1_000));
        assertTrue(apply(event("d", row("A"), "null", 3_000)));
        assertTrue(store.get("1001").isEmpty());
    }

    @Test
    void skipsTombstonesAndOtherTables() {
        assertTrue(parser.parse(null).isEmpty());
        ChangeEvent other = new ChangeEvent(ChangeEvent.Operation.CREATE, "CORE.CUSTOMERS", null,
                Map.of("CIF", "C001"), Instant.EPOCH);
        assertFalse(projector.apply(other));
    }

    @Test
    void parsesPayloadOnlyEnvelopeWithoutSchemaName() {
        ChangeEvent parsed = parser.parse("""
                {"op": "c", "before": null, "after": {"ID": 1}, "source": {"table": "ACCOUNTS", "ts_ms": 5}}
                """).orElseThrow();
        assertEquals("ACCOUNTS", parsed.table());
        assertEquals(1, parsed.after().get("ID"));
    }

    @Test
    void failsLoudlyOnUnmappedCode() {
        String unknownType = row("A").replace("\"CA\"", "\"XX\"");
        assertThrows(IllegalArgumentException.class, () -> apply(event("c", "null", unknownType, 1_000)));
    }
}
