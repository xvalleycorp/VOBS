package io.vobs.switching;

import io.vobs.model.BankId;
import io.vobs.model.Money;
import java.util.Objects;

/**
 * Lệnh chuyển khoản gửi tới chuyển mạch.
 *
 * @param reference mã tham chiếu do ngân hàng gửi cấp, dùng làm khóa idempotency
 * @param amount số tiền
 * @param debtorBank ngân hàng người chuyển
 * @param debtorAccount số tài khoản người chuyển
 * @param debtorName tên người chuyển
 * @param creditorBank ngân hàng người nhận
 * @param creditorAccount số tài khoản người nhận
 * @param purpose nội dung chuyển khoản, có thể {@code null}
 */
public record TransferRequest(
        String reference,
        Money amount,
        BankId debtorBank,
        String debtorAccount,
        String debtorName,
        BankId creditorBank,
        String creditorAccount,
        String purpose) {

    public TransferRequest {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(debtorBank, "debtorBank");
        Objects.requireNonNull(debtorAccount, "debtorAccount");
        Objects.requireNonNull(debtorName, "debtorName");
        Objects.requireNonNull(creditorBank, "creditorBank");
        Objects.requireNonNull(creditorAccount, "creditorAccount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Số tiền chuyển phải dương");
        }
    }
}
