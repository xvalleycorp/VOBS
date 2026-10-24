package io.vobs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void vndHasNoMinorUnits() {
        assertEquals(Money.vnd(150_000), Money.of("150000.00", "VND"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("1000.5", "VND"));
    }

    @Test
    void usdKeepsTwoDecimals() {
        Money usd = Money.of("10.5", "USD");
        assertEquals("10.50 USD", usd.toString());
        assertThrows(IllegalArgumentException.class, () -> Money.of("0.001", "USD"));
    }

    @Test
    void moneyArithmeticRequiresSameCurrency() {
        assertEquals(Money.vnd(70_000), Money.vnd(100_000).minus(Money.vnd(30_000)));
        assertThrows(IllegalArgumentException.class, () -> Money.vnd(1).plus(Money.of("1", "USD")));
    }

    @Test
    void removesVietnameseDiacritics() {
        assertEquals("NGUYEN DUC ANH", VietnameseText.toSwitchName("  Nguyễn   Đức  Anh "));
        assertEquals("Tran Thi Thuy Duong", VietnameseText.removeDiacritics("Trần Thị Thủy Dương"));
        assertTrue(VietnameseText.namesMatch("Lê Văn Hưng", "LE VAN HUNG"));
        assertFalse(VietnameseText.namesMatch("Lê Văn Hưng", "LE VAN HUNH"));
    }

    @Test
    void decodesCitizenId() {
        CitizenId male1990 = new CitizenId("001090123456");
        assertEquals("001", male1990.provinceCode());
        assertEquals(CitizenId.Gender.MALE, male1990.gender());
        assertEquals(1990, male1990.birthYear());

        CitizenId female2005 = new CitizenId("079305000001");
        assertEquals(CitizenId.Gender.FEMALE, female2005.gender());
        assertEquals(2005, female2005.birthYear());
    }

    @Test
    void rejectsMalformedCitizenId() {
        assertFalse(CitizenId.isValid("12345678901"));
        assertFalse(CitizenId.isValid("00109012345A"));
        assertFalse(CitizenId.isValid("000090123456"));
        assertFalse(CitizenId.isValid("097090123456"));
        assertFalse(CitizenId.isValid(null));
    }

    @Test
    void citizenIdIsMaskedInLogs() {
        assertEquals("001*******56", new CitizenId("001090123456").toString());
    }

    @Test
    void validatesBankId() {
        assertEquals("970436", BankId.ofBin("970436").bin());
        assertThrows(IllegalArgumentException.class, () -> BankId.ofBin("97043"));
        assertThrows(IllegalArgumentException.class, () -> new BankId("970436", "bfte"));
        assertEquals("BFTVVNVX", new BankId("970436", "BFTVVNVX").bicCode().orElseThrow());
    }

    @Test
    void transactionAmountIsUnsignedAndDirectionCarriesSign() {
        Transaction debit = new Transaction("t1", "a1", Transaction.Direction.DEBIT, Money.vnd(50_000),
                Transaction.Status.BOOKED, Instant.parse("2026-10-24T03:00:00Z"), LocalDate.of(2026, 10, 24),
                "Thanh toan hoa don", null, null);
        assertEquals(Money.vnd(-50_000), debit.signedAmount());
        assertThrows(IllegalArgumentException.class, () -> new Transaction("t2", "a1",
                Transaction.Direction.CREDIT, Money.vnd(-1), Transaction.Status.PENDING, null, null, "", null, null));
    }

    @Test
    void masksAccountNumber() {
        Account account = new Account("acc-1", "0011004567890", BankId.ofBin("970436"), "cif-1", "CA01",
                Account.AccountType.CURRENT, Money.VND, Account.AccountStatus.ACTIVE, LocalDate.of(2020, 1, 1));
        assertEquals("*********7890", account.maskedNumber());
    }
}
