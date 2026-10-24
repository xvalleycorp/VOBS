package io.vobs.consent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Vòng đời consent: đối tác yêu cầu, khách hàng duyệt hoặc từ chối, khách hàng hoặc đối tác thu hồi, tự hết hạn.
 * Mọi lần đổi trạng thái đều ghi vào {@link AuditLog}.
 *
 * <pre>
 * AWAITING_AUTHORISATION --authorise--> AUTHORISED --revoke--> REVOKED
 *          |                                 |
 *          +--reject--> REJECTED             +--(hết hạn)--> EXPIRED
 * </pre>
 */
public final class ConsentService {

    private final ConsentRepository repository;
    private final AuditLog auditLog;
    private final Clock clock;
    private final Duration maxValidity;

    public ConsentService(ConsentRepository repository, AuditLog auditLog, Clock clock, Duration maxValidity) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.clock = clock;
        this.maxValidity = maxValidity;
    }

    /** Đối tác gửi yêu cầu consent; khách hàng duyệt sau qua kênh của ngân hàng. */
    public Consent request(String clientId, String customerId, Set<ConsentScope> scopes, Duration validity) {
        if (validity.isNegative() || validity.isZero() || validity.compareTo(maxValidity) > 0) {
            throw new IllegalArgumentException("Thời hạn consent phải trong khoảng (0, %s]".formatted(maxValidity));
        }
        Instant now = clock.instant();
        Consent consent = new Consent(UUID.randomUUID().toString(), clientId, customerId, scopes, Set.of(), now,
                now.plus(validity), Consent.Status.AWAITING_AUTHORISATION, now);
        repository.save(consent);
        auditLog.append(now, "client:" + clientId, "consent.requested", consent.consentId());
        return consent;
    }

    /** Khách hàng duyệt consent và chọn các tài khoản được chia sẻ. */
    public Consent authorise(String consentId, String customerId, Set<String> accountIds) {
        Consent consent = requireOwnedBy(consentId, customerId);
        requireStatus(consent, Consent.Status.AWAITING_AUTHORISATION);
        boolean paymentOnly = consent.scopes().equals(Set.of(ConsentScope.PAYMENTS_INITIATE));
        if (accountIds.isEmpty() && !paymentOnly) {
            throw new IllegalArgumentException("Cần chọn ít nhất một tài khoản để chia sẻ dữ liệu");
        }
        return transition(consent, Consent.Status.AUTHORISED, accountIds, "customer:" + customerId,
                "consent.authorised");
    }

    public Consent reject(String consentId, String customerId) {
        Consent consent = requireOwnedBy(consentId, customerId);
        requireStatus(consent, Consent.Status.AWAITING_AUTHORISATION);
        return transition(consent, Consent.Status.REJECTED, consent.accountIds(), "customer:" + customerId,
                "consent.rejected");
    }

    /** Khách hàng thu hồi consent của mình. */
    public Consent revokeByCustomer(String consentId, String customerId) {
        return revoke(requireOwnedBy(consentId, customerId), "customer:" + customerId);
    }

    /** Đối tác tự hủy consent đã được cấp cho mình. */
    public Consent revokeByClient(String consentId, String clientId) {
        Consent consent = require(consentId);
        if (!consent.clientId().equals(clientId)) {
            throw new NoSuchElementException("Không tìm thấy consent " + consentId);
        }
        return revoke(consent, "client:" + clientId);
    }

    /** Lấy consent; nếu đã quá hạn thì chuyển sang {@code EXPIRED} trước khi trả về. */
    public Optional<Consent> find(String consentId) {
        return repository.find(consentId).map(this::expireIfDue);
    }

    /** Gateway gọi trước mỗi request của đối tác. */
    public AccessDecision check(String consentId, String clientId, ConsentScope scope, String accountId) {
        Optional<Consent> found = find(consentId);
        if (found.isEmpty() || !found.get().clientId().equals(clientId)) {
            return AccessDecision.deny("consent_not_found");
        }
        Consent consent = found.get();
        if (consent.status() != Consent.Status.AUTHORISED) {
            return AccessDecision.deny("consent_" + consent.status().name().toLowerCase());
        }
        if (!consent.scopes().contains(scope)) {
            return AccessDecision.deny("scope_not_granted");
        }
        if (accountId != null && !consent.accountIds().contains(accountId)) {
            return AccessDecision.deny("account_not_granted");
        }
        return AccessDecision.ALLOW;
    }

    private Consent revoke(Consent consent, String actor) {
        Consent current = expireIfDue(consent);
        if (current.status().isTerminal()) {
            throw new IllegalStateException("Consent %s đã ở trạng thái %s".formatted(current.consentId(),
                    current.status()));
        }
        return transition(current, Consent.Status.REVOKED, current.accountIds(), actor, "consent.revoked");
    }

    private Consent expireIfDue(Consent consent) {
        Instant now = clock.instant();
        if (consent.status().isTerminal() || !consent.isExpiredAt(now)) {
            return consent;
        }
        return transition(consent, Consent.Status.EXPIRED, consent.accountIds(), "system", "consent.expired");
    }

    private Consent transition(Consent consent, Consent.Status status, Set<String> accountIds, String actor,
                               String action) {
        Instant now = clock.instant();
        Consent updated = consent.withStatus(status, accountIds, now);
        repository.save(updated);
        auditLog.append(now, actor, action, consent.consentId());
        return updated;
    }

    private Consent require(String consentId) {
        return find(consentId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy consent " + consentId));
    }

    private Consent requireOwnedBy(String consentId, String customerId) {
        Consent consent = require(consentId);
        if (!consent.customerId().equals(customerId)) {
            // Không tiết lộ consent của khách hàng khác tồn tại hay không
            throw new NoSuchElementException("Không tìm thấy consent " + consentId);
        }
        return consent;
    }

    private static void requireStatus(Consent consent, Consent.Status expected) {
        if (consent.status() != expected) {
            throw new IllegalStateException("Consent %s đang ở trạng thái %s, cần %s".formatted(
                    consent.consentId(), consent.status(), expected));
        }
    }
}
