package io.vobs.keycloak;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.keycloak.models.UserModel;

/**
 * Khách hàng ứng với người dùng Keycloak và các tài khoản khách hàng chọn được khi duyệt consent.
 *
 * <p>Đọc từ thuộc tính người dùng: {@value #CUSTOMER_ID_ATTRIBUTE} là mã khách hàng (không có thì dùng username),
 * {@value #ACCOUNT_ATTRIBUTE} là các tài khoản, mỗi giá trị dạng {@code <mã tài khoản>|<tên hiển thị>}. Ngân hàng
 * thường nạp hai thuộc tính này từ hệ thống của mình qua User Federation (LDAP hoặc provider riêng nối core), hoặc
 * thay lớp này bằng lớp gọi thẳng core banking.
 */
final class CustomerAccounts {

    static final String CUSTOMER_ID_ATTRIBUTE = "customer_id";
    static final String ACCOUNT_ATTRIBUTE = "vobs_account";

    private CustomerAccounts() {
    }

    static String customerId(UserModel user) {
        String customerId = user.getFirstAttribute(CUSTOMER_ID_ATTRIBUTE);
        return customerId == null || customerId.isBlank() ? user.getUsername() : customerId;
    }

    static List<Account> of(UserModel user) {
        return parse(user.getAttributeStream(ACCOUNT_ATTRIBUTE));
    }

    static List<Account> parse(Stream<String> values) {
        return values.filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    int separator = value.indexOf('|');
                    String id = (separator < 0 ? value : value.substring(0, separator)).strip();
                    String label = separator < 0 ? "" : value.substring(separator + 1).strip();
                    return new Account(id, label.isEmpty() ? id : label);
                })
                .filter(account -> !account.id().isEmpty())
                .distinct()
                .toList();
    }

    /** Một tài khoản của khách hàng: mã dùng trong API và tên hiển thị trên trang duyệt. */
    record Account(String id, String label) {
    }
}
