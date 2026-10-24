package io.vobs.iso20022;

import io.vobs.model.BankId;
import io.vobs.model.Money;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Lệnh chuyển khoản VND giữa hai ngân hàng trong nước, dạng trung lập trước khi dựng thành pacs.008.
 *
 * @param messageId mã thông điệp, tối đa 35 ký tự
 * @param endToEndId mã đầu-cuối do bên khởi tạo cấp, tối đa 35 ký tự
 * @param createdAt thời điểm tạo thông điệp
 * @param settlementDate ngày thanh toán liên ngân hàng
 * @param amount số tiền, phải là VND
 * @param debtorName tên người chuyển
 * @param debtorAccount số tài khoản người chuyển
 * @param debtorBank ngân hàng người chuyển
 * @param creditorName tên người nhận
 * @param creditorAccount số tài khoản người nhận
 * @param creditorBank ngân hàng người nhận
 * @param remittanceInfo nội dung chuyển khoản, có thể {@code null}
 */
public record DomesticCreditTransfer(
        String messageId,
        String endToEndId,
        OffsetDateTime createdAt,
        LocalDate settlementDate,
        Money amount,
        String debtorName,
        String debtorAccount,
        BankId debtorBank,
        String creditorName,
        String creditorAccount,
        BankId creditorBank,
        String remittanceInfo) {

    public DomesticCreditTransfer {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(endToEndId, "endToEndId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(settlementDate, "settlementDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(debtorName, "debtorName");
        Objects.requireNonNull(debtorAccount, "debtorAccount");
        Objects.requireNonNull(debtorBank, "debtorBank");
        Objects.requireNonNull(creditorName, "creditorName");
        Objects.requireNonNull(creditorAccount, "creditorAccount");
        Objects.requireNonNull(creditorBank, "creditorBank");
        if (!Money.VND.equals(amount.currency()) || !amount.isPositive()) {
            throw new IllegalArgumentException("Chuyển khoản nội địa phải là số tiền VND dương: " + amount);
        }
    }
}
