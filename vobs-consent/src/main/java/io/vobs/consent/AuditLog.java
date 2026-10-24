package io.vobs.consent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Log kiểm toán chỉ ghi nối tiếp, mỗi bản ghi chứa hash SHA-256 của bản ghi trước. Sửa hay xóa một bản ghi bất kỳ
 * làm gãy chuỗi và bị {@link #verify()} phát hiện.
 *
 * <p>Bản ghi nằm trong một {@link Store}: trong bộ nhớ ({@link #AuditLog()}) cho kiểm thử, hoặc PostgreSQL
 * ({@code jdbc.PostgresAuditStore}). Khi mở lại trên một store đã có dữ liệu, chuỗi được nối tiếp từ bản ghi cuối.
 * Mỗi lúc chỉ một tiến trình được ghi vào cùng một store.
 */
public final class AuditLog {

    /** Nơi lưu bản ghi kiểm toán; chỉ ghi thêm, không sửa, không xóa. */
    public interface Store {

        /** Bản ghi cuối cùng, nếu có. */
        Optional<Entry> last();

        void append(Entry entry);

        /** Toàn bộ bản ghi theo thứ tự. */
        List<Entry> all();

        static Store inMemory() {
            List<Entry> entries = new ArrayList<>();
            return new Store() {
                @Override
                public synchronized Optional<Entry> last() {
                    return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getLast());
                }

                @Override
                public synchronized void append(Entry entry) {
                    entries.add(entry);
                }

                @Override
                public synchronized List<Entry> all() {
                    return List.copyOf(entries);
                }
            };
        }
    }

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

    private final Store store;
    private Entry last;

    /** Log trong bộ nhớ, cho kiểm thử và sandbox. */
    public AuditLog() {
        this(Store.inMemory());
    }

    /** Log trên một store; nối tiếp từ bản ghi cuối nếu store đã có dữ liệu. */
    public AuditLog(Store store) {
        this.store = Objects.requireNonNull(store, "store");
        this.last = store.last().orElse(null);
    }

    public synchronized Entry append(Instant at, String actor, String action, String subject) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subject, "subject");
        // PostgreSQL lưu tới micro giây; làm tròn trước khi tính hash để hash khớp khi đọc lại
        Instant stamped = at.truncatedTo(ChronoUnit.MICROS);
        String previousHash = last == null ? GENESIS : last.hash();
        long sequence = last == null ? 1L : last.sequence() + 1L;
        Entry unsigned = new Entry(sequence, stamped, actor, action, subject, previousHash, null);
        Entry entry = new Entry(sequence, stamped, actor, action, subject, previousHash,
                sha256(unsigned.canonical()));
        store.append(entry);
        last = entry;
        return entry;
    }

    public synchronized List<Entry> entries() {
        return store.all();
    }

    /** Kiểm tra toàn bộ chuỗi; trả về {@code true} nếu không bản ghi nào bị sửa, xóa hay chèn. */
    public synchronized boolean verify() {
        return verify(store.all());
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
