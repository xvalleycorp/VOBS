package io.vobs.switching;

import io.vobs.model.BankId;
import io.vobs.model.Money;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chuyển mạch giả lập trong bộ nhớ, dùng cho sandbox và kiểm thử. Không kết nối mạng.
 *
 * <p>Quy tắc giả lập: tài khoản nhận phải có trong danh bạ ({@code AC03} nếu không), số tiền không vượt hạn mức
 * mỗi giao dịch ({@code AM02}), gửi lại cùng mã tham chiếu với nội dung khác bị từ chối ({@code AM05}). Lệnh hợp
 * lệ được quyết toán ngay.
 */
public final class SimulatorSwitch implements SwitchAdapter {

    private record AccountKey(String bin, String accountNumber) {
    }

    private record Submission(TransferRequest request, TransferResult result) {
    }

    private final String id;
    private final Money perTransactionLimit;
    private final Clock clock;
    private final Map<AccountKey, String> directory = new ConcurrentHashMap<>();
    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public SimulatorSwitch(String id, Money perTransactionLimit, Clock clock) {
        this.id = id;
        this.perTransactionLimit = perTransactionLimit;
        this.clock = clock;
    }

    public SimulatorSwitch addAccount(BankId bank, String accountNumber, String holderName) {
        directory.put(new AccountKey(bank.bin(), accountNumber), holderName);
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<String> inquireAccountName(BankId bank, String accountNumber) {
        return Optional.ofNullable(directory.get(new AccountKey(bank.bin(), accountNumber)));
    }

    @Override
    public TransferResult submit(TransferRequest request) {
        Submission submission = submissions.computeIfAbsent(request.reference(),
                reference -> new Submission(request, process(request)));
        return submission.request().equals(request) ? submission.result() : reject(request, "AM05");
    }

    @Override
    public Optional<TransferResult> status(String reference) {
        return Optional.ofNullable(submissions.get(reference)).map(Submission::result);
    }

    private TransferResult process(TransferRequest request) {
        if (inquireAccountName(request.creditorBank(), request.creditorAccount()).isEmpty()) {
            return reject(request, "AC03");
        }
        if (request.amount().compareTo(perTransactionLimit) > 0) {
            return reject(request, "AM02");
        }
        String switchReference = "%s-%010d".formatted(id.toUpperCase(), sequence.incrementAndGet());
        return new TransferResult(request.reference(), switchReference, TransferResult.Status.SETTLED, null,
                clock.instant());
    }

    private TransferResult reject(TransferRequest request, String reasonCode) {
        return new TransferResult(request.reference(), null, TransferResult.Status.REJECTED, reasonCode,
                clock.instant());
    }
}
