package io.vobs.iso20022;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.ActiveCurrencyAndAmount;
import io.vobs.model.BankId;
import io.vobs.model.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class Pacs008Test {

    private static final DomesticCreditTransfer TRANSFER = new DomesticCreditTransfer(
            "MSG20261024000001",
            "E2E20261024000001",
            OffsetDateTime.parse("2026-10-24T10:15:30+07:00"),
            LocalDate.of(2026, 10, 24),
            Money.vnd(2_500_000),
            "Nguyễn Văn An",
            "0011004567890",
            new BankId("970436", "BFTVVNVX"),
            "Trần Thị Bình",
            "113366668888",
            BankId.ofBin("970415"),
            "Thanh toán hóa đơn 1234");

    @Test
    void buildsPacs008WithVietnameseConventions() {
        String xml = Pacs008.build(TRANSFER).message();

        assertTrue(xml.contains("pacs.008.001.08"));
        assertTrue(xml.contains("<Nm>NGUYEN VAN AN</Nm>") || xml.contains(":Nm>NGUYEN VAN AN</"), xml);
        assertTrue(xml.contains("VNBIN"));
        assertTrue(xml.contains("Thanh toan hoa don 1234"));
    }

    @Test
    void roundTripsThroughXml() {
        String xml = Pacs008.build(TRANSFER).message();
        DomesticCreditTransfer read = Pacs008.read(xml);

        assertEquals(TRANSFER.messageId(), read.messageId());
        assertEquals(TRANSFER.endToEndId(), read.endToEndId());
        assertEquals(TRANSFER.amount(), read.amount());
        assertEquals(TRANSFER.settlementDate(), read.settlementDate());
        assertEquals(TRANSFER.debtorBank(), read.debtorBank());
        assertEquals(TRANSFER.creditorBank(), read.creditorBank());
        assertEquals("TRAN THI BINH", read.creditorName());
        assertEquals(TRANSFER.creditorAccount(), read.creditorAccount());
        assertEquals("Thanh toan hoa don 1234", read.remittanceInfo());
        assertEquals(TRANSFER.createdAt().toInstant(), read.createdAt().toInstant());
    }

    @Test
    void builtMessagePassesVietnameseRules() {
        assertEquals(List.of(), VnPaymentRules.validate(Pacs008.build(TRANSFER)));
    }

    @Test
    void reportsEveryViolationAtOnce() {
        MxPacs00800108 mx = Pacs008.build(TRANSFER);
        var tx = mx.getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        tx.setIntrBkSttlmAmt(new ActiveCurrencyAndAmount().setCcy("USD").setValue(new BigDecimal("10.50")));
        tx.getCdtr().setNm("Trần Thị Bình");
        mx.getFIToFICstmrCdtTrf().getGrpHdr().setNbOfTxs("2");

        List<String> violations = VnPaymentRules.validate(mx);

        assertEquals(4, violations.size(), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("NbOfTxs")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("phải là VND")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("phần lẻ")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("Cdtr/Nm")));
    }

    @Test
    void rejectsNonVndTransfer() {
        assertThrows(IllegalArgumentException.class, () -> new DomesticCreditTransfer("m", "e",
                OffsetDateTime.now(), LocalDate.now(), Money.of("10", "USD"), "a", "1", BankId.ofBin("970436"),
                "b", "2", BankId.ofBin("970415"), null));
    }
}
