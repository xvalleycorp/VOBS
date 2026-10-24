package io.vobs.hub;

import io.vobs.model.Account;
import io.vobs.model.BankId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * Mapper tài khoản cấu hình được: tên cột và bảng mã loại, trạng thái tài khoản của từng core được khai báo trong
 * {@link Columns} thay vì viết code riêng.
 */
public final class AccountRowMapper implements RowMapper<Account> {

    /**
     * Tên cột của bảng tài khoản trong core.
     *
     * @param accountId cột khóa chính
     * @param accountNumber cột số tài khoản
     * @param customerId cột mã khách hàng
     * @param productId cột mã sản phẩm, {@code null} nếu không có
     * @param type cột loại tài khoản
     * @param currency cột loại tiền (mã ISO 4217)
     * @param status cột trạng thái
     * @param openedOn cột ngày mở, {@code null} nếu không có
     * @param dateFormat định dạng ngày nếu core lưu ngày dạng chuỗi (ví dụ {@code yyyyMMdd})
     */
    public record Columns(String accountId, String accountNumber, String customerId, String productId, String type,
                          String currency, String status, String openedOn, DateTimeFormatter dateFormat) {
    }

    private final String sourceTable;
    private final BankId bank;
    private final Columns columns;
    private final Map<String, Account.AccountType> typeCodes;
    private final Map<String, Account.AccountStatus> statusCodes;

    public AccountRowMapper(String sourceTable, BankId bank, Columns columns,
                            Map<String, Account.AccountType> typeCodes,
                            Map<String, Account.AccountStatus> statusCodes) {
        this.sourceTable = sourceTable;
        this.bank = bank;
        this.columns = columns;
        this.typeCodes = Map.copyOf(typeCodes);
        this.statusCodes = Map.copyOf(statusCodes);
    }

    @Override
    public String sourceTable() {
        return sourceTable;
    }

    @Override
    public String key(Map<String, Object> row) {
        return required(row, columns.accountId());
    }

    @Override
    public Account map(Map<String, Object> row) {
        return new Account(
                key(row),
                required(row, columns.accountNumber()),
                bank,
                required(row, columns.customerId()),
                optional(row, columns.productId()),
                lookup(typeCodes, required(row, columns.type()), "loại tài khoản"),
                Currency.getInstance(required(row, columns.currency())),
                lookup(statusCodes, required(row, columns.status()), "trạng thái tài khoản"),
                date(optional(row, columns.openedOn())));
    }

    private LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        return columns.dateFormat() == null ? LocalDate.parse(value) : LocalDate.parse(value, columns.dateFormat());
    }

    private static <E> E lookup(Map<String, E> codes, String code, String label) {
        E value = codes.get(code);
        if (value == null) {
            throw new IllegalArgumentException("Mã %s chưa được ánh xạ: %s".formatted(label, code));
        }
        return value;
    }

    private static String required(Map<String, Object> row, String column) {
        return Objects.requireNonNull(optional(row, column), () -> "Thiếu giá trị cột " + column);
    }

    private static String optional(Map<String, Object> row, String column) {
        if (column == null) {
            return null;
        }
        Object value = row.get(column);
        return value == null ? null : value.toString().strip();
    }
}
