package io.vobs.consent;

/**
 * Phạm vi dữ liệu mà một consent cho phép.
 *
 * <p>Danh mục tạm thời; sẽ chỉnh theo danh mục nhóm API của Thông tư 64/2024/TT-NHNN khi bộ đặc tả trong
 * {@code vobs-spec} được chốt.
 */
public enum ConsentScope {
    ACCOUNTS_READ,
    BALANCES_READ,
    TRANSACTIONS_READ,
    PAYMENTS_INITIATE
}
