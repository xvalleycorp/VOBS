package io.vobs.iso20022;

import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.AccountIdentification4Choice;
import com.prowidesoftware.swift.model.mx.dic.ActiveCurrencyAndAmount;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CashAccount38;
import com.prowidesoftware.swift.model.mx.dic.ChargeBearerType1Code;
import com.prowidesoftware.swift.model.mx.dic.ClearingSystemIdentification2Choice;
import com.prowidesoftware.swift.model.mx.dic.ClearingSystemMemberIdentification2;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GenericAccountIdentification1;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import com.prowidesoftware.swift.model.mx.dic.PaymentIdentification7;
import com.prowidesoftware.swift.model.mx.dic.RemittanceInformation16;
import com.prowidesoftware.swift.model.mx.dic.SettlementInstruction7;
import com.prowidesoftware.swift.model.mx.dic.SettlementMethod1Code;
import io.vobs.model.BankId;
import io.vobs.model.Money;
import io.vobs.model.VietnameseText;
import java.math.BigDecimal;

/**
 * Dựng và đọc thông điệp pacs.008.001.08 (FI to FI Customer Credit Transfer) cho chuyển khoản VND nội địa.
 *
 * <p>Ngân hàng được định danh bằng mã BIN trong {@code ClrSysMmbId}, với mã hệ thống thanh toán bù trừ dạng
 * proprietary {@value #CLEARING_SYSTEM}. Đây là quy ước tạm của VOBS cho tới khi có đặc tả chính thức của từng
 * chuyển mạch; adapter của NAPAS hoặc MDP có thể đổi lại. Tên người chuyển và người nhận được bỏ dấu, viết hoa để
 * nằm trong bộ ký tự Latin của ISO 20022.
 */
public final class Pacs008 {

    public static final String CLEARING_SYSTEM = "VNBIN";

    private Pacs008() {
    }

    public static MxPacs00800108 build(DomesticCreditTransfer transfer) {
        GroupHeader93 header = new GroupHeader93()
                .setMsgId(transfer.messageId())
                .setCreDtTm(transfer.createdAt())
                .setNbOfTxs("1")
                .setSttlmInf(new SettlementInstruction7().setSttlmMtd(SettlementMethod1Code.CLRG));

        CreditTransferTransaction39 tx = new CreditTransferTransaction39()
                .setPmtId(new PaymentIdentification7()
                        .setEndToEndId(transfer.endToEndId())
                        .setTxId(transfer.endToEndId()))
                .setIntrBkSttlmAmt(new ActiveCurrencyAndAmount()
                        .setCcy(transfer.amount().currency().getCurrencyCode())
                        .setValue(transfer.amount().amount()))
                .setIntrBkSttlmDt(transfer.settlementDate())
                .setChrgBr(ChargeBearerType1Code.SLEV)
                .setDbtr(party(transfer.debtorName()))
                .setDbtrAcct(account(transfer.debtorAccount()))
                .setDbtrAgt(agent(transfer.debtorBank()))
                .setCdtrAgt(agent(transfer.creditorBank()))
                .setCdtr(party(transfer.creditorName()))
                .setCdtrAcct(account(transfer.creditorAccount()));
        if (transfer.remittanceInfo() != null) {
            tx.setRmtInf(new RemittanceInformation16()
                    .addUstrd(VietnameseText.removeDiacritics(transfer.remittanceInfo())));
        }

        MxPacs00800108 mx = new MxPacs00800108();
        mx.setFIToFICstmrCdtTrf(new FIToFICustomerCreditTransferV08().setGrpHdr(header).addCdtTrfTxInf(tx));
        return mx;
    }

    /** Đọc lại một pacs.008 có đúng một giao dịch, dựng theo quy ước của lớp này. */
    public static DomesticCreditTransfer read(MxPacs00800108 mx) {
        FIToFICustomerCreditTransferV08 body = mx.getFIToFICstmrCdtTrf();
        if (body == null || body.getCdtTrfTxInf().size() != 1) {
            throw new IllegalArgumentException("Chỉ hỗ trợ pacs.008 có đúng một giao dịch");
        }
        GroupHeader93 header = body.getGrpHdr();
        CreditTransferTransaction39 tx = body.getCdtTrfTxInf().getFirst();
        ActiveCurrencyAndAmount amount = tx.getIntrBkSttlmAmt();
        RemittanceInformation16 rmt = tx.getRmtInf();

        return new DomesticCreditTransfer(
                header.getMsgId(),
                tx.getPmtId().getEndToEndId(),
                header.getCreDtTm(),
                tx.getIntrBkSttlmDt(),
                Money.of(amount.getValue().toPlainString(), amount.getCcy()),
                tx.getDbtr().getNm(),
                tx.getDbtrAcct().getId().getOthr().getId(),
                bank(tx.getDbtrAgt()),
                tx.getCdtr().getNm(),
                tx.getCdtrAcct().getId().getOthr().getId(),
                bank(tx.getCdtrAgt()),
                rmt == null || rmt.getUstrd().isEmpty() ? null : String.join(" ", rmt.getUstrd()));
    }

    public static DomesticCreditTransfer read(String xml) {
        MxPacs00800108 mx = MxPacs00800108.parse(xml);
        if (mx == null) {
            throw new IllegalArgumentException("Không đọc được pacs.008.001.08");
        }
        return read(mx);
    }

    static BigDecimal amountOf(CreditTransferTransaction39 tx) {
        return tx.getIntrBkSttlmAmt() == null ? null : tx.getIntrBkSttlmAmt().getValue();
    }

    private static PartyIdentification135 party(String name) {
        return new PartyIdentification135().setNm(VietnameseText.toSwitchName(name));
    }

    private static CashAccount38 account(String number) {
        return new CashAccount38().setId(new AccountIdentification4Choice()
                .setOthr(new GenericAccountIdentification1().setId(number)));
    }

    private static BranchAndFinancialInstitutionIdentification6 agent(BankId bank) {
        FinancialInstitutionIdentification18 id = new FinancialInstitutionIdentification18()
                .setClrSysMmbId(new ClearingSystemMemberIdentification2()
                        .setClrSysId(new ClearingSystemIdentification2Choice().setPrtry(CLEARING_SYSTEM))
                        .setMmbId(bank.bin()));
        bank.bicCode().ifPresent(id::setBICFI);
        return new BranchAndFinancialInstitutionIdentification6().setFinInstnId(id);
    }

    private static BankId bank(BranchAndFinancialInstitutionIdentification6 agent) {
        FinancialInstitutionIdentification18 id = agent.getFinInstnId();
        return new BankId(id.getClrSysMmbId().getMmbId(), id.getBICFI());
    }
}
