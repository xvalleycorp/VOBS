package io.vobs.consent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import vn.io.dxa.rulestone.api.AuditSink;
import vn.io.dxa.rulestone.api.DecisionRef;
import vn.io.dxa.rulestone.api.DecisionResult;
import vn.io.dxa.rulestone.api.Facts;
import vn.io.dxa.rulestone.api.RuleStoneEngine;
import vn.io.dxa.rulestone.core.DecisionTable;
import vn.io.dxa.rulestone.core.DecisionTableJson;
import vn.io.dxa.rulestone.core.RuleStone;

/**
 * Chính sách nhận yêu cầu consent, viết bằng bảng quyết định <a href="https://rulestone.dxa.io.vn">RuleStone</a>
 * thay vì mã Java: loại bên thứ ba nào được xin phạm vi nào (Điều 5 Thông tư 64/2024/TT-NHNN) và thời hạn tối đa
 * (Điều 11). Mỗi phiên bản bảng có ngày hiệu lực. Khi quy định thay đổi hoặc ngân hàng áp chính sách chặt hơn, thêm
 * phiên bản bảng mới, không sửa mã.
 *
 * <p>Bảng {@code consent_request} nhận các input {@code thirdPartyType}, {@code scopes} (mỗi phần tử có
 * {@code name} và {@code paymentGroup}) và {@code validityDays}. Mỗi quy tắc phải đặt output {@code allowed}; quy tắc
 * từ chối đặt thêm {@code reasonCode}. Hai mã lý do có cách xử lý riêng: {@value #VALIDITY_OUT_OF_RANGE} (kèm output
 * {@code maxValidityDays}) và {@value #SCOPE_NOT_PERMITTED} (kèm output {@code notPermittedScopes}). Mã khác là chính
 * sách riêng của ngân hàng.
 *
 * <p>Bảng mặc định nằm trong classpath ({@code rules/consent_request.v1.stone.json}). Ngân hàng dùng bảng riêng bằng
 * {@link #fromDirectory(Path, AuditSink)}. Bảng được kiểm tra kiểu khi nạp: bảng sai làm service dừng khởi động thay vì
 * lỗi khi đối tác gọi.
 */
public final class ConsentPolicy {

    /** Mã bảng quyết định. */
    public static final String DECISION_ID = "consent_request";

    /** Thời hạn nằm ngoài khoảng cho phép. */
    public static final String VALIDITY_OUT_OF_RANGE = "VALIDITY_OUT_OF_RANGE";

    /** Bên thứ ba xin phạm vi mà loại tổ chức của họ không được dùng. */
    public static final String SCOPE_NOT_PERMITTED = "SCOPE_NOT_PERMITTED";

    /** Múi giờ xác định ngày nghiệp vụ, từ đó chọn phiên bản bảng đang có hiệu lực. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<String> BUILT_IN = List.of("rules/consent_request.v1.stone.json");

    private final RuleStoneEngine engine;

    private ConsentPolicy(Collection<DecisionTable> tables, AuditSink auditSink) {
        RuleStone.Builder builder = RuleStone.builder().clock(Clock.system(BUSINESS_ZONE)).decisions(tables);
        if (auditSink != null) {
            builder.auditSink(auditSink);
        }
        this.engine = builder.build();
    }

    /** Bảng mặc định theo Thông tư 64, không ghi bản ghi quyết định ra ngoài. */
    public static ConsentPolicy builtIn() {
        return builtIn(null);
    }

    /**
     * Bảng mặc định theo Thông tư 64.
     *
     * @param auditSink nhận bản ghi của mọi lần quyết định (không chứa giá trị input), hoặc {@code null}
     */
    public static ConsentPolicy builtIn(AuditSink auditSink) {
        List<DecisionTable> tables = new ArrayList<>();
        for (String resource : BUILT_IN) {
            try (InputStream in = ConsentPolicy.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Thiếu " + resource + " trong classpath");
                }
                tables.add(DecisionTableJson.read(in));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new ConsentPolicy(tables, auditSink);
    }

    /**
     * Mọi bảng {@code *.stone.json} trong một thư mục, thay cho bảng mặc định. Thư mục phải chứa mọi phiên bản của
     * bảng {@code consent_request}, kể cả phiên bản cũ, để consent cũ vẫn tra được phiên bản đã áp dụng.
     *
     * @param auditSink nhận bản ghi của mọi lần quyết định (không chứa giá trị input), hoặc {@code null}
     */
    public static ConsentPolicy fromDirectory(Path directory, AuditSink auditSink) {
        List<DecisionTable> tables;
        try (Stream<Path> files = Files.list(directory)) {
            tables = files.filter(file -> file.getFileName().toString().endsWith(".stone.json")).sorted()
                    .map(DecisionTableJson::read).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được thư mục bảng quyết định " + directory, e);
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("Không có file *.stone.json trong " + directory);
        }
        return new ConsentPolicy(tables, auditSink);
    }

    /**
     * Xét một yêu cầu consent theo phiên bản bảng có hiệu lực vào ngày nghiệp vụ.
     *
     * @param consentId mã consent sẽ cấp, ghi vào bản ghi quyết định để đối chiếu
     * @return phiên bản bảng đã áp dụng, dạng {@code <version>@sha256:<hash>}
     * @throws IllegalArgumentException nếu thời hạn ngoài khoảng cho phép
     * @throws ScopeNotPermittedException nếu loại tổ chức của bên thứ ba không được dùng phạm vi đã xin
     * @throws ConsentRejectedException nếu chính sách khác của ngân hàng từ chối
     */
    String evaluate(ThirdParty client, Set<ConsentScope> scopes, long validityDays, LocalDate businessDate,
                    String consentId) {
        List<Map<String, Object>> requested = scopes.stream().sorted()
                .map(scope -> Map.<String, Object>of("name", scope.name(), "paymentGroup", scope.isPaymentGroup()))
                .toList();
        DecisionResult result = engine.evaluate(
                DecisionRef.of(DECISION_ID).asOf(businessDate).correlationId(consentId),
                Facts.of("thirdPartyType", client.type().name(), "scopes", requested, "validityDays", validityDays));
        Map<String, Object> output = result.output();
        if (!(output.get("allowed") instanceof Boolean allowed)) {
            throw new IllegalStateException("Bảng %s %s không đặt output allowed cho yêu cầu này".formatted(
                    DECISION_ID, result.ruleVersion()));
        }
        if (allowed) {
            return result.ruleVersion();
        }
        String reasonCode = result.reasonCodes().isEmpty() ? "CONSENT_POLICY" : result.reasonCodes().getFirst();
        switch (reasonCode) {
            case VALIDITY_OUT_OF_RANGE -> throw new IllegalArgumentException(
                    output.get("maxValidityDays") instanceof Long max
                            ? "Thời hạn consent phải từ 1 đến %d ngày".formatted(max)
                            : "Thời hạn consent nằm ngoài khoảng cho phép");
            case SCOPE_NOT_PERMITTED -> throw new ScopeNotPermittedException(client, notPermitted(output));
            default -> throw new ConsentRejectedException(reasonCode, result.ruleVersion());
        }
    }

    private static Set<ConsentScope> notPermitted(Map<String, Object> output) {
        if (!(output.get("notPermittedScopes") instanceof List<?> names)) {
            return Set.of();
        }
        return names.stream().map(name -> ConsentScope.valueOf((String) name)).collect(Collectors.toSet());
    }
}
