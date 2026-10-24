package io.vobs.hub;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Một thay đổi trên một dòng dữ liệu của core, lấy từ envelope của Debezium.
 *
 * @param operation loại thay đổi
 * @param table bảng nguồn, dạng {@code schema.table} nếu có schema
 * @param before dòng trước khi đổi, rỗng với {@code CREATE} và {@code READ}
 * @param after dòng sau khi đổi, rỗng với {@code DELETE}
 * @param committedAt thời điểm giao dịch được commit ở database của core
 */
public record ChangeEvent(
        Operation operation,
        String table,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant committedAt) {

    public enum Operation {
        CREATE("c"),
        UPDATE("u"),
        DELETE("d"),
        /** Dòng đọc trong lúc chụp snapshot ban đầu. */
        READ("r");

        private final String code;

        Operation(String code) {
            this.code = code;
        }

        static Operation fromCode(String code) {
            for (Operation operation : values()) {
                if (operation.code.equals(code)) {
                    return operation;
                }
            }
            throw new IllegalArgumentException("Loại thay đổi Debezium không hỗ trợ: " + code);
        }
    }

    public ChangeEvent {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(committedAt, "committedAt");
        before = before == null ? Map.of() : before;
        after = after == null ? Map.of() : after;
    }

    /** Dòng mang trạng thái mới nhất: {@code after}, hoặc {@code before} nếu dòng bị xóa. */
    public Map<String, Object> latestRow() {
        return operation == Operation.DELETE ? before : after;
    }
}
