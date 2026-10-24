package io.vobs.consent;

/**
 * Yêu cầu consent bị một quy tắc riêng của ngân hàng trong bảng {@code consent_request} từ chối (xem
 * {@link ConsentPolicy}).
 */
public class ConsentRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;

    public ConsentRejectedException(String reasonCode, String ruleVersion) {
        super("Yêu cầu consent bị từ chối theo chính sách %s (bảng %s %s)".formatted(reasonCode,
                ConsentPolicy.DECISION_ID, ruleVersion));
        this.reasonCode = reasonCode;
    }

    /** Mã lý do của quy tắc đã từ chối, dạng {@code UPPER_SNAKE_CASE}. */
    public String reasonCode() {
        return reasonCode;
    }
}
