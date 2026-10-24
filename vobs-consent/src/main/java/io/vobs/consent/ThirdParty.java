package io.vobs.consent;

import java.util.Objects;

/**
 * Bên thứ ba (đối tác) đã ký hợp đồng kết nối Open API với ngân hàng.
 *
 * @param clientId mã client cấp khi đăng ký trên Keycloak
 * @param type loại tổ chức, quyết định nhóm API được phép dùng (xem {@link ConsentPolicy})
 */
public record ThirdParty(String clientId, Type type) {

    public enum Type {
        BANK,
        /** Tổ chức cung ứng dịch vụ trung gian thanh toán được Ngân hàng Nhà nước cấp phép. */
        PAYMENT_INTERMEDIARY,
        /** Fintech và tổ chức khác: chỉ dùng được nhóm truy vấn thông tin. */
        OTHER
    }

    public ThirdParty {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(type, "type");
    }
}
