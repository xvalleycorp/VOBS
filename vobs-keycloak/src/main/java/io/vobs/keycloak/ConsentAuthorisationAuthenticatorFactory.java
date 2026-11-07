package io.vobs.keycloak;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Đăng ký {@link ConsentAuthorisationAuthenticator} với Keycloak. Địa chỉ API nội bộ của service Quản lý sự đồng ý
 * lấy từ biến môi trường {@value #CONSENT_URL_ENV}.
 */
public final class ConsentAuthorisationAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "vobs-consent-authorisation";
    static final String CONSENT_URL_ENV = "VOBS_CONSENT_INTERNAL_URL";
    private static final String DEFAULT_CONSENT_URL = "http://localhost:8081";

    private HttpClient http;
    private ConsentServiceClient consents;

    @Override
    public void init(Config.Scope config) {
        String url = System.getenv(CONSENT_URL_ENV);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        consents = new ConsentServiceClient(URI.create(url == null || url.isBlank() ? DEFAULT_CONSENT_URL : url),
                http);
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new ConsentAuthorisationAuthenticator(consents);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Không cần
    }

    @Override
    public void close() {
        if (http != null) {
            http.close();
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "VOBS: khách hàng duyệt consent";
    }

    @Override
    public String getHelpText() {
        return "Hiện trang duyệt consent của Open API (phạm vi, thời hạn, chọn tài khoản) sau khi khách hàng đăng nhập,"
                + " ghi kết quả vào service Quản lý sự đồng ý và gắn mã consent vào mã truy cập.";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
            AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED,
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
