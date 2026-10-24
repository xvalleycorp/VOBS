package io.vobs.hub;

import java.util.Optional;

/**
 * Áp một sự kiện CDC lên kho đọc qua mapper tương ứng.
 *
 * <p>Sự kiện cũ hơn bản đang có trong kho (theo thời điểm commit) bị bỏ qua, để việc phát lại từ đầu topic hoặc
 * chạy lại snapshot không ghi đè dữ liệu mới hơn.
 *
 * @param <T> kiểu thực thể
 */
public final class Projector<T> {

    private final RowMapper<T> mapper;
    private final ReadStore<T> store;

    public Projector(RowMapper<T> mapper, ReadStore<T> store) {
        this.mapper = mapper;
        this.store = store;
    }

    /** Trả về {@code true} nếu sự kiện thuộc bảng của mapper và đã được áp lên kho. */
    public boolean apply(ChangeEvent event) {
        if (!event.table().equals(mapper.sourceTable())) {
            return false;
        }
        String key = mapper.key(event.latestRow());
        Optional<ReadStore.Versioned<T>> current = store.get(key);
        if (current.isPresent() && current.get().committedAt().isAfter(event.committedAt())) {
            return false;
        }
        if (event.operation() == ChangeEvent.Operation.DELETE) {
            store.delete(key, event.committedAt());
        } else {
            store.upsert(key, mapper.map(event.after()), event.committedAt());
        }
        return true;
    }
}
