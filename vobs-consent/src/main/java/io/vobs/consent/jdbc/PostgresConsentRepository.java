package io.vobs.consent.jdbc;

import io.vobs.consent.Consent;
import io.vobs.consent.ConsentRepository;
import io.vobs.consent.ConsentScope;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/** Lưu consent vào bảng {@code consent} của PostgreSQL (xem {@code schema.sql}). */
public final class PostgresConsentRepository implements ConsentRepository {

    private static final String COLUMNS = "consent_id, client_id, customer_id, scopes, account_ids, created_at, "
            + "expires_at, status, status_changed_at";

    private final DataSource dataSource;

    public PostgresConsentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Consent consent) {
        String sql = "INSERT INTO consent (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (consent_id) DO UPDATE SET customer_id = EXCLUDED.customer_id, "
                + "account_ids = EXCLUDED.account_ids, status = EXCLUDED.status, "
                + "status_changed_at = EXCLUDED.status_changed_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, consent.consentId());
            statement.setString(2, consent.clientId());
            statement.setString(3, consent.customerId());
            statement.setArray(4, textArray(connection, consent.scopes().stream().map(Enum::name).sorted().toList()));
            statement.setArray(5, textArray(connection, consent.accountIds().stream().sorted().toList()));
            statement.setObject(6, timestamp(consent.createdAt()));
            statement.setObject(7, timestamp(consent.expiresAt()));
            statement.setString(8, consent.status().name());
            statement.setObject(9, timestamp(consent.statusChangedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Không lưu được consent " + consent.consentId(), e);
        }
    }

    @Override
    public Optional<Consent> find(String consentId) {
        List<Consent> found = query("SELECT " + COLUMNS + " FROM consent WHERE consent_id = ?", consentId);
        return found.stream().findFirst();
    }

    @Override
    public List<Consent> findByCustomer(String customerId) {
        return query("SELECT " + COLUMNS + " FROM consent WHERE customer_id = ? ORDER BY created_at DESC",
                customerId);
    }

    private List<Consent> query(String sql, String parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<Consent> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(consent(rows));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được consent", e);
        }
    }

    private static Consent consent(ResultSet row) throws SQLException {
        Set<ConsentScope> scopes = texts(row.getArray("scopes")).stream().map(ConsentScope::valueOf)
                .collect(Collectors.toSet());
        return new Consent(
                row.getString("consent_id"),
                row.getString("client_id"),
                row.getString("customer_id"),
                scopes,
                Set.copyOf(texts(row.getArray("account_ids"))),
                instant(row, "created_at"),
                instant(row, "expires_at"),
                Consent.Status.valueOf(row.getString("status")),
                instant(row, "status_changed_at"));
    }

    private static Array textArray(Connection connection, List<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray());
    }

    private static List<String> texts(Array array) throws SQLException {
        return Arrays.stream((Object[]) array.getArray()).map(String.class::cast).toList();
    }

    static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }
}
