package io.vobs.consent;

/**
 * Phạm vi mà một consent cho phép, theo nhóm Open API cơ bản tại Điều 6 khoản 1 Thông tư 64/2024/TT-NHNN.
 *
 * <p>Nhóm truy vấn tỷ giá và lãi suất (điểm a) là thông tin công khai của ngân hàng nên không cần consent.
 */
public enum ConsentScope {

    /** Điểm b: API Lấy danh sách tài khoản, API Lấy thông tin tài khoản. */
    ACCOUNTS_READ(false),

    /** Điểm b: API Lấy lịch sử giao dịch. */
    TRANSACTIONS_READ(false),

    /** Điểm c(i): Open API khởi tạo thanh toán. */
    PAYMENTS_INITIATE(true),

    /** Điểm c(ii): Open API nạp tiền vào ví điện tử. */
    EWALLET_TOPUP(true),

    /** Điểm c(iii): Open API rút tiền ra khỏi ví điện tử. */
    EWALLET_WITHDRAWAL(true);

    private final boolean paymentGroup;

    ConsentScope(boolean paymentGroup) {
        this.paymentGroup = paymentGroup;
    }

    /**
     * Thuộc nhóm điểm c, chỉ được cấp cho bên thứ ba là ngân hàng hoặc tổ chức cung ứng dịch vụ trung gian thanh
     * toán (Điều 5).
     */
    public boolean isPaymentGroup() {
        return paymentGroup;
    }
}
