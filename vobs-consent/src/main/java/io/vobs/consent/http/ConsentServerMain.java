package io.vobs.consent.http;

import io.vobs.consent.AuditLog;
import io.vobs.consent.ConsentRepository;
import io.vobs.consent.ConsentService;
import io.vobs.consent.jdbc.ConsentSchema;
import io.vobs.consent.jdbc.PostgresAuditStore;
import io.vobs.consent.jdbc.PostgresConsentRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Chạy service consent. Cấu hình qua biến môi trường:
 *
 * <ul>
 *   <li>{@code VOBS_CONSENT_DATABASE_URL}: URL JDBC của PostgreSQL, ví dụ
 *       {@code jdbc:postgresql://db:5432/vobs}; kèm {@code VOBS_CONSENT_DATABASE_USER} và
 *       {@code VOBS_CONSENT_DATABASE_PASSWORD}. Bảng được tạo khi khởi động nếu chưa có. Không đặt thì consent và
 *       log kiểm toán chỉ nằm trong bộ nhớ và mất khi dừng service (dùng cho sandbox, thử nghiệm).</li>
 *   <li>{@code VOBS_CONSENT_THIRD_PARTIES} (bắt buộc): danh sách đối tác dạng {@code clientId:LOẠI}, cách nhau bởi dấu
 *       phẩy; LOẠI là {@code BANK}, {@code PAYMENT_INTERMEDIARY} hoặc {@code OTHER}.</li>
 *   <li>{@code VOBS_CONSENT_BIND} (mặc định {@code 127.0.0.1}): địa chỉ nghe.</li>
 *   <li>{@code VOBS_CONSENT_PARTNER_PORT} (mặc định 8080) và {@code VOBS_CONSENT_INTERNAL_PORT} (mặc định 8081).</li>
 * </ul>
 *
 * <p>Mỗi lúc chỉ chạy một bản service trên cùng một cơ sở dữ liệu: chuỗi hash của log kiểm toán được nối tiếp
 * trong tiến trình.
 */
public final class ConsentServerMain {

    private ConsentServerMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {
        String thirdParties = System.getenv("VOBS_CONSENT_THIRD_PARTIES");
        if (thirdParties == null || thirdParties.isBlank()) {
            System.err.println("Thiếu VOBS_CONSENT_THIRD_PARTIES, ví dụ: fintech-a:OTHER,wallet-b:PAYMENT_INTERMEDIARY");
            System.exit(2);
        }
        String bind = env("VOBS_CONSENT_BIND", "127.0.0.1");
        int partnerPort = Integer.parseInt(env("VOBS_CONSENT_PARTNER_PORT", "8080"));
        int internalPort = Integer.parseInt(env("VOBS_CONSENT_INTERNAL_PORT", "8081"));

        ConsentRepository repository;
        AuditLog auditLog;
        String databaseUrl = System.getenv("VOBS_CONSENT_DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            repository = ConsentRepository.inMemory();
            auditLog = new AuditLog();
            System.out.println("vobs-consent: lưu trữ trong bộ nhớ, dữ liệu mất khi dừng service");
        } else {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setUrl(databaseUrl);
            dataSource.setUser(env("VOBS_CONSENT_DATABASE_USER", null));
            dataSource.setPassword(env("VOBS_CONSENT_DATABASE_PASSWORD", null));
            ConsentSchema.migrate(dataSource);
            repository = new PostgresConsentRepository(dataSource);
            auditLog = new AuditLog(new PostgresAuditStore(dataSource));
            if (!auditLog.verify()) {
                System.err.println("vobs-consent: chuỗi hash của log kiểm toán bị gãy, dừng khởi động");
                System.exit(3);
            }
        }
        ConsentService service = new ConsentService(repository, auditLog, Clock.systemUTC(),
                ConsentService.TT64_MAX_VALIDITY);
        ConsentHttpServer server = ConsentHttpServer.start(service, ThirdPartyDirectory.parse(thirdParties),
                new InetSocketAddress(bind, partnerPort), new InetSocketAddress(bind, internalPort));
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            stopped.countDown();
        }));
        System.out.printf("vobs-consent: API đối tác tại %s, API nội bộ tại %s%n", server.partnerAddress(),
                server.internalAddress());
        stopped.await();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
