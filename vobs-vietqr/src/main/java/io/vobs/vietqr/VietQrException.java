package io.vobs.vietqr;

/** Mã QR sai cấu trúc, sai CRC hoặc thiếu trường bắt buộc. */
public class VietQrException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public VietQrException(String message) {
        super(message);
    }
}
