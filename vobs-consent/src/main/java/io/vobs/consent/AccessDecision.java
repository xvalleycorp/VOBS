package io.vobs.consent;

/**
 * Kết quả kiểm tra quyền truy cập theo consent, trả cho gateway.
 *
 * @param allowed được phép hay không
 * @param reason lý do từ chối, {@code null} khi được phép
 */
public record AccessDecision(boolean allowed, String reason) {

    static final AccessDecision ALLOW = new AccessDecision(true, null);

    static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason);
    }
}
