package io.vobs.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConsentServiceTest {

    /** Đồng hồ chỉnh được để thử hết hạn. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T03:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final AuditLog audit = new AuditLog();
    private final ConsentService service =
            new ConsentService(ConsentRepository.inMemory(), audit, clock, Duration.ofDays(180));

    private Consent authorisedConsent() {
        Consent requested = service.request("fintech-a", "cif-1",
                Set.of(ConsentScope.ACCOUNTS_READ, ConsentScope.BALANCES_READ), Duration.ofDays(90));
        return service.authorise(requested.consentId(), "cif-1", Set.of("acc-1"));
    }

    @Test
    void grantsAccessOnlyWithinScopeAndAccounts() {
        Consent consent = authorisedConsent();
        String id = consent.consentId();

        assertTrue(service.check(id, "fintech-a", ConsentScope.BALANCES_READ, "acc-1").allowed());
        assertEquals("scope_not_granted",
                service.check(id, "fintech-a", ConsentScope.TRANSACTIONS_READ, "acc-1").reason());
        assertEquals("account_not_granted",
                service.check(id, "fintech-a", ConsentScope.BALANCES_READ, "acc-2").reason());
        assertEquals("consent_not_found",
                service.check(id, "fintech-b", ConsentScope.BALANCES_READ, "acc-1").reason());
    }

    @Test
    void pendingConsentGrantsNothing() {
        Consent requested = service.request("fintech-a", "cif-1", Set.of(ConsentScope.ACCOUNTS_READ),
                Duration.ofDays(1));
        assertEquals("consent_awaiting_authorisation",
                service.check(requested.consentId(), "fintech-a", ConsentScope.ACCOUNTS_READ, null).reason());
    }

    @Test
    void customerCanRevoke() {
        Consent consent = authorisedConsent();
        service.revokeByCustomer(consent.consentId(), "cif-1");

        assertEquals("consent_revoked",
                service.check(consent.consentId(), "fintech-a", ConsentScope.BALANCES_READ, "acc-1").reason());
        assertThrows(IllegalStateException.class, () -> service.revokeByClient(consent.consentId(), "fintech-a"));
    }

    @Test
    void otherCustomerCannotSeeOrChangeConsent() {
        Consent requested = service.request("fintech-a", "cif-1", Set.of(ConsentScope.ACCOUNTS_READ),
                Duration.ofDays(1));
        assertThrows(NoSuchElementException.class,
                () -> service.authorise(requested.consentId(), "cif-2", Set.of("acc-9")));
    }

    @Test
    void expiresAutomatically() {
        Consent consent = authorisedConsent();
        clock.advance(Duration.ofDays(90));

        assertEquals("consent_expired",
                service.check(consent.consentId(), "fintech-a", ConsentScope.BALANCES_READ, "acc-1").reason());
        assertEquals(Consent.Status.EXPIRED, service.find(consent.consentId()).orElseThrow().status());
    }

    @Test
    void enforcesValidityAndAccountSelection() {
        assertThrows(IllegalArgumentException.class, () -> service.request("fintech-a", "cif-1",
                Set.of(ConsentScope.ACCOUNTS_READ), Duration.ofDays(181)));
        Consent requested = service.request("fintech-a", "cif-1", Set.of(ConsentScope.ACCOUNTS_READ),
                Duration.ofDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> service.authorise(requested.consentId(), "cif-1", Set.of()));

        Consent paymentOnly = service.request("fintech-a", "cif-1", Set.of(ConsentScope.PAYMENTS_INITIATE),
                Duration.ofDays(1));
        assertEquals(Consent.Status.AUTHORISED,
                service.authorise(paymentOnly.consentId(), "cif-1", Set.of()).status());
    }

    @Test
    void auditsEveryTransitionInAnUnbrokenChain() {
        Consent consent = authorisedConsent();
        service.revokeByClient(consent.consentId(), "fintech-a");

        List<String> actions = audit.entries().stream().map(AuditLog.Entry::action).toList();
        assertEquals(List.of("consent.requested", "consent.authorised", "consent.revoked"), actions);
        assertEquals("client:fintech-a", audit.entries().getLast().actor());
        assertTrue(audit.verify());
    }

    @Test
    void detectsTamperedAuditEntry() {
        authorisedConsent();
        List<AuditLog.Entry> chain = new ArrayList<>(audit.entries());
        AuditLog.Entry first = chain.getFirst();
        chain.set(0, new AuditLog.Entry(first.sequence(), first.at(), "client:attacker", first.action(),
                first.subject(), first.previousHash(), first.hash()));

        assertFalse(AuditLog.verify(chain));
        chain.removeFirst();
        assertFalse(AuditLog.verify(chain));
    }
}
