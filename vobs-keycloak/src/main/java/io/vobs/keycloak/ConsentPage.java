package io.vobs.keycloak;

import java.util.List;

/**
 * Dữ liệu cho trang duyệt consent ({@code theme/vobs/login/vobs-consent.ftl}). Dùng getter kiểu JavaBean để
 * FreeMarker của Keycloak đọc được.
 */
public final class ConsentPage {

    private final String clientName;
    private final List<String> scopes;
    private final String expiresOn;
    private final List<AccountOption> accounts;
    private final boolean accountsRequired;

    ConsentPage(String clientName, List<String> scopes, String expiresOn, List<AccountOption> accounts,
                boolean accountsRequired) {
        this.clientName = clientName;
        this.scopes = List.copyOf(scopes);
        this.expiresOn = expiresOn;
        this.accounts = List.copyOf(accounts);
        this.accountsRequired = accountsRequired;
    }

    /** Tên đối tác đã đăng ký với ngân hàng. */
    public String getClientName() {
        return clientName;
    }

    /** Mã phạm vi; trang hiển thị bằng thông điệp {@code vobsScope.<mã>}. */
    public List<String> getScopes() {
        return scopes;
    }

    /** Ngày hết hạn, dạng dd/MM/yyyy theo giờ Việt Nam. */
    public String getExpiresOn() {
        return expiresOn;
    }

    public List<AccountOption> getAccounts() {
        return accounts;
    }

    /** Consent có phạm vi đọc dữ liệu thì phải chọn ít nhất một tài khoản. */
    public boolean isAccountsRequired() {
        return accountsRequired;
    }

    /** Một tài khoản khách hàng chọn được. */
    public static final class AccountOption {

        private final String id;
        private final String label;

        AccountOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }
    }
}
