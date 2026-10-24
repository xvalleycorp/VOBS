package io.vobs.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Giao dịch trên tài khoản. Tương ứng bản ghi giao dịch của service domain <em>Current Account</em> của BIAN.
 *
 * @param transactionId mã giao dịch ổn định
 * @param accountId tài khoản phát sinh giao dịch
 * @param direction ghi nợ hay ghi có
 * @param amount số tiền, luôn dương; chiều tiền nằm ở {@code direction}
 * @param status trạng thái hạch toán
 * @param bookedAt thời điểm hạch toán, {@code null} khi chưa hạch toán
 * @param valueDate ngày giá trị
 * @param description nội dung giao dịch
 * @param reference mã tham chiếu (ví dụ mã giao dịch chuyển mạch), có thể {@code null}
 * @param counterparty bên đối ứng, có thể {@code null}
 */
public record Transaction(
        String transactionId,
        String accountId,
        Direction direction,
        Money amount,
        Status status,
        Instant bookedAt,
        LocalDate valueDate,
        String description,
        String reference,
        Counterparty counterparty) {

    public enum Direction { DEBIT, CREDIT }

    public enum Status { PENDING, BOOKED, REVERSED }

    /** Bên đối ứng của giao dịch. */
    public record Counterparty(String name, String accountNumber, BankId bank) {
    }

    public Transaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Số tiền giao dịch phải dương, chiều tiền nằm ở direction");
        }
        if (status == Status.BOOKED && bookedAt == null) {
            throw new IllegalArgumentException("Giao dịch đã hạch toán phải có bookedAt");
        }
    }

    /** Số tiền có dấu: âm với ghi nợ, dương với ghi có. */
    public Money signedAmount() {
        return direction == Direction.DEBIT ? amount.negate() : amount;
    }
}
