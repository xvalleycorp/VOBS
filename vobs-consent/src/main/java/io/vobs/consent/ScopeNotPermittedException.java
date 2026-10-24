package io.vobs.consent;

import java.util.Set;

/** Đối tác xin phạm vi mà loại tổ chức của họ không được phép dùng (Điều 5 Thông tư 64/2024/TT-NHNN). */
public class ScopeNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Set<ConsentScope> scopes;

    public ScopeNotPermittedException(ThirdParty client, Set<ConsentScope> scopes) {
        super("Đối tác %s (%s) không được dùng phạm vi %s; nhóm thanh toán chỉ dành cho ngân hàng và tổ chức trung gian thanh toán"
                .formatted(client.clientId(), client.type(), scopes));
        this.scopes = Set.copyOf(scopes);
    }

    public Set<ConsentScope> scopes() {
        return scopes;
    }
}
