package io.vobs.hub;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kho đọc mà API đọc truy vấn, thay vì gọi core. Bản production dùng PostgreSQL và Valkey; {@link #inMemory()} dùng
 * cho kiểm thử.
 *
 * @param <T> kiểu thực thể
 */
public interface ReadStore<T> {

    /** Một bản ghi kèm thời điểm commit ở core của thay đổi đã tạo ra nó. */
    record Versioned<T>(T value, Instant committedAt) {
    }

    void upsert(String key, T value, Instant committedAt);

    void delete(String key, Instant committedAt);

    Optional<Versioned<T>> get(String key);

    static <T> ReadStore<T> inMemory() {
        Map<String, Versioned<T>> data = new ConcurrentHashMap<>();
        return new ReadStore<>() {
            @Override
            public void upsert(String key, T value, Instant committedAt) {
                data.put(key, new Versioned<>(value, committedAt));
            }

            @Override
            public void delete(String key, Instant committedAt) {
                data.remove(key);
            }

            @Override
            public Optional<Versioned<T>> get(String key) {
                return Optional.ofNullable(data.get(key));
            }
        };
    }
}
