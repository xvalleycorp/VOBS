package io.vobs.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.vobs.keycloak.ConsentServiceClient.ConsentServiceException;
import io.vobs.keycloak.ConsentServiceClient.ConsentView;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsentServiceClientTest {

    private static final String PENDING = """
            {"consentId":"c-1","status":"AWAITING_AUTHORISATION","scopes":["ACCOUNTS_READ","TRANSACTIONS_READ"],
             "accountIds":[],"createdAt":"2026-10-25T09:00:00+07:00","expiresAt":"2027-01-23T09:00:00+07:00",
             "clientId":"fintech-a","customerId":null,"statusChangedAt":"2026-10-25T09:00:00+07:00"}
            """;

    private HttpServer server;
    private HttpClient http;
    private ConsentServiceClient client;
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getRawPath();
            requests.add(exchange.getRequestMethod() + " " + path + " " + body);
            int status;
            String reply;
            if (path.equals("/internal/consents/c-1")) {
                status = 200;
                reply = PENDING;
            } else if (path.equals("/internal/consents/c-1/authorisation")) {
                status = 200;
                reply = PENDING.replace("AWAITING_AUTHORISATION", "AUTHORISED").replace("null", "\"cif-2001\"");
            } else if (path.equals("/internal/consents/c-done/rejection")) {
                status = 409;
                reply = "{\"status\":409,\"reason\":\"consent_state_conflict\"}";
            } else {
                status = 404;
                reply = "{\"status\":404,\"reason\":\"not_found\"}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        http = HttpClient.newHttpClient();
        client = new ConsentServiceClient(
                URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()), http);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        http.close();
    }

    @Test
    void readsPendingConsent() {
        ConsentView consent = client.find("c-1").orElseThrow();
        assertEquals("fintech-a", consent.clientId());
        assertEquals(ConsentView.AWAITING_AUTHORISATION, consent.status());
        assertEquals(List.of("ACCOUNTS_READ", "TRANSACTIONS_READ"), consent.scopes());
        assertEquals(null, consent.customerId());
        assertTrue(client.find("khong-ton-tai").isEmpty());
    }

    @Test
    void sendsCustomerDecision() {
        ConsentView authorised = client.authorise("c-1", "cif-2001", List.of("acc-1001"));
        assertEquals("AUTHORISED", authorised.status());
        assertEquals("POST /internal/consents/c-1/authorisation {\"customerId\":\"cif-2001\",\"accountIds\":[\"acc-1001\"]}",
                requests.getLast());
    }

    @Test
    void reportsRefusalWithReason() {
        ConsentServiceException e = assertThrows(ConsentServiceException.class,
                () -> client.reject("c-done", "cif-2001"));
        assertEquals(409, e.status());
        assertEquals("consent_state_conflict", e.reason());
    }

    @Test
    void encodesConsentIdInPath() {
        assertTrue(client.find("../consent-checks").isEmpty());
        assertEquals("GET /internal/consents/..%2Fconsent-checks ", requests.getLast());
    }
}
