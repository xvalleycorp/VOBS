package io.vobs.keycloak;

import io.vobs.keycloak.ConsentServiceClient.ConsentServiceException;
import io.vobs.keycloak.ConsentServiceClient.ConsentView;
import io.vobs.keycloak.CustomerAccounts.Account;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.LoginProtocol;
import org.keycloak.protocol.oidc.endpoints.AuthorizationEndpoint;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Bước khách hàng duyệt consent trong luồng đăng nhập của Keycloak, đặt sau bước xác thực khách hàng.
 *
 * <p>Đối tác tạo consent qua API ({@code POST /consents}), rồi gửi Pushed Authorization Request kèm tham số
 * {@value #CONSENT_ID_PARAM}. Keycloak lưu tham số này trong phiên đăng nhập. Sau khi khách hàng đăng nhập, bước này:
 *
 * <ol>
 *   <li>đọc consent từ service Quản lý sự đồng ý và kiểm tra consent thuộc đúng đối tác đang chuyển khách hàng sang,
 *       còn chờ duyệt, chưa gắn với khách hàng khác;</li>
 *   <li>hiện trang duyệt: tên đối tác, phạm vi dữ liệu, thời hạn, các tài khoản của khách hàng để chọn;</li>
 *   <li>khách hàng duyệt thì ghi kết quả vào service consent và lưu mã consent vào phiên của đối tác, để
 *       {@link ConsentIdProtocolMapper} gắn vào mã truy cập; khách hàng từ chối thì ghi từ chối và trả
 *       {@code access_denied} về cho đối tác.</li>
 * </ol>
 *
 * <p>Mọi trường hợp consent không hợp lệ đều trả {@code access_denied} về {@code redirect_uri} của đối tác, kèm mô tả
 * ngắn, không tiết lộ chi tiết consent.
 */
final class ConsentAuthorisationAuthenticator implements Authenticator {

    /** Tham số đối tác gửi trong Pushed Authorization Request. */
    static final String CONSENT_ID_PARAM = "consent_id";
    /** Ghi chú của phiên đối tác sau khi khách hàng duyệt; {@link ConsentIdProtocolMapper} đọc ghi chú này. */
    static final String CONSENT_NOTE = "vobs.consent_id";

    private static final Logger LOG = Logger.getLogger(ConsentAuthorisationAuthenticator.class);
    private static final String REQUEST_PARAM_NOTE =
            AuthorizationEndpoint.LOGIN_SESSION_NOTE_ADDITIONAL_REQ_PARAMS_PREFIX + CONSENT_ID_PARAM;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> PAYMENT_SCOPES = Set.of("PAYMENTS_INITIATE", "EWALLET_TOPUP",
            "EWALLET_WITHDRAWAL");

    private final ConsentServiceClient consents;

    ConsentAuthorisationAuthenticator(ConsentServiceClient consents) {
        this.consents = consents;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        loadPending(context).ifPresent(consent -> context.challenge(page(context, consent, null)));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        Optional<ConsentView> pending = loadPending(context);
        if (pending.isEmpty()) {
            return;
        }
        ConsentView consent = pending.get();
        String customerId = CustomerAccounts.customerId(context.getUser());
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        try {
            if (form.containsKey("reject")) {
                consents.reject(consent.consentId(), customerId);
                deny(context, "consent_rejected_by_customer", "Khách hàng từ chối consent");
                return;
            }
            if (!form.containsKey("approve")) {
                context.challenge(page(context, consent, null));
                return;
            }
            Set<String> owned = new LinkedHashSet<>();
            CustomerAccounts.of(context.getUser()).forEach(account -> owned.add(account.id()));
            List<String> chosen = Optional.ofNullable(form.get("accountId")).orElse(List.of()).stream()
                    .distinct().toList();
            if (!owned.containsAll(chosen)) {
                // Chỉ xảy ra khi form bị sửa: tài khoản không thuộc khách hàng này
                deny(context, "account_not_owned", "Tài khoản không thuộc khách hàng");
                return;
            }
            if (chosen.isEmpty() && accountsRequired(consent)) {
                context.challenge(page(context, consent, "vobsConsentSelectAccount"));
                return;
            }
            consents.authorise(consent.consentId(), customerId, chosen);
            context.getAuthenticationSession().setClientNote(CONSENT_NOTE, consent.consentId());
            context.getEvent().detail("consent_id", consent.consentId());
            context.success();
        } catch (ConsentServiceException e) {
            LOG.warnf("Không ghi được quyết định cho consent %s: %s (lý do %s)", consent.consentId(), e.getMessage(),
                    e.reason());
            deny(context, "consent_service_error", "Không ghi được quyết định của khách hàng");
        }
    }

    /**
     * Consent đang chờ khách hàng này duyệt; nếu không hợp lệ thì đã trả lỗi về đối tác và trả về rỗng.
     */
    private Optional<ConsentView> loadPending(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        String consentId = session.getClientNote(REQUEST_PARAM_NOTE);
        if (consentId == null || consentId.isBlank()) {
            deny(context, "consent_id_missing", "Thiếu tham số consent_id");
            return Optional.empty();
        }
        Optional<ConsentView> found;
        try {
            found = consents.find(consentId);
        } catch (ConsentServiceException e) {
            LOG.warnf("Không đọc được consent %s: %s", consentId, e.getMessage());
            deny(context, "consent_service_error", "Không đọc được consent");
            return Optional.empty();
        }
        String clientId = session.getClient().getClientId();
        String customerId = CustomerAccounts.customerId(context.getUser());
        // Không phân biệt các trường hợp với đối tác, để đối tác không dò được consent của bên khác
        boolean valid = found.isPresent()
                && clientId.equals(found.get().clientId())
                && ConsentView.AWAITING_AUTHORISATION.equals(found.get().status())
                && (found.get().customerId() == null || customerId.equals(found.get().customerId()));
        if (!valid) {
            context.getEvent().detail("consent_id", consentId);
            deny(context, "consent_invalid", "Consent không tồn tại, không thuộc đối tác hoặc không còn chờ duyệt");
            return Optional.empty();
        }
        return found;
    }

    private Response page(AuthenticationFlowContext context, ConsentView consent, String error) {
        List<ConsentPage.AccountOption> accounts = CustomerAccounts.of(context.getUser()).stream()
                .map((Account account) -> new ConsentPage.AccountOption(account.id(), account.label()))
                .toList();
        String clientName = context.getAuthenticationSession().getClient().getName();
        if (clientName == null || clientName.isBlank()) {
            clientName = consent.clientId();
        }
        ConsentPage page = new ConsentPage(clientName, consent.scopes(), expiryDate(consent.expiresAt()), accounts,
                accountsRequired(consent));
        var form = context.form().setAttribute("vobsConsent", page);
        if (error != null) {
            form.setError(error);
        }
        return form.createForm("vobs-consent.ftl");
    }

    private static boolean accountsRequired(ConsentView consent) {
        return !PAYMENT_SCOPES.containsAll(consent.scopes());
    }

    private static String expiryDate(String expiresAt) {
        try {
            return DATE.format(OffsetDateTime.parse(expiresAt));
        } catch (DateTimeParseException | NullPointerException e) {
            return expiresAt;
        }
    }

    /** Trả {@code access_denied} về {@code redirect_uri} của đối tác và kết thúc luồng đăng nhập. */
    private static void deny(AuthenticationFlowContext context, String reason, String description) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        LoginProtocol protocol = context.getSession().getProvider(LoginProtocol.class, session.getProtocol());
        protocol.setRealm(context.getRealm())
                .setHttpHeaders(context.getHttpRequest().getHttpHeaders())
                .setUriInfo(context.getUriInfo())
                .setEventBuilder(context.getEvent());
        Response response = protocol.sendError(session, LoginProtocol.Error.CONSENT_DENIED, description);
        context.failure(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR, response, reason, null);
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // Không cần hành động bắt buộc nào
    }

    @Override
    public void close() {
        // Client HTTP dùng chung, đóng cùng factory
    }
}
