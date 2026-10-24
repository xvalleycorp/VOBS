package io.vobs.hub;

import java.util.Map;

/**
 * Ánh xạ một dòng của bảng core sang thực thể của {@code io.vobs.model}. Mỗi connector core (T24, Flexcube,
 * Silverlake...) cung cấp một bộ mapper cho các bảng nó đọc.
 *
 * @param <T> kiểu thực thể đích
 */
public interface RowMapper<T> {

    /** Bảng nguồn, khớp với {@link ChangeEvent#table()}. */
    String sourceTable();

    /** Khóa của thực thể trong kho đọc, lấy từ dòng nguồn. */
    String key(Map<String, Object> row);

    T map(Map<String, Object> row);
}
