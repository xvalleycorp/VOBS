package io.vobs.consent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Log kiểm toán chỉ ghi nối tiếp, mỗi bản ghi chứa hash SHA-256 của bản ghi trước. Sửa hay xóa một bản ghi bất kỳ
 * làm gãy chuỗi và bị {@link #verify()} phát hiện.
 *
 * <p>Bản trong bộ nhớ này dùng cho kiểm thử; bản production ghi các {@link Entry} vào Kafka rồi lưu kiểu WORM.
 */
public final class AuditLog {

    static final String GENESIS = "0".repeat(64);

    /**
     * Một bản ghi kiểm toán.
     *
     * @param sequence số thứ tự, bắt đầu từ 1
     * @param at thời điểm
     * @param actor ai thực hiện: {@code customer:<id>}, {@code client:<id>} hoặc {@code system}
     * @param action hành động, ví dụ {@code consent.authorised}
     * @param subject đối tượng bị tác động, ví dụ mã consent
     * @param previousHash hash của bản ghi trước
     * @param hash hash của bản ghi này
     */
    public record Entry(long sequence, Instant at, String actor, String action, String subject,
                        String previousHash, String hash) {

        String canonical() {
            return String.join("|", Long.toString(sequence), at.toString(), actor, action, subject, previousHash);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public synchronized Entry append(Instant at, String actor, String action, String subject) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subject, "subject");
        String previousHash = entries.isEmpty() ? GENESIS : entries.getLast().hash();
        Entry unsigned = new Entry(entries.size() + 1L, at, actor, action, subject, previousHash, null);
        Entry entry = new Entry(unsigned.sequence(), at, actor, action, subject, previousHash,
                sha256(unsigned.canonical()));
        entries.add(entry);
        return entry;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Kiểm tra toàn bộ chuỗi; trả về {@code true} nếu không bản ghi nào bị sửa, xóa hay chèn. */
    public synchronized boolean verify() {
        return verify(entries);
    }

    static boolean verify(List<Entry> chain) {
        String previousHash = GENESIS;
        for (int i = 0; i < chain.size(); i++) {
            Entry entry = chain.get(i);
            if (entry.sequence() != i + 1L
                    || !entry.previousHash().equals(previousHash)
                    || !entry.hash().equals(sha256(entry.canonical()))) {
                return false;
            }
            previousHash = entry.hash();
        }
        return true;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
    }
}
