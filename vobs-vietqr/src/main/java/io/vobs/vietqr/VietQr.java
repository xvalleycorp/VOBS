package io.vobs.vietqr;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mã VietQR cho chuyển khoản nhanh liên ngân hàng, theo cấu trúc EMVCo QR.
 *
 * <p>Các trường được dùng:
 * <ul>
 *   <li>00 phiên bản ({@code 01}), 01 cách khởi tạo ({@code 11} mã tĩnh, {@code 12} mã dùng một lần)</li>
 *   <li>38 thông tin tài khoản thụ hưởng: 00 GUID {@value #NAPAS_GUID}, 01 gồm mã BIN và số tài khoản hoặc số thẻ,
 *       02 mã dịch vụ</li>
 *   <li>53 loại tiền ({@code 704} = VND), 54 số tiền, 58 mã quốc gia ({@code VN})</li>
 *   <li>62 dữ liệu bổ sung: 08 nội dung chuyển khoản</li>
 *   <li>63 CRC-16/CCITT-FALSE tính trên toàn bộ chuỗi, kể cả {@code 6304}</li>
 * </ul>
 *
 * <p>Nội dung chuyển khoản chỉ nhận ký tự ASCII in được; tiếng Việt cần bỏ dấu trước, ví dụ bằng
 * {@code io.vobs.model.VietnameseText}.
 *
 * @param bankBin mã BIN 6 chữ số của ngân hàng thụ hưởng
 * @param beneficiary số tài khoản hoặc số thẻ thụ hưởng
 * @param service chuyển tới tài khoản hay tới thẻ
 * @param amount số tiền VND, {@code null} nếu người chuyển tự nhập
 * @param purpose nội dung chuyển khoản, {@code null} nếu không có
 * @param oneTime {@code true} nếu mã chỉ dùng một lần (trường 01 = {@code 12})
 */
public record VietQr(
        String bankBin,
        String beneficiary,
        Service service,
        Long amount,
        String purpose,
        boolean oneTime) {

    public static final String NAPAS_GUID = "A000000727";
    static final String CURRENCY_VND = "704";
    static final String COUNTRY_VN = "VN";

    private static final Pattern BIN = Pattern.compile("\\d{6}");
    private static final Pattern BENEFICIARY = Pattern.compile("[0-9A-Za-z]{1,19}");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]+");
    private static final int MAX_PURPOSE_LENGTH = 25;
    private static final long MAX_AMOUNT = 9_999_999_999_999L;

    /** Mã dịch vụ trong trường 38.02. */
    public enum Service {
        TO_ACCOUNT("QRIBFTTA"),
        TO_CARD("QRIBFTTC");

        private final String code;

        Service(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        static Service fromCode(String code) {
            for (Service service : values()) {
                if (service.code.equals(code)) {
                    return service;
                }
            }
            throw new VietQrException("Mã dịch vụ không hỗ trợ: " + code);
        }
    }

    public VietQr {
        Objects.requireNonNull(service, "service");
        if (bankBin == null || !BIN.matcher(bankBin).matches()) {
            throw new VietQrException("Mã BIN phải gồm 6 chữ số: " + bankBin);
        }
        if (beneficiary == null || !BENEFICIARY.matcher(beneficiary).matches()) {
            throw new VietQrException("Số tài khoản hoặc số thẻ phải gồm 1–19 chữ số hoặc chữ cái");
        }
        if (amount != null && (amount <= 0 || amount > MAX_AMOUNT)) {
            throw new VietQrException("Số tiền phải dương và tối đa 13 chữ số: " + amount);
        }
        if (purpose != null) {
            if (purpose.length() > MAX_PURPOSE_LENGTH || !PRINTABLE_ASCII.matcher(purpose).matches()) {
                throw new VietQrException("Nội dung chuyển khoản phải là ASCII không dấu, tối đa 25 ký tự");
            }
        }
    }

    /** Mã tĩnh chuyển tới tài khoản, người chuyển tự nhập số tiền. */
    public static VietQr toAccount(String bankBin, String accountNumber) {
        return new VietQr(bankBin, accountNumber, Service.TO_ACCOUNT, null, null, false);
    }

    public VietQr withAmount(long vnd) {
        return new VietQr(bankBin, beneficiary, service, vnd, purpose, oneTime);
    }

    public VietQr withPurpose(String text) {
        return new VietQr(bankBin, beneficiary, service, amount, text, oneTime);
    }

    public VietQr asOneTime() {
        return new VietQr(bankBin, beneficiary, service, amount, purpose, true);
    }

    public Optional<Long> amountVnd() {
        return Optional.ofNullable(amount);
    }

    public Optional<String> purposeText() {
        return Optional.ofNullable(purpose);
    }

    /** Chuỗi dữ liệu để in thành mã QR. */
    public String encode() {
        String beneficiaryInfo = Tlv.encode("00", bankBin) + Tlv.encode("01", beneficiary);
        String accountInfo = Tlv.encode("00", NAPAS_GUID)
                + Tlv.encode("01", beneficiaryInfo)
                + Tlv.encode("02", service.code());

        StringBuilder qr = new StringBuilder()
                .append(Tlv.encode("00", "01"))
                .append(Tlv.encode("01", oneTime ? "12" : "11"))
                .append(Tlv.encode("38", accountInfo))
                .append(Tlv.encode("53", CURRENCY_VND));
        if (amount != null) {
            qr.append(Tlv.encode("54", Long.toString(amount)));
        }
        qr.append(Tlv.encode("58", COUNTRY_VN));
        if (purpose != null) {
            qr.append(Tlv.encode("62", Tlv.encode("08", purpose)));
        }
        qr.append("6304");
        return qr.append(Crc16.hex(qr.toString())).toString();
    }

    /** Đọc và kiểm tra một chuỗi VietQR, kể cả CRC. */
    public static VietQr parse(String payload) {
        Objects.requireNonNull(payload, "payload");
        verifyCrc(payload);

        List<Tlv.DataObject> objects = Tlv.parse(payload);
        if (!objects.getFirst().id().equals("00") || !objects.getFirst().value().equals("01")) {
            throw new VietQrException("Trường đầu tiên phải là 00 với giá trị 01");
        }
        Map<String, String> root = Tlv.parseToMap(payload);

        String initiation = root.getOrDefault("01", "11");
        if (!initiation.equals("11") && !initiation.equals("12")) {
            throw new VietQrException("Cách khởi tạo không hợp lệ: " + initiation);
        }
        String currency = root.get("53");
        if (currency != null && !currency.equals(CURRENCY_VND)) {
            throw new VietQrException("VietQR chỉ hỗ trợ VND (704), nhận được: " + currency);
        }
        String country = root.get("58");
        if (country != null && !country.equals(COUNTRY_VN)) {
            throw new VietQrException("Mã quốc gia phải là VN, nhận được: " + country);
        }

        Map<String, String> accountInfo = Tlv.parseToMap(required(root, "38", "thông tin tài khoản thụ hưởng"));
        if (!NAPAS_GUID.equals(accountInfo.get("00"))) {
            throw new VietQrException("Không phải mã VietQR: GUID trường 38.00 khác " + NAPAS_GUID);
        }
        Map<String, String> beneficiaryInfo = Tlv.parseToMap(required(accountInfo, "01", "trường 38.01"));
        Service service = Service.fromCode(required(accountInfo, "02", "mã dịch vụ 38.02"));

        Long amount = null;
        if (root.containsKey("54")) {
            try {
                amount = Long.parseLong(root.get("54"));
            } catch (NumberFormatException e) {
                throw new VietQrException("Số tiền VND phải là số nguyên: " + root.get("54"));
            }
        }
        String purpose = root.containsKey("62") ? Tlv.parseToMap(root.get("62")).get("08") : null;

        return new VietQr(
                required(beneficiaryInfo, "00", "mã BIN"),
                required(beneficiaryInfo, "01", "số tài khoản hoặc số thẻ"),
                service,
                amount,
                purpose,
                initiation.equals("12"));
    }

    private static void verifyCrc(String payload) {
        int crcStart = payload.length() - 8;
        if (crcStart < 0 || !payload.startsWith("6304", crcStart)) {
            throw new VietQrException("Trường CRC (63) phải nằm cuối và dài 4 ký tự");
        }
        String expected = Crc16.hex(payload.substring(0, crcStart + 4));
        String actual = payload.substring(crcStart + 4);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new VietQrException("Sai CRC: mong đợi %s, nhận được %s".formatted(expected, actual));
        }
    }

    private static String required(Map<String, String> fields, String id, String label) {
        String value = fields.get(id);
        if (value == null) {
            throw new VietQrException("Thiếu " + label);
        }
        return value;
    }
}
