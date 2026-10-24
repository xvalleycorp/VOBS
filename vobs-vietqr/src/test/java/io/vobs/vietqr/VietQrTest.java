package io.vobs.vietqr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VietQrTest {

    @Test
    void crcMatchesStandardCheckValue() {
        // Giá trị kiểm tra chuẩn của CRC-16/CCITT-FALSE
        assertEquals(0x29B1, Crc16.compute("123456789"));
    }

    @Test
    void encodesStaticAccountQrFieldByField() {
        String expectedWithoutCrc = "000201"
                + "010211"
                + "3854" + "0010A000000727" + "0124" + "0006999901" + "0110" + "0123456789" + "0208QRIBFTTA"
                + "5303704"
                + "5802VN"
                + "6304";

        String qr = VietQr.toAccount("999901", "0123456789").encode();

        assertEquals(expectedWithoutCrc + Crc16.hex(expectedWithoutCrc), qr);
    }

    @Test
    void roundTripsAmountPurposeAndOneTimeFlag() {
        VietQr original = VietQr.toAccount("999902", "113366668888")
                .withAmount(150_000)
                .withPurpose("Thanh toan don 1234")
                .asOneTime();

        String encoded = original.encode();

        assertTrue(encoded.startsWith("000201010212"));
        assertTrue(encoded.contains("5406150000"));
        assertEquals(original, VietQr.parse(encoded));
    }

    @Test
    void roundTripsCardTransfer() {
        VietQr card = new VietQr("999901", "9999016614528181", VietQr.Service.TO_CARD, null, null, false);
        VietQr parsed = VietQr.parse(card.encode());
        assertEquals(VietQr.Service.TO_CARD, parsed.service());
        assertFalse(parsed.amountVnd().isPresent());
    }

    @Test
    void rejectsTamperedPayload() {
        String qr = VietQr.toAccount("999901", "0123456789").withAmount(10_000).encode();
        String tampered = qr.replace("540510000", "540590000");

        VietQrException error = assertThrows(VietQrException.class, () -> VietQr.parse(tampered));
        assertTrue(error.getMessage().startsWith("Sai CRC"));
    }

    @Test
    void acceptsLowercaseCrc() {
        String qr = VietQr.toAccount("999901", "0123456789").encode();
        String lower = qr.substring(0, qr.length() - 4) + qr.substring(qr.length() - 4).toLowerCase();
        assertEquals(VietQr.toAccount("999901", "0123456789"), VietQr.parse(lower));
    }

    @Test
    void rejectsNonVietQrMerchantInfo() {
        String body = "000201010211"
                + Tlv.encode("38", Tlv.encode("00", "A000000999") + Tlv.encode("01", "x"))
                + "5303704" + "5802VN" + "6304";
        VietQrException error = assertThrows(VietQrException.class, () -> VietQr.parse(body + Crc16.hex(body)));
        assertTrue(error.getMessage().contains("GUID"));
    }

    @Test
    void rejectsTruncatedData() {
        assertThrows(VietQrException.class, () -> Tlv.parse("000201015"));
        assertThrows(VietQrException.class, () -> VietQr.parse("0002"));
    }

    @Test
    void validatesInputs() {
        assertThrows(VietQrException.class, () -> VietQr.toAccount("97043", "0123"));
        assertThrows(VietQrException.class, () -> VietQr.toAccount("999901", "0123-456"));
        assertThrows(VietQrException.class, () -> VietQr.toAccount("999901", "0123").withAmount(0));
        assertThrows(VietQrException.class, () -> VietQr.toAccount("999901", "0123").withPurpose("Chuyển tiền"));
        assertThrows(VietQrException.class,
                () -> VietQr.toAccount("999901", "0123").withPurpose("a".repeat(26)));
    }
}
