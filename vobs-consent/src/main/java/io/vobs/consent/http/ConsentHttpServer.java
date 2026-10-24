package io.vobs.consent.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.vobs.consent.AccessDecision;
import io.vobs.consent.Consent;
import io.vobs.consent.ConsentScope;
import io.vobs.consent.ConsentService;
import io.vobs.consent.ScopeNotPermittedException;
import io.vobs.consent.ThirdParty;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp HTTP của service consent, chạy trên máy chủ HTTP có sẵn trong JDK. Mở hai cổng tách biệt để API nội bộ không bị
 * lộ ra ngoài dù route ở gateway cấu hình nhầm:
 *
 * <ul>
 *   <li>Cổng đối tác, chỉ nhận request đã qua API Gateway: {@code POST /consents}, {@code GET /consents/{id}},
 *       {@code DELETE /consents/{id}} (đặc tả {@code vobs-spec/openapi/vobs-open-api.yaml}). Gateway xác thực mã truy
 *       cập rồi đặt mã client vào header {@value #CLIENT_ID_HEADER}, ghi đè giá trị đối tác tự gửi.</li>
 *   <li>Cổng nội bộ: {@code POST /internal/consent-checks} cho gateway; xem danh sách, duyệt, từ chối, thu hồi
 *       consent cho kênh của ngân hàng (đặc tả {@code vobs-spec/openapi/internal-consent-check.yaml}).</li>
 * </ul>
 *
 * <p>Lỗi trả theo RFC 9457 ({@code application/problem+json}) kèm mã lý do máy đọc được. TLS và mTLS do sidecar
 * hoặc service mesh đảm nhận; máy chủ này nghe HTTP thường, nên chỉ được mở trong mạng nội bộ của cụm.
 */
public final class ConsentHttpServer implements AutoCloseable {

    /** Header chứa mã client đã được gateway xác thực. */
    public static final String CLIENT_ID_HEADER = "x-vobs-client-id";

    static final String INTERACTION_ID_HEADER = "x-fapi-interaction-id";
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final String PROBLEM_TYPE_BASE = "https://vobs.io/problems/";
    /** Thời điểm trả về theo giờ Việt Nam (RFC 3339 có múi giờ), như ví dụ trong đặc tả. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.ofHours(7));

    private final ConsentService service;
    private final ThirdPartyDirectory directory;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpServer partner;
    private final HttpServer internal;

    private ConsentHttpServer(ConsentService service, ThirdPartyDirectory directory,
                              InetSocketAddress partnerAddress, InetSocketAddress internalAddress) throws IOException {
        this.service = service;
        this.directory = directory;
        this.partner = HttpServer.create(partnerAddress, 0);
        this.internal = HttpServer.create(internalAddress, 0);
        partner.createContext("/", exchange -> handle(exchange, this::routePartner));
        internal.createContext("/", exchange -> handle(exchange, this::routeInternal));
        partner.setExecutor(executor);
        internal.setExecutor(executor);
    }

    /** Mở hai cổng và bắt đầu nhận request. Cổng 0 nghĩa là để hệ điều hành chọn cổng trống. */
    public static ConsentHttpServer start(ConsentService service, ThirdPartyDirectory directory,
                                          InetSocketAddress partnerAddress, InetSocketAddress internalAddress)
            throws IOException {
        ConsentHttpServer server = new ConsentHttpServer(service, directory, partnerAddress, internalAddress);
        server.partner.start();
        server.internal.start();
        return server;
    }

    public InetSocketAddress partnerAddress() {
        return partner.getAddress();
    }

    public InetSocketAddress internalAddress() {
        return internal.getAddress();
    }

    @Override
    public void close() {
        partner.stop(0);
        internal.stop(0);
        executor.close();
    }

    // ------------------------------------------------------------------ định tuyến

    private Response routePartner(HttpExchange exchange) throws IOException {
        String[] path = segments(exchange);
        if (path.length == 1 && path[0].equals("consents")) {
            requireMethod(exchange, "POST");
            return createConsent(exchange);
        }
        if (path.length == 2 && path[0].equals("consents")) {
            return switch (exchange.getRequestMethod()) {
                case "GET" -> getConsent(exchange, path[1]);
                case "DELETE" -> revokeByClient(exchange, path[1]);
                default -> throw ApiException.methodNotAllowed("GET, DELETE");
            };
        }
        throw ApiException.notFound();
    }

    private Response routeInternal(HttpExchange exchange) throws IOException {
        String[] path = segments(exchange);
        if (path.length == 2 && path[0].equals("internal") && path[1].equals("consent-checks")) {
            requireMethod(exchange, "POST");
            return checkConsent(exchange);
        }
        if (path.length == 4 && path[0].equals("internal") && path[1].equals("customers")
                && path[3].equals("consents")) {
            requireMethod(exchange, "GET");
            return listForCustomer(path[2]);
        }
        if (path.length == 4 && path[0].equals("internal") && path[1].equals("consents")) {
            requireMethod(exchange, "POST");
            String consentId = path[2];
            return switch (path[3]) {
                case "authorisation" -> authorise(exchange, consentId);
                case "rejection" -> reject(exchange, consentId);
                case "revocation" -> revokeByCustomer(exchange, consentId);
                default -> throw ApiException.notFound();
            };
        }
        throw ApiException.notFound();
    }

    // ------------------------------------------------------------------ API đối tác

    private Response createConsent(HttpExchange exchange) throws IOException {
        ThirdParty client = authenticatedClient(exchange);
        JsonNode body = readObject(exchange);
        Set<ConsentScope> scopes = scopes(body.get("scopes"));
        JsonNode validityDays = body.get("validityDays");
        if (validityDays == null || !validityDays.isIntegralNumber() || !validityDays.canConvertToInt()) {
            throw ApiException.badRequest("validityDays phải là số nguyên");
        }
        Consent consent = service.request(client, scopes, Duration.ofDays(validityDays.intValue()));
        return Response.json(201, partnerView(consent));
    }

    private Response getConsent(HttpExchange exchange, String consentId) {
        ThirdParty client = authenticatedClient(exchange);
        Consent consent = service.find(consentId)
                .filter(found -> found.clientId().equals(client.clientId()))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy consent " + consentId));
        return Response.json(200, partnerView(consent));
    }

    private Response revokeByClient(HttpExchange exchange, String consentId) {
        ThirdParty client = authenticatedClient(exchange);
        service.revokeByClient(consentId, client.clientId());
        return Response.noContent();
    }

    // ------------------------------------------------------------------ API nội bộ

    private Response checkConsent(HttpExchange exchange) throws IOException {
        JsonNode body = readObject(exchange);
        String consentId = requiredText(body, "consentId");
        String clientId = requiredText(body, "clientId");
        ConsentScope scope = scope(body.get("scope"));
        String accountId = optionalText(body, "accountId");
        AccessDecision decision = service.check(consentId, clientId, scope, accountId);
        ObjectNode result = json.createObjectNode();
        result.put("allowed", decision.allowed());
        result.put("reason", decision.reason());
        return Response.json(200, result);
    }

    private Response listForCustomer(String customerId) {
        ObjectNode result = json.createObjectNode();
        ArrayNode consents = result.putArray("consents");
        service.listForCustomer(customerId).forEach(consent -> consents.add(internalView(consent)));
        return Response.json(200, result);
    }

    private Response authorise(HttpExchange exchange, String consentId) throws IOException {
        JsonNode body = readObject(exchange);
        String customerId = requiredText(body, "customerId");
        Set<String> accountIds = new LinkedHashSet<>();
        JsonNode accounts = body.get("accountIds");
        if (accounts != null && !accounts.isNull()) {
            if (!accounts.isArray()) {
                throw ApiException.badRequest("accountIds phải là mảng");
            }
            for (JsonNode account : accounts) {
                if (!account.isTextual() || account.asText().isBlank()) {
                    throw ApiException.badRequest("Mỗi phần tử của accountIds phải là chuỗi khác rỗng");
                }
                accountIds.add(account.asText());
            }
        }
        return Response.json(200, internalView(service.authorise(consentId, customerId, accountIds)));
    }

    private Response reject(HttpExchange exchange, String consentId) throws IOException {
        String customerId = requiredText(readObject(exchange), "customerId");
        return Response.json(200, internalView(service.reject(consentId, customerId)));
    }

    private Response revokeByCustomer(HttpExchange exchange, String consentId) throws IOException {
        String customerId = requiredText(readObject(exchange), "customerId");
        return Response.json(200, internalView(service.revokeByCustomer(consentId, customerId)));
    }

    // ------------------------------------------------------------------ chuyển đổi JSON

    /** Consent theo đặc tả cho đối tác: không có mã khách hàng nội bộ của ngân hàng. */
    private ObjectNode partnerView(Consent consent) {
        ObjectNode node = json.createObjectNode();
        node.put("consentId", consent.consentId());
        node.put("status", consent.status().name());
        ArrayNode scopes = node.putArray("scopes");
        consent.scopes().stream().sorted().forEach(scope -> scopes.add(scope.name()));
        ArrayNode accounts = node.putArray("accountIds");
        consent.accountIds().stream().sorted().forEach(accounts::add);
        node.put("createdAt", timestamp(consent.createdAt()));
        node.put("expiresAt", timestamp(consent.expiresAt()));
        return node;
    }

    /** Consent cho kênh của ngân hàng: thêm đối tác, khách hàng và thời điểm đổi trạng thái. */
    private ObjectNode internalView(Consent consent) {
        ObjectNode node = partnerView(consent);
        node.put("clientId", consent.clientId());
        node.put("customerId", consent.customerId());
        node.put("statusChangedAt", timestamp(consent.statusChangedAt()));
        return node;
    }

    private static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    private Set<ConsentScope> scopes(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw ApiException.badRequest("scopes phải là mảng có ít nhất một phạm vi");
        }
        Set<ConsentScope> scopes = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!scopes.add(scope(item))) {
                throw ApiException.badRequest("scopes có phạm vi lặp lại: " + item.asText());
            }
        }
        return scopes;
    }

    private static ConsentScope scope(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw ApiException.badRequest("scope phải là chuỗi");
        }
        try {
            return ConsentScope.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Phạm vi không hợp lệ: " + node.asText());
        }
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw ApiException.badRequest(field + " là bắt buộc và phải là chuỗi khác rỗng");
        }
        return node.asText();
    }

    private static String optionalText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.badRequest(field + " phải là chuỗi");
        }
        return node.asText();
    }

    // ------------------------------------------------------------------ hạ tầng request

    private ThirdParty authenticatedClient(HttpExchange exchange) {
        String clientId = exchange.getRequestHeaders().getFirst(CLIENT_ID_HEADER);
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(401, "client_unauthenticated", "Thiếu danh tính đối tác đã xác thực");
        }
        return directory.find(clientId)
                .orElseThrow(() -> new ApiException(403, "client_not_registered",
                        "Đối tác chưa đăng ký kết nối Open API với ngân hàng"));
    }

    private JsonNode readObject(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream in = exchange.getRequestBody()) {
            bytes = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "payload_too_large", "Nội dung request quá lớn");
        }
        JsonNode node;
        try {
            node = json.readTree(bytes);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Nội dung không phải JSON hợp lệ");
        }
        if (node == null || !node.isObject()) {
            throw ApiException.badRequest("Nội dung phải là một đối tượng JSON");
        }
        return node;
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!exchange.getRequestMethod().equals(method)) {
            throw ApiException.methodNotAllowed(method);
        }
    }

    private static String[] segments(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    @FunctionalInterface
    private interface Route {
        Response handle(HttpExchange exchange) throws IOException;
    }

    private void handle(HttpExchange exchange, Route route) throws IOException {
        try (exchange) {
            // FAPI: trả lại mã tương quan của bên thứ ba, hoặc tạo mới nếu không có
            String interactionId = exchange.getRequestHeaders().getFirst(INTERACTION_ID_HEADER);
            exchange.getResponseHeaders().set(INTERACTION_ID_HEADER,
                    interactionId == null || interactionId.isBlank() ? UUID.randomUUID().toString() : interactionId);
            Response response;
            try {
                response = route.handle(exchange);
            } catch (ApiException e) {
                response = problem(e.status(), e.reason(), e.title(), e.detail());
                if (e.allow() != null) {
                    exchange.getResponseHeaders().set("Allow", e.allow());
                }
            } catch (ScopeNotPermittedException e) {
                response = problem(403, "scope_not_permitted",
                        "Phạm vi thuộc nhóm thanh toán chỉ dành cho ngân hàng và trung gian thanh toán", null);
            } catch (NoSuchElementException e) {
                response = problem(404, "not_found", "Không tìm thấy", null);
            } catch (IllegalStateException e) {
                response = problem(409, "consent_state_conflict", "Consent không ở trạng thái cho phép thao tác này",
                        e.getMessage());
            } catch (IllegalArgumentException e) {
                response = problem(400, "invalid_request", "Request không hợp lệ", e.getMessage());
            } catch (RuntimeException e) {
                response = problem(500, "internal_error", "Lỗi nội bộ", null);
            }
            send(exchange, response);
        }
    }

    private Response problem(int status, String reason, String title, String detail) {
        ObjectNode node = json.createObjectNode();
        node.put("type", PROBLEM_TYPE_BASE + reason.replace('_', '-'));
        node.put("title", title);
        node.put("status", status);
        if (detail != null) {
            node.put("detail", detail);
        }
        node.put("reason", reason);
        return new Response(status, "application/problem+json", node);
    }

    private void send(HttpExchange exchange, Response response) throws IOException {
        if (response.body() == null) {
            exchange.sendResponseHeaders(response.status(), -1);
            return;
        }
        byte[] bytes = json.writeValueAsBytes(response.body());
        exchange.getResponseHeaders().set("Content-Type", response.contentType() + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private record Response(int status, String contentType, JsonNode body) {

        static Response json(int status, JsonNode body) {
            return new Response(status, "application/json", body);
        }

        static Response noContent() {
            return new Response(204, null, null);
        }
    }

    /** Lỗi ở tầng HTTP, ánh xạ thẳng sang Problem Details. */
    private static final class ApiException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int status;
        private final String reason;
        private final String title;
        private final String detail;
        private final String allow;

        ApiException(int status, String reason, String title) {
            this(status, reason, title, null, null);
        }

        private ApiException(int status, String reason, String title, String detail, String allow) {
            super(reason, null, false, false);
            this.status = status;
            this.reason = reason;
            this.title = title;
            this.detail = detail;
            this.allow = allow;
        }

        static ApiException badRequest(String detail) {
            return new ApiException(400, "invalid_request", "Request không hợp lệ", detail, null);
        }

        static ApiException notFound() {
            return new ApiException(404, "not_found", "Không tìm thấy");
        }

        static ApiException methodNotAllowed(String allow) {
            return new ApiException(405, "method_not_allowed", "Phương thức không được hỗ trợ", null, allow);
        }

        int status() {
            return status;
        }

        String reason() {
            return reason;
        }

        String title() {
            return title;
        }

        String detail() {
            return detail;
        }

        String allow() {
            return allow;
        }
    }
}
