package io.vobs.hub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Đọc giá trị (value) của bản ghi Kafka do Debezium sinh ra, ở cả hai dạng: có schema
 * ({@code {"schema":..., "payload":{...}}}) và chỉ payload.
 */
public final class DebeziumEventParser {

    private static final TypeReference<Map<String, Object>> ROW = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public DebeziumEventParser() {
        this(new ObjectMapper());
    }

    public DebeziumEventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Trả về rỗng với tombstone (giá trị {@code null}, Debezium gửi sau mỗi lần xóa để Kafka compact) và với sự kiện
     * truncate, vốn không mang dữ liệu dòng.
     */
    public Optional<ChangeEvent> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = mapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Giá trị không phải JSON hợp lệ", e);
        }
        JsonNode payload = root.has("payload") && root.has("schema") ? root.get("payload") : root;
        if (payload == null || payload.isNull()) {
            return Optional.empty();
        }
        String op = text(payload, "op");
        if ("t".equals(op)) {
            return Optional.empty();
        }

        JsonNode source = payload.path("source");
        String table = source.hasNonNull("schema")
                ? text(source, "schema") + "." + text(source, "table")
                : text(source, "table");
        long committedAtMs = source.hasNonNull("ts_ms") ? source.get("ts_ms").asLong() : payload.path("ts_ms").asLong();

        return Optional.of(new ChangeEvent(
                ChangeEvent.Operation.fromCode(op),
                table,
                row(payload.get("before")),
                row(payload.get("after")),
                Instant.ofEpochMilli(committedAtMs)));
    }

    private Map<String, Object> row(JsonNode node) {
        return node == null || node.isNull() ? Map.of() : mapper.convertValue(node, ROW);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Envelope Debezium thiếu trường " + field);
        }
        return value.asText();
    }
}
