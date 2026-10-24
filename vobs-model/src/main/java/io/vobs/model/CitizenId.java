package io.vobs.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Số định danh cá nhân 12 chữ số, in trên thẻ căn cước (CCCD gắn chip).
 *
 * <p>Cấu trúc: 3 chữ số mã tỉnh nơi đăng ký khai sinh, 1 chữ số mã giới tính và thế kỷ sinh, 2 chữ số cuối của
 * năm sinh, 6 chữ số ngẫu nhiên. Mã giới tính và thế kỷ: 0/1 = nam/nữ sinh thế kỷ 20, 2/3 = thế kỷ 21, 4/5 = thế kỷ
 * 22, 6/7 = thế kỷ 23, 8/9 = thế kỷ 24.
 *
 * <p>Lớp này chỉ kiểm tra cấu trúc, không xác thực số với cơ sở dữ liệu quốc gia về dân cư.
 * {@link #toString()} che bớt số để tránh lộ dữ liệu cá nhân trong log.
 */
public record CitizenId(String value) {

    private static final Pattern TWELVE_DIGITS = Pattern.compile("\\d{12}");
    private static final int MAX_PROVINCE_CODE = 96;

    public enum Gender { MALE, FEMALE }

    public CitizenId {
        Objects.requireNonNull(value, "value");
        if (!TWELVE_DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException("Số định danh phải gồm đúng 12 chữ số");
        }
        int province = Integer.parseInt(value.substring(0, 3));
        if (province < 1 || province > MAX_PROVINCE_CODE) {
            throw new IllegalArgumentException("Mã tỉnh không hợp lệ: " + value.substring(0, 3));
        }
    }

    public static boolean isValid(String value) {
        try {
            new CitizenId(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    public String provinceCode() {
        return value.substring(0, 3);
    }

    public Gender gender() {
        return genderCenturyDigit() % 2 == 0 ? Gender.MALE : Gender.FEMALE;
    }

    public int birthYear() {
        int century = 1900 + (genderCenturyDigit() / 2) * 100;
        return century + Integer.parseInt(value.substring(4, 6));
    }

    private int genderCenturyDigit() {
        return value.charAt(3) - '0';
    }

    /** Hiển thị dạng che: {@code 001*******89}. */
    public String masked() {
        return value.substring(0, 3) + "*".repeat(7) + value.substring(10);
    }

    @Override
    public String toString() {
        return masked();
    }
}
