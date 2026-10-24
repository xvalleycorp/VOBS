package io.vobs.consent.http;

import io.vobs.consent.AuditLog;
import io.vobs.consent.ConsentRepository;
import io.vobs.consent.ConsentService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;

/**
 * Chạy service consent với lưu trữ trong bộ nhớ, cho sandbox và thử nghiệm. Cấu hình qua biến môi trường:
 *
 * <ul>
 *   <li>{@code VOBS_CONSENT_THIRD_PARTIES} (bắt buộc): danh sách đối tác dạng {@code clientId:LOẠI}, cách nhau bởi dấu
 *       phẩy; LOẠI là {@code BANK}, {@code PAYMENT_INTERMEDIARY} hoặc {@code OTHER}.</li>
 *   <li>{@code VOBS_CONSENT_BIND} (mặc định {@code 127.0.0.1}): địa chỉ nghe.</li>
 *   <li>{@code VOBS_CONSENT_PARTNER_PORT} (mặc định 8080) và {@code VOBS_CONSENT_INTERNAL_PORT} (mặc định 8081).</li>
 * </ul>
 *
 * <p>Dữ liệu mất khi dừng service. Bản production cần lưu trữ PostgreSQL và log kiểm toán trên Kafka.
 */
public final class ConsentServerMain {

    private ConsentServerMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String thirdParties = System.getenv("VOBS_CONSENT_THIRD_PARTIES");
        if (thirdParties == null || thirdParties.isBlank()) {
            System.err.println("Thiếu VOBS_CONSENT_THIRD_PARTIES, ví dụ: fintech-a:OTHER,wallet-b:PAYMENT_INTERMEDIARY");
            System.exit(2);
        }
        String bind = env("VOBS_CONSENT_BIND", "127.0.0.1");
        int partnerPort = Integer.parseInt(env("VOBS_CONSENT_PARTNER_PORT", "8080"));
        int internalPort = Integer.parseInt(env("VOBS_CONSENT_INTERNAL_PORT", "8081"));

        ConsentService service = new ConsentService(ConsentRepository.inMemory(), new AuditLog(), Clock.systemUTC(),
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
