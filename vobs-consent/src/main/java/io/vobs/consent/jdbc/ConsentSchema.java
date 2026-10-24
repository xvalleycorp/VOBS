package io.vobs.consent.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/** Tạo bảng của service consent nếu chưa có ({@code schema.sql}); chạy lại nhiều lần không sao. */
public final class ConsentSchema {

    private ConsentSchema() {
    }

    public static void migrate(DataSource dataSource) throws SQLException {
        String sql;
        try (InputStream in = ConsentSchema.class.getResourceAsStream("schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("Thiếu schema.sql trong classpath");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // Driver PostgreSQL chạy được nhiều câu lệnh trong một lần gọi, kể cả thân hàm $$...$$
            statement.execute(sql);
        }
    }
}
