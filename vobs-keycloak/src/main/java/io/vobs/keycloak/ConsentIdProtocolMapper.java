package io.vobs.keycloak;

import java.util.ArrayList;
import java.util.List;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.TokenIntrospectionTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

/**
 * Gắn mã consent khách hàng đã duyệt vào token, claim {@value #CLAIM}. Mã truy cập vì vậy chỉ dùng được với đúng
 * consent đó: API Gateway lấy consent từ claim này thay vì tin header của đối tác.
 *
 * <p>Giá trị lấy từ phiên của đối tác (client session), do {@link ConsentAuthorisationAuthenticator} lưu khi khách hàng
 * duyệt, nên mã truy cập cấp lại bằng refresh token vẫn mang đúng consent. Token client credentials không có claim
 * này.
 */
public final class ConsentIdProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, TokenIntrospectionTokenMapper {

    public static final String PROVIDER_ID = "vobs-consent-id-mapper";
    public static final String CLAIM = "consent_id";

    private static final List<ProviderConfigProperty> CONFIG = new ArrayList<>();

    static {
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(CONFIG, ConsentIdProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mapping, UserSessionModel userSession,
                            KeycloakSession session, ClientSessionContext clientSessionCtx) {
        String consentId = clientSessionCtx.getClientSession().getNote(ConsentAuthorisationAuthenticator.CONSENT_NOTE);
        if (consentId != null && !consentId.isBlank()) {
            token.getOtherClaims().put(CLAIM, consentId);
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "VOBS: mã consent";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Gắn mã consent khách hàng đã duyệt vào token, claim consent_id.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }
}
