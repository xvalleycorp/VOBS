package io.vobs.consent.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vobs.consent.AuditLog;
import io.vobs.consent.ConsentPolicy;
import io.vobs.consent.ConsentRepository;
import io.vobs.consent.ConsentService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsentHttpServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditLog audit = new AuditLog();
    private final HttpClient http = HttpClient.newHttpClient();
    private ConsentHttpServer server;

    /** Kết quả gọi API: mã trạng thái, header và body đã đọc thành JSON (hoặc {@code null} khi không có body). */
    private record Reply(int status, HttpResponse<String> raw, JsonNode body) {
        String text(String field) {
            return body.get(field).asText();
        }
    }

    @BeforeEach
    void start() throws IOException {
        ConsentService service = new ConsentService(ConsentRepository.inMemory(), audit, Clock.systemUTC(),
                ConsentPolicy.builtIn());
        InetSocketAddress loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        server = ConsentHttpServer.start(service,
                ThirdPartyDirectory.parse("fintech-a:OTHER, fintech-b:OTHER, wallet-a:PAYMENT_INTERMEDIARY"),
                loopback, loopback);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    // ------------------------------------------------------------------ luồng đầy đủ

    @Test
    void partnerCreatesCustomerAuthorisesGatewayChecks() throws Exception {
        Reply created = partner("POST", "/consents", "fintech-a",
                "{\"scopes\":[\"ACCOUNTS_READ\",\"TRANSACTIONS_READ\"],\"validityDays\":90}");
        assertEquals(201, created.status());
        assertEquals("AWAITING_AUTHORISATION", created.text("status"));
        assertTrue(created.text("createdAt").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+07:00"),
                created.text("createdAt"));
        assertFalse(created.body().has("customerId"), "đối tác không được thấy mã khách hàng nội bộ");
        assertFalse(created.body().has("policyVersion"), "đối tác không cần thấy phiên bản chính sách nội bộ");
        String id = created.text("consentId");

        assertEquals("consent_awaiting_authorisation", check(id, "fintech-a", "ACCOUNTS_READ", "acc-1").text("reason"));

        Reply authorised = internal("POST", "/internal/consents/" + id + "/authorisation",
                "{\"customerId\":\"cif-1\",\"accountIds\":[\"acc-1\"]}");
        assertEquals(200, authorised.status());
        assertEquals("AUTHORISED", authorised.text("status"));
        assertEquals("cif-1", authorised.text("customerId"));
        assertTrue(authorised.text("policyVersion").startsWith("1@sha256:"), authorised.text("policyVersion"));

        Reply allowed = check(id, "fintech-a", "ACCOUNTS_READ", "acc-1");
        assertTrue(allowed.body().get("allowed").asBoolean());
        assertTrue(allowed.body().get("reason").isNull());
        assertEquals("account_not_granted", check(id, "fintech-a", "ACCOUNTS_READ", "acc-2").text("reason"));
        assertEquals("consent_not_found", check(id, "fintech-b", "ACCOUNTS_READ", "acc-1").text("reason"));

        Reply fetched = partner("GET", "/consents/" + id, "fintech-a", null);
        assertEquals(200, fetched.status());
        assertEquals(List.of("acc-1"), JSON.convertValue(fetched.body().get("accountIds"), List.class));

        assertEquals(204, partner("DELETE", "/consents/" + id, "fintech-a", null).status());
        assertEquals("consent_revoked", check(id, "fintech-a", "ACCOUNTS_READ", "acc-1").text("reason"));
        Reply again = partner("DELETE", "/consents/" + id, "fintech-a", null);
        assertEquals(409, again.status());
        assertEquals("consent_state_conflict", again.text("reason"));

        assertEquals(List.of("consent.requested", "consent.authorised", "consent.revoked"),
                audit.entries().stream().map(AuditLog.Entry::action).toList());
        assertTrue(audit.verify());
    }

    @Test
    void bankChannelReadsConsentBeforeCustomerDecides() throws Exception {
        String id = createAccountsConsent("fintech-a");
        Reply pending = internal("GET", "/internal/consents/" + id, null);
        assertEquals(200, pending.status());
        assertEquals("fintech-a", pending.text("clientId"));
        assertEquals("AWAITING_AUTHORISATION", pending.text("status"));
        assertTrue(pending.body().get("customerId").isNull(), "chưa khách hàng nào duyệt");

        assertEquals(404, internal("GET", "/internal/consents/khong-ton-tai", null).status());
        assertEquals(405, internal("DELETE", "/internal/consents/" + id, null).status());
        assertEquals(404, partner("GET", "/internal/consents/" + id, "fintech-a", null).status(),
                "API nội bộ không có trên cổng đối tác");
    }

    @Test
    void customerRejectsOrRevokesThroughBankChannel() throws Exception {
        String rejected = createAccountsConsent("fintech-a");
        Reply rejection = internal("POST", "/internal/consents/" + rejected + "/rejection", "{\"customerId\":\"cif-1\"}");
        assertEquals("REJECTED", rejection.text("status"));

        String revoked = createAccountsConsent("fintech-a");
        internal("POST", "/internal/consents/" + revoked + "/authorisation",
                "{\"customerId\":\"cif-1\",\"accountIds\":[\"acc-1\"]}");
        assertEquals(404, internal("POST", "/internal/consents/" + revoked + "/revocation",
                "{\"customerId\":\"cif-2\"}").status());
        assertEquals("REVOKED", internal("POST", "/internal/consents/" + revoked + "/revocation",
                "{\"customerId\":\"cif-1\"}").text("status"));
    }

    @Test
    void customerSeesOwnConsentsThroughBankChannel() throws Exception {
        String first = createAccountsConsent("fintech-a");
        internal("POST", "/internal/consents/" + first + "/authorisation",
                "{\"customerId\":\"cif-1\",\"accountIds\":[\"acc-1\"]}");
        String second = createAccountsConsent("fintech-b");
        internal("POST", "/internal/consents/" + second + "/rejection", "{\"customerId\":\"cif-1\"}");
        String other = createAccountsConsent("fintech-a");
        internal("POST", "/internal/consents/" + other + "/authorisation",
                "{\"customerId\":\"cif-2\",\"accountIds\":[\"acc-9\"]}");

        Reply list = internal("GET", "/internal/customers/cif-1/consents", null);
        assertEquals(200, list.status());
        JsonNode consents = list.body().get("consents");
        assertEquals(2, consents.size());
        Set<String> ids = new HashSet<>();
        consents.forEach(consent -> {
            ids.add(consent.get("consentId").asText());
            assertEquals("cif-1", consent.get("customerId").asText());
        });
        assertEquals(Set.of(first, second), ids);
        assertEquals(0, internal("GET", "/internal/customers/cif-3/consents", null).body().get("consents").size());
    }

    // ------------------------------------------------------------------ quy định của Thông tư

    @Test
    void paymentScopesAreReservedForBanksAndPaymentIntermediaries() throws Exception {
        String body = "{\"scopes\":[\"EWALLET_TOPUP\"],\"validityDays\":30}";
        Reply denied = partner("POST", "/consents", "fintech-a", body);
        assertEquals(403, denied.status());
        assertEquals("scope_not_permitted", denied.text("reason"));
        assertEquals("application/problem+json; charset=utf-8",
                denied.raw().headers().firstValue("Content-Type").orElseThrow());

        assertEquals(201, partner("POST", "/consents", "wallet-a", body).status());
    }

    @Test
    void validityIsCappedAt180Days() throws Exception {
        assertEquals(201, partner("POST", "/consents", "fintech-a",
                "{\"scopes\":[\"ACCOUNTS_READ\"],\"validityDays\":180}").status());
        for (String validity : List.of("181", "0", "-1", "1.5", "\"30\"")) {
            Reply reply = partner("POST", "/consents", "fintech-a",
                    "{\"scopes\":[\"ACCOUNTS_READ\"],\"validityDays\":" + validity + "}");
            assertEquals(400, reply.status(), "validityDays=" + validity);
            assertEquals("invalid_request", reply.text("reason"));
        }
    }

    // ------------------------------------------------------------------ danh tính và phân quyền

    @Test
    void partnerIdentityComesFromGatewayHeader() throws Exception {
        String body = "{\"scopes\":[\"ACCOUNTS_READ\"],\"validityDays\":30}";
        assertEquals("client_unauthenticated", partner("POST", "/consents", null, body).text("reason"));
        Reply unknown = partner("POST", "/consents", "fintech-z", body);
        assertEquals(403, unknown.status());
        assertEquals("client_not_registered", unknown.text("reason"));
    }

    @Test
    void partnerSeesOnlyItsOwnConsents() throws Exception {
        String id = createAccountsConsent("fintech-a");
        assertEquals(404, partner("GET", "/consents/" + id, "fintech-b", null).status());
        assertEquals(404, partner("DELETE", "/consents/" + id, "fintech-b", null).status());
        assertEquals(404, partner("GET", "/consents/khong-co", "fintech-a", null).status());
    }

    @Test
    void internalApiIsNotServedOnPartnerPort() throws Exception {
        String id = createAccountsConsent("fintech-a");
        HttpResponse<String> response = send(server.partnerAddress(), "POST",
                "/internal/consents/" + id + "/authorisation", null,
                "{\"customerId\":\"cif-1\",\"accountIds\":[\"acc-1\"]}");
        assertEquals(404, response.statusCode());
        assertEquals("consent_awaiting_authorisation", check(id, "fintech-a", "ACCOUNTS_READ", "acc-1").text("reason"));
    }

    // ------------------------------------------------------------------ request sai

    @Test
    void rejectsMalformedRequests() throws Exception {
        assertEquals(400, partner("POST", "/consents", "fintech-a", "{không phải json").status());
        assertEquals(400, partner("POST", "/consents", "fintech-a", "[]").status());
        assertEquals(400, partner("POST", "/consents", "fintech-a", "{\"scopes\":[],\"validityDays\":1}").status());
        assertEquals(400, partner("POST", "/consents", "fintech-a",
                "{\"scopes\":[\"ACCOUNTS_READ\",\"ACCOUNTS_READ\"],\"validityDays\":1}").status());
        assertEquals(400, partner("POST", "/consents", "fintech-a",
                "{\"scopes\":[\"BALANCES_READ\"],\"validityDays\":1}").status());
        assertEquals(400, internal("POST", "/internal/consent-checks",
                "{\"consentId\":\"x\",\"clientId\":\"fintech-a\",\"scope\":\"NOPE\"}").status());
        assertEquals(400, internal("POST", "/internal/consent-checks", "{\"clientId\":\"fintech-a\"}").status());

        Reply tooLarge = partner("POST", "/consents", "fintech-a",
                "{\"pad\":\"" + "x".repeat(70_000) + "\"}");
        assertEquals(413, tooLarge.status());

        Reply wrongMethod = partner("PUT", "/consents", "fintech-a", "{}");
        assertEquals(405, wrongMethod.status());
        assertEquals("POST", wrongMethod.raw().headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void echoesFapiInteractionId() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(server.partnerAddress(), "/consents/x"))
                .header(ConsentHttpServer.CLIENT_ID_HEADER, "fintech-a")
                .header("x-fapi-interaction-id", "93bac548-d2de-4546-b106-880a5018460d")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("93bac548-d2de-4546-b106-880a5018460d",
                response.headers().firstValue("x-fapi-interaction-id").orElseThrow());

        HttpResponse<String> generated = send(server.partnerAddress(), "GET", "/consents/x", "fintech-a", null);
        assertEquals(36, generated.headers().firstValue("x-fapi-interaction-id").orElseThrow().length());
    }

    // ------------------------------------------------------------------ tiện ích

    private String createAccountsConsent(String clientId) throws Exception {
        Reply created = partner("POST", "/consents", clientId, "{\"scopes\":[\"ACCOUNTS_READ\"],\"validityDays\":30}");
        assertEquals(201, created.status());
        return created.text("consentId");
    }

    private Reply check(String consentId, String clientId, String scope, String accountId) throws Exception {
        Reply reply = internal("POST", "/internal/consent-checks", JSON.writeValueAsString(Map.of(
                "consentId", consentId, "clientId", clientId, "scope", scope, "accountId", accountId)));
        assertEquals(200, reply.status());
        return reply;
    }

    private Reply partner(String method, String path, String clientId, String body) throws Exception {
        return reply(send(server.partnerAddress(), method, path, clientId, body));
    }

    private Reply internal(String method, String path, String body) throws Exception {
        return reply(send(server.internalAddress(), method, path, null, body));
    }

    private HttpResponse<String> send(InetSocketAddress address, String method, String path, String clientId,
                                      String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(address, path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (clientId != null) {
            request.header(ConsentHttpServer.CLIENT_ID_HEADER, clientId);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Reply reply(HttpResponse<String> response) throws IOException {
        JsonNode body = response.body().isEmpty() ? null : JSON.readTree(response.body());
        return new Reply(response.statusCode(), response, body);
    }

    private static URI uri(InetSocketAddress address, String path) {
        return URI.create("http://" + address.getAddress().getHostAddress() + ":" + address.getPort() + path);
    }
}
