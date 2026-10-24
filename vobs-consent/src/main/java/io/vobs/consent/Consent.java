package io.vobs.consent;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Consent của một khách hàng cho một đối tác (client) truy cập dữ liệu hoặc khởi tạo thanh toán.
 *
 * @param consentId mã consent
 * @param clientId mã client của đối tác, cấp khi đăng ký trên Keycloak
 * @param customerId mã khách hàng cấp consent; {@code null} khi khách hàng chưa đăng nhập ngân hàng để duyệt hay từ
 *     chối (đối tác vừa tạo yêu cầu, hoặc consent đã hết hạn hay bị đối tác thu hồi trước đó)
 * @param scopes các phạm vi dữ liệu được phép
 * @param accountIds các tài khoản được phép; rỗng khi chưa được khách hàng chọn
 * @param createdAt thời điểm đối tác gửi yêu cầu
 * @param expiresAt thời điểm hết hạn
 * @param status trạng thái
 * @param statusChangedAt thời điểm đổi trạng thái gần nhất
 */
public record Consent(
        String consentId,
        String clientId,
        String customerId,
        Set<ConsentScope> scopes,
        Set<String> accountIds,
        Instant createdAt,
        Instant expiresAt,
        Status status,
        Instant statusChangedAt) {

    public enum Status {
        AWAITING_AUTHORISATION,
        AUTHORISED,
        REJECTED,
        REVOKED,
        EXPIRED;

        public boolean isTerminal() {
            return this == REJECTED || this == REVOKED || this == EXPIRED;
        }
    }

    public Consent {
        Objects.requireNonNull(consentId, "consentId");
        Objects.requireNonNull(clientId, "clientId");
        // Chưa có khách hàng được: đối tác vừa tạo, hoặc hết hạn hay bị đối tác thu hồi trước khi khách hàng duyệt
        if (customerId == null && (status == Status.AUTHORISED || status == Status.REJECTED)) {
            throw new IllegalArgumentException("Consent đã được khách hàng duyệt hoặc từ chối phải có customerId");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(statusChangedAt, "statusChangedAt");
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("Consent phải có ít nhất một phạm vi");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Thời điểm hết hạn phải sau thời điểm tạo");
        }
        scopes = Set.copyOf(scopes);
        accountIds = accountIds == null ? Set.of() : Set.copyOf(accountIds);
    }

    Consent withStatus(Status newStatus, Set<String> newAccountIds, Instant at) {
        return new Consent(consentId, clientId, customerId, scopes, newAccountIds, createdAt, expiresAt, newStatus,
                at);
    }

    Consent withCustomer(String newCustomerId) {
        return new Consent(consentId, clientId, newCustomerId, scopes, accountIds, createdAt, expiresAt, status,
                statusChangedAt);
    }

    Consent withStatus(Status newStatus, Instant at) {
        return withStatus(newStatus, accountIds, at);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
