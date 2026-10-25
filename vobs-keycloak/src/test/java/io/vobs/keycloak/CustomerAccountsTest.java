package io.vobs.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vobs.keycloak.CustomerAccounts.Account;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CustomerAccountsTest {

    @Test
    void parsesIdAndLabel() {
        assertEquals(List.of(new Account("acc-1001", "Tài khoản thanh toán"), new Account("acc-2002", "acc-2002")),
                CustomerAccounts.parse(Stream.of(" acc-1001 | Tài khoản thanh toán ", "acc-2002")));
    }

    @Test
    void skipsBlankAndDuplicateValues() {
        assertEquals(List.of(new Account("acc-1", "A")),
                CustomerAccounts.parse(Stream.of("", "  ", "|Không có mã", "acc-1|A", "acc-1|A", null)));
    }
}
