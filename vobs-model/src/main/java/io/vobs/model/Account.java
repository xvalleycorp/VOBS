package io.vobs.model;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Tài khoản của khách hàng. Tương ứng service domain <em>Current Account</em> và <em>Savings Account</em> của BIAN.
 *
 * @param accountId mã định danh ổn định dùng trong API, không phải số tài khoản
 * @param accountNumber số tài khoản
 * @param bank ngân hàng giữ tài khoản
 * @param customerId mã khách hàng chủ tài khoản
 * @param productId mã sản phẩm, xem {@link Product}
 * @param type loại tài khoản
 * @param currency loại tiền của tài khoản
 * @param status trạng thái
 * @param openedOn ngày mở
 */
public record Account(
        String accountId,
        String accountNumber,
        BankId bank,
        String customerId,
        String productId,
        AccountType type,
        Currency currency,
        AccountStatus status,
        LocalDate openedOn) {

    public enum AccountType { CURRENT, SAVINGS, LOAN, CARD }

    public enum AccountStatus { ACTIVE, DORMANT, BLOCKED, CLOSED }

    public Account {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
    }

    /** Số tài khoản che bớt, chỉ giữ 4 số cuối. */
    public String maskedNumber() {
        int keep = Math.min(4, accountNumber.length());
        return "*".repeat(accountNumber.length() - keep) + accountNumber.substring(accountNumber.length() - keep);
    }
}
