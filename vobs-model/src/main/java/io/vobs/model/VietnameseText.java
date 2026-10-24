package io.vobs.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Xử lý chuỗi tiếng Việt cho các hệ thống chỉ nhận ký tự Latin cơ bản.
 *
 * <p>Hệ thống chuyển mạch và thông điệp thanh toán thường yêu cầu tên người thụ hưởng viết hoa, không dấu.
 * Lớp này bỏ dấu (kể cả {@code đ/Đ}, vốn không tách được bằng Unicode NFD), gộp khoảng trắng và so khớp tên
 * không phân biệt dấu, hoa thường.
 */
public final class VietnameseText {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private VietnameseText() {
    }

    /** Bỏ dấu tiếng Việt, giữ nguyên hoa thường: {@code "Nguyễn Đức Anh" -> "Nguyen Duc Anh"}. */
    public static String removeDiacritics(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    /** Dạng dùng cho chuyển mạch: không dấu, viết hoa, một khoảng trắng giữa các từ. */
    public static String toSwitchName(String text) {
        String plain = removeDiacritics(text).toUpperCase(Locale.ROOT).strip();
        return WHITESPACE.matcher(plain).replaceAll(" ");
    }

    /** So khớp hai tên, không phân biệt dấu, hoa thường và khoảng trắng thừa. */
    public static boolean namesMatch(String a, String b) {
        return toSwitchName(a).equals(toSwitchName(b));
    }
}
