package io.vobs.consent.jdbc;

import io.vobs.consent.AuditLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Lưu log kiểm toán vào bảng {@code consent_audit_log}. Trigger trong {@code schema.sql} chặn sửa và xóa bản ghi;
 * khóa chính theo số thứ tự làm lần ghi thứ hai cùng số thứ tự thất bại thay vì rẽ nhánh chuỗi.
 */
public final class PostgresAuditStore implements AuditLog.Store {

    private static final String COLUMNS = "sequence, at, actor, action, subject, previous_hash, hash";

    private final DataSource dataSource;

    public PostgresAuditStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<AuditLog.Entry> last() {
        return query("SELECT " + COLUMNS + " FROM consent_audit_log ORDER BY sequence DESC LIMIT 1").stream()
                .findFirst();
    }

    @Override
    public void append(AuditLog.Entry entry) {
        String sql = "INSERT INTO consent_audit_log (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, entry.sequence());
            statement.setObject(2, PostgresConsentRepository.timestamp(entry.at()));
            statement.setString(3, entry.actor());
            statement.setString(4, entry.action());
            statement.setString(5, entry.subject());
            statement.setString(6, entry.previousHash());
            statement.setString(7, entry.hash());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Không ghi được log kiểm toán #" + entry.sequence(), e);
        }
    }

    @Override
    public List<AuditLog.Entry> all() {
        return query("SELECT " + COLUMNS + " FROM consent_audit_log ORDER BY sequence");
    }

    private List<AuditLog.Entry> query(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<AuditLog.Entry> result = new ArrayList<>();
            while (rows.next()) {
                result.add(new AuditLog.Entry(
                        rows.getLong("sequence"),
                        PostgresConsentRepository.instant(rows, "at"),
                        rows.getString("actor"),
                        rows.getString("action"),
                        rows.getString("subject"),
                        rows.getString("previous_hash"),
                        rows.getString("hash")));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được log kiểm toán", e);
        }
    }
}
