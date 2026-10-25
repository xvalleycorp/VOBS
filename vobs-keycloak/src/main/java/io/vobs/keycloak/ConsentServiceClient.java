package io.vobs.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gọi API nội bộ của service Quản lý sự đồng ý: xem consent, khách hàng duyệt, khách hàng từ chối
 * ({@code vobs-spec/openapi/internal-consent-check.yaml}). API này chỉ mở trong mạng nội bộ.
 */
final class ConsentServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    ConsentServiceClient(URI baseUri, HttpClient http) {
        String base = baseUri.toString();
        this.baseUri = URI.create(base.endsWith("/") ? base : base + "/");
        this.http = http;
    }

    /** Consent theo mã; rỗng nếu không tồn tại. */
    Optional<ConsentView> find(String consentId) {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(consentUri(consentId, "")).GET());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        return Optional.of(ConsentView.from(read(response, 200)));
    }

    /** Khách hàng duyệt consent với các tài khoản đã chọn. */
    ConsentView authorise(String consentId, String customerId, List<String> accountIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("accountIds", accountIds);
        return ConsentView.from(read(post(consentUri(consentId, "/authorisation"), body), 200));
    }

    /** Khách hàng từ chối consent. */
    ConsentView reject(String consentId, String customerId) {
        return ConsentView.from(read(post(consentUri(consentId, "/rejection"), Map.of("customerId", customerId)),
                200));
    }

    private URI consentUri(String consentId, String suffix) {
        return baseUri.resolve("internal/consents/" + URLEncoder.encode(consentId, StandardCharsets.UTF_8) + suffix);
    }

    private HttpResponse<byte[]> post(URI uri, Object body) {
        try {
            return send(HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))));
        } catch (IOException e) {
            throw new ConsentServiceException("Không mã hóa được request: " + e.getMessage(), 0, null);
        }
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder request) {
        try {
            return http.send(request.timeout(TIMEOUT).header("Accept", "application/json").build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ConsentServiceException("Không gọi được service consent: " + e.getMessage(), 0, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConsentServiceException("Bị ngắt khi gọi service consent", 0, null);
        }
    }

    private JsonNode read(HttpResponse<byte[]> response, int expected) {
        JsonNode body;
        try {
            body = json.readTree(response.body());
        } catch (IOException e) {
            body = null;
        }
        if (response.statusCode() != expected) {
            String reason = body != null && body.hasNonNull("reason") ? body.get("reason").asText() : null;
            throw new ConsentServiceException("Service consent trả về HTTP " + response.statusCode(),
                    response.statusCode(), reason);
        }
        if (body == null || !body.isObject()) {
            throw new ConsentServiceException("Service consent trả về nội dung không phải JSON", expected, null);
        }
        return body;
    }

    /** Các trường của consent mà trang duyệt cần. */
    record ConsentView(String consentId, String clientId, String customerId, String status, List<String> scopes,
                       String expiresAt) {

        static final String AWAITING_AUTHORISATION = "AWAITING_AUTHORISATION";

        static ConsentView from(JsonNode node) {
            List<String> scopes = new ArrayList<>();
            node.path("scopes").forEach(scope -> scopes.add(scope.asText()));
            return new ConsentView(text(node, "consentId"), text(node, "clientId"), text(node, "customerId"),
                    text(node, "status"), List.copyOf(scopes), text(node, "expiresAt"));
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }
    }

    /** Service consent không trả lời được, hoặc từ chối thao tác. */
    static final class ConsentServiceException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int status;
        private final String reason;

        ConsentServiceException(String message, int status, String reason) {
            super(message);
            this.status = status;
            this.reason = reason;
        }

        /** Mã HTTP, hoặc 0 nếu không gọi được service. */
        int status() {
            return status;
        }

        /** Mã lý do trong Problem Details của service consent, nếu có. */
        String reason() {
            return reason;
        }
    }
}
