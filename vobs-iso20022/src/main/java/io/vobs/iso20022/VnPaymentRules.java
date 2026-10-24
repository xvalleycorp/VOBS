package io.vobs.iso20022;

import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Các quy tắc nghiệp vụ Việt Nam kiểm tra trên pacs.008, bổ sung cho kiểm tra schema của ISO 20022.
 *
 * <p>Trả về danh sách vi phạm thay vì ném ngoại lệ, để gateway và sandbox báo đủ lỗi trong một lần.
 */
public final class VnPaymentRules {

    private static final Pattern BASIC_LATIN = Pattern.compile("[\\x20-\\x7E]*");
    private static final int MAX_ID_LENGTH = 35;
    private static final int MAX_NAME_LENGTH = 140;
    private static final int MAX_REMITTANCE_LENGTH = 140;

    private VnPaymentRules() {
    }

    public static List<String> validate(MxPacs00800108 mx) {
        List<String> violations = new ArrayList<>();
        FIToFICustomerCreditTransferV08 body = mx.getFIToFICstmrCdtTrf();
        if (body == null || body.getGrpHdr() == null) {
            violations.add("Thiếu GrpHdr");
            return violations;
        }
        checkLength(violations, "GrpHdr/MsgId", body.getGrpHdr().getMsgId(), MAX_ID_LENGTH);
        String declared = body.getGrpHdr().getNbOfTxs();
        if (!String.valueOf(body.getCdtTrfTxInf().size()).equals(declared)) {
            violations.add("GrpHdr/NbOfTxs = %s nhưng có %d giao dịch".formatted(declared,
                    body.getCdtTrfTxInf().size()));
        }

        for (int i = 0; i < body.getCdtTrfTxInf().size(); i++) {
            CreditTransferTransaction39 tx = body.getCdtTrfTxInf().get(i);
            String path = "CdtTrfTxInf[%d]".formatted(i);
            checkAmount(violations, path, tx);
            checkLength(violations, path + "/PmtId/EndToEndId",
                    tx.getPmtId() == null ? null : tx.getPmtId().getEndToEndId(), MAX_ID_LENGTH);
            checkName(violations, path + "/Dbtr/Nm", tx.getDbtr() == null ? null : tx.getDbtr().getNm());
            checkName(violations, path + "/Cdtr/Nm", tx.getCdtr() == null ? null : tx.getCdtr().getNm());
            if (tx.getRmtInf() != null) {
                for (String line : tx.getRmtInf().getUstrd()) {
                    checkLength(violations, path + "/RmtInf/Ustrd", line, MAX_REMITTANCE_LENGTH);
                    checkLatin(violations, path + "/RmtInf/Ustrd", line);
                }
            }
        }
        return violations;
    }

    private static void checkAmount(List<String> violations, String path, CreditTransferTransaction39 tx) {
        BigDecimal amount = Pacs008.amountOf(tx);
        if (amount == null) {
            violations.add(path + "/IntrBkSttlmAmt bị thiếu");
            return;
        }
        if (!"VND".equals(tx.getIntrBkSttlmAmt().getCcy())) {
            violations.add(path + "/IntrBkSttlmAmt phải là VND");
        }
        if (amount.signum() <= 0) {
            violations.add(path + "/IntrBkSttlmAmt phải dương");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            violations.add(path + "/IntrBkSttlmAmt: VND không có phần lẻ");
        }
    }

    private static void checkName(List<String> violations, String path, String name) {
        checkLength(violations, path, name, MAX_NAME_LENGTH);
        checkLatin(violations, path, name);
    }

    private static void checkLength(List<String> violations, String path, String value, int max) {
        if (value == null || value.isBlank()) {
            violations.add(path + " bị thiếu");
        } else if (value.length() > max) {
            violations.add("%s dài quá %d ký tự".formatted(path, max));
        }
    }

    private static void checkLatin(List<String> violations, String path, String value) {
        if (value != null && !BASIC_LATIN.matcher(value).matches()) {
            violations.add(path + " chứa ký tự ngoài bộ Latin cơ bản (cần bỏ dấu tiếng Việt)");
        }
    }
}
