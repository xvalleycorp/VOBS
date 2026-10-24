package io.vobs.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Định danh ngân hàng: mã BIN 6 chữ số dùng trong chuyển mạch và VietQR, kèm mã BIC (SWIFT) nếu có.
 *
 * @param bin mã BIN 6 chữ số
 * @param bic mã BIC 8 hoặc 11 ký tự, có thể {@code null}
 */
public record BankId(String bin, String bic) {

    private static final Pattern BIN = Pattern.compile("\\d{6}");
    private static final Pattern BIC = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");

    public BankId {
        Objects.requireNonNull(bin, "bin");
        if (!BIN.matcher(bin).matches()) {
            throw new IllegalArgumentException("Mã BIN phải gồm 6 chữ số: " + bin);
        }
        if (bic != null && !BIC.matcher(bic).matches()) {
            throw new IllegalArgumentException("Mã BIC không hợp lệ: " + bic);
        }
    }

    public static BankId ofBin(String bin) {
        return new BankId(bin, null);
    }

    public Optional<String> bicCode() {
        return Optional.ofNullable(bic);
    }
}
