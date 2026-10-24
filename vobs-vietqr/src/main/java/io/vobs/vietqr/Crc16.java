package io.vobs.vietqr;

import java.nio.charset.StandardCharsets;

/**
 * CRC-16/CCITT-FALSE (đa thức 0x1021, giá trị đầu 0xFFFF), thuật toán checksum của EMVCo QR.
 */
public final class Crc16 {

    private Crc16() {
    }

    public static int compute(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
        }
        return crc & 0xFFFF;
    }

    /** CRC dạng 4 ký tự hex viết hoa, như trong trường 63 của mã QR. */
    public static String hex(String data) {
        return "%04X".formatted(compute(data));
    }
}
