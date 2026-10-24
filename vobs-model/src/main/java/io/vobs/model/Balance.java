package io.vobs.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Số dư tài khoản tại một thời điểm.
 *
 * @param accountId tài khoản
 * @param type loại số dư
 * @param amount số tiền
 * @param asOf thời điểm số dư được ghi nhận ở core; kho đọc có thể trễ hơn core một khoảng nhỏ
 */
public record Balance(String accountId, BalanceType type, Money amount, Instant asOf) {

    public enum BalanceType {
        /** Số dư sổ sách đã hạch toán. */
        BOOKED,
        /** Số dư khả dụng, đã trừ các khoản phong tỏa và tạm giữ. */
        AVAILABLE
    }

    public Balance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(asOf, "asOf");
    }
}
