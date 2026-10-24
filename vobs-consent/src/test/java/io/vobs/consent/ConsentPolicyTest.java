package io.vobs.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.io.dxa.rulestone.api.DecisionNotFoundException;
import vn.io.dxa.rulestone.api.DecisionRecord;
import vn.io.dxa.rulestone.core.DecisionRecordJson;
import vn.io.dxa.rulestone.core.InvalidDecisionException;

class ConsentPolicyTest {

    private static final ThirdParty FINTECH = new ThirdParty("fintech-a", ThirdParty.Type.OTHER);
    private static final ThirdParty WALLET = new ThirdParty("wallet-b", ThirdParty.Type.PAYMENT_INTERMEDIARY);
    private static final ThirdParty BANK = new ThirdParty("bank-c", ThirdParty.Type.BANK);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    private final ConsentPolicy builtIn = ConsentPolicy.builtIn();

    private String evaluate(ConsentPolicy policy, ThirdParty client, Set<ConsentScope> scopes, long days,
                            LocalDate date) {
        return policy.evaluate(client, scopes, days, date, "consent-1");
    }

    @Test
    void builtInTableFollowsCircular64() {
        String version = evaluate(builtIn, FINTECH, Set.of(ConsentScope.ACCOUNTS_READ), 180, TODAY);
        assertTrue(version.matches("1@sha256:[0-9a-f]{64}"), version);

        for (long days : List.of(0L, -1L, 181L)) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> evaluate(builtIn, BANK, Set.of(ConsentScope.ACCOUNTS_READ), days, TODAY));
            assertEquals("Thời hạn consent phải từ 1 đến 180 ngày", error.getMessage());
        }
    }

    @Test
    void paymentGroupOnlyForBanksAndPaymentIntermediaries() {
        Set<ConsentScope> requested = Set.of(ConsentScope.ACCOUNTS_READ, ConsentScope.PAYMENTS_INITIATE,
                ConsentScope.EWALLET_TOPUP);
        ScopeNotPermittedException error = assertThrows(ScopeNotPermittedException.class,
                () -> evaluate(builtIn, FINTECH, requested, 30, TODAY));
        assertEquals(Set.of(ConsentScope.PAYMENTS_INITIATE, ConsentScope.EWALLET_TOPUP), error.scopes());

        evaluate(builtIn, WALLET, requested, 30, TODAY);
        evaluate(builtIn, BANK, requested, 30, TODAY);
    }

    @Test
    void notInForceBeforeTheCircularTookEffect() {
        assertThrows(DecisionNotFoundException.class,
                () -> evaluate(builtIn, FINTECH, Set.of(ConsentScope.ACCOUNTS_READ), 30, LocalDate.of(2025, 2, 28)));
    }

    @Test
    void decisionRecordsCarryTheConsentIdButNoInputValues() {
        List<DecisionRecord> records = new ArrayList<>();
        ConsentPolicy policy = ConsentPolicy.builtIn(records::add);
        assertThrows(ScopeNotPermittedException.class,
                () -> evaluate(policy, FINTECH, Set.of(ConsentScope.EWALLET_TOPUP), 30, TODAY));

        DecisionRecord record = records.getFirst();
        assertEquals("consent_request", record.decision());
        assertEquals(Optional.of("consent-1"), record.correlationId());
        assertTrue(record.version().orElseThrow().startsWith("1@sha256:"));
        String json = DecisionRecordJson.write(record);
        assertTrue(json.contains("SCOPE_NOT_PERMITTED"), json);
        // Input chỉ có mặt dưới dạng hash, không có giá trị (loại tổ chức, số ngày)
        assertTrue(record.factsHash().isPresent());
        assertFalse(json.contains("OTHER") || json.contains("validityDays"), json);
    }

    /**
     * Ngân hàng thêm phiên bản 2 có hiệu lực từ 01/03/2027: thời hạn tối đa 90 ngày và một quy tắc riêng. Phiên bản 1
     * phải được đóng bằng {@code effectiveTo}, nên hash của nó đổi so với bảng mặc định.
     */
    @Test
    void bankPolicyFromDirectoryTakesEffectOnItsDate(@TempDir Path rules) throws IOException {
        String v1 = builtInTable().replace("\"effectiveFrom\": \"2025-03-01\",",
                "\"effectiveFrom\": \"2025-03-01\",\n  \"effectiveTo\": \"2027-03-01\",");
        Files.writeString(rules.resolve("consent_request.v1.stone.json"), v1);
        Files.writeString(rules.resolve("consent_request.v2.stone.json"), """
                {
                  "id": "consent_request",
                  "version": "2",
                  "effectiveFrom": "2027-03-01",
                  "hitPolicy": "FIRST",
                  "inputs": {
                    "thirdPartyType": "string",
                    "scopes": [{ "name": "string", "paymentGroup": "bool" }],
                    "validityDays": "int"
                  },
                  "outputs": { "allowed": "bool", "maxValidityDays": "int", "notPermittedScopes": ["string"] },
                  "rules": [
                    { "id": "VALIDITY", "when": "validityDays < 1 || validityDays > 90",
                      "then": { "allowed": "false", "maxValidityDays": "90" }, "reasonCode": "VALIDITY_OUT_OF_RANGE" },
                    { "id": "PAYMENT_GROUP",
                      "when": "!(thirdPartyType in ['BANK', 'PAYMENT_INTERMEDIARY']) && scopes.exists(s, s.paymentGroup)",
                      "then": { "allowed": "false", "notPermittedScopes": "scopes.filter(s, s.paymentGroup).map(s, s.name)" },
                      "reasonCode": "SCOPE_NOT_PERMITTED" },
                    { "id": "TRANSACTIONS_NEED_ACCOUNTS",
                      "when": "scopes.exists(s, s.name == 'TRANSACTIONS_READ') && !scopes.exists(s, s.name == 'ACCOUNTS_READ')",
                      "then": { "allowed": "false" }, "reasonCode": "TRANSACTIONS_WITHOUT_ACCOUNTS" },
                    { "id": "ALLOW", "when": "true", "then": { "allowed": "true" } }
                  ]
                }
                """);
        ConsentPolicy policy = ConsentPolicy.fromDirectory(rules, null);
        LocalDate before = LocalDate.of(2027, 2, 28);
        LocalDate after = LocalDate.of(2027, 3, 1);

        String closedV1 = evaluate(policy, FINTECH, Set.of(ConsentScope.TRANSACTIONS_READ), 120, before);
        assertTrue(closedV1.startsWith("1@sha256:"));
        assertNotEquals(evaluate(builtIn, FINTECH, Set.of(ConsentScope.TRANSACTIONS_READ), 120, before), closedV1);

        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> evaluate(policy, FINTECH, Set.of(ConsentScope.ACCOUNTS_READ), 120, after));
        assertEquals("Thời hạn consent phải từ 1 đến 90 ngày", tooLong.getMessage());
        ConsentRejectedException rejected = assertThrows(ConsentRejectedException.class,
                () -> evaluate(policy, FINTECH, Set.of(ConsentScope.TRANSACTIONS_READ), 30, after));
        assertEquals("TRANSACTIONS_WITHOUT_ACCOUNTS", rejected.reasonCode());
        assertTrue(evaluate(policy, FINTECH, Set.of(ConsentScope.ACCOUNTS_READ, ConsentScope.TRANSACTIONS_READ), 90,
                after).startsWith("2@sha256:"));
    }

    @Test
    void invalidTableStopsLoading(@TempDir Path rules) throws IOException {
        Files.writeString(rules.resolve("consent_request.v1.stone.json"),
                builtInTable().replace("validityDays > 180", "validityDay > 180"));
        InvalidDecisionException error = assertThrows(InvalidDecisionException.class,
                () -> ConsentPolicy.fromDirectory(rules, null));
        assertTrue(error.getMessage().contains("rules[VALIDITY].when"), error.getMessage());

        Path empty = Files.createDirectory(rules.resolve("empty"));
        assertThrows(IllegalArgumentException.class, () -> ConsentPolicy.fromDirectory(empty, null));
    }

    private static String builtInTable() throws IOException {
        try (InputStream in = ConsentPolicy.class.getResourceAsStream("rules/consent_request.v1.stone.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
