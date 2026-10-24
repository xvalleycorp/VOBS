package io.vobs.vietqr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mã hóa và giải mã dữ liệu dạng ID–độ dài–giá trị của EMVCo QR: ID 2 chữ số, độ dài 2 chữ số, giá trị tối đa 99
 * ký tự.
 */
public final class Tlv {

    private static final Pattern TWO_DIGITS = Pattern.compile("\\d{2}");

    /** Một trường dữ liệu. */
    public record DataObject(String id, String value) {

        public DataObject {
            if (id == null || !TWO_DIGITS.matcher(id).matches()) {
                throw new VietQrException("ID trường phải gồm 2 chữ số: " + id);
            }
            if (value == null || value.isEmpty() || value.length() > 99) {
                throw new VietQrException("Giá trị trường %s phải dài 1–99 ký tự".formatted(id));
            }
        }

        public String encode() {
            return id + "%02d".formatted(value.length()) + value;
        }
    }

    private Tlv() {
    }

    public static String encode(String id, String value) {
        return new DataObject(id, value).encode();
    }

    public static List<DataObject> parse(String data) {
        List<DataObject> objects = new ArrayList<>();
        int pos = 0;
        while (pos < data.length()) {
            if (pos + 4 > data.length()) {
                throw new VietQrException("Dữ liệu bị cắt cụt tại vị trí " + pos);
            }
            String id = data.substring(pos, pos + 2);
            String lengthText = data.substring(pos + 2, pos + 4);
            if (!TWO_DIGITS.matcher(id).matches() || !TWO_DIGITS.matcher(lengthText).matches()) {
                throw new VietQrException("ID hoặc độ dài không hợp lệ tại vị trí " + pos);
            }
            int length = Integer.parseInt(lengthText);
            int end = pos + 4 + length;
            if (end > data.length()) {
                throw new VietQrException("Trường %s dài hơn phần dữ liệu còn lại".formatted(id));
            }
            objects.add(new DataObject(id, data.substring(pos + 4, end)));
            pos = end;
        }
        return objects;
    }

    /** Giải mã thành map theo ID; báo lỗi nếu một ID xuất hiện hai lần. */
    public static Map<String, String> parseToMap(String data) {
        Map<String, String> map = new LinkedHashMap<>();
        for (DataObject object : parse(data)) {
            if (map.putIfAbsent(object.id(), object.value()) != null) {
                throw new VietQrException("Trường %s bị lặp".formatted(object.id()));
            }
        }
        return map;
    }
}
