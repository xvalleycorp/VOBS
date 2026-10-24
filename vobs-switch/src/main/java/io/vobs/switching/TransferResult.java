package io.vobs.switching;

import java.time.Instant;
import java.util.Objects;

/**
 * Kết quả xử lý một lệnh chuyển khoản.
 *
 * @param reference mã tham chiếu của ngân hàng gửi
 * @param switchReference mã giao dịch do chuyển mạch cấp, {@code null} nếu bị từ chối trước khi vào chuyển mạch
 * @param status trạng thái
 * @param reasonCode mã lý do theo bộ ExternalStatusReason1Code của ISO 20022 (ví dụ {@code AC03}),
 *     {@code null} nếu thành công
 * @param at thời điểm ghi nhận trạng thái
 */
public record TransferResult(
        String reference,
        String switchReference,
        Status status,
        String reasonCode,
        Instant at) {

    public enum Status {
        /** Chuyển mạch đã nhận, chưa quyết toán. */
        ACCEPTED,
        /** Đã ghi có cho người nhận. */
        SETTLED,
        /** Bị từ chối; xem {@code reasonCode}. */
        REJECTED,
        /** Quá thời gian chờ, chưa rõ kết quả; cần tra soát trước khi thử lại. */
        UNKNOWN
    }

    public TransferResult {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(at, "at");
        if (status == Status.REJECTED && reasonCode == null) {
            throw new IllegalArgumentException("Giao dịch bị từ chối phải có mã lý do");
        }
    }

    public boolean isFinal() {
        return status == Status.SETTLED || status == Status.REJECTED;
    }
}
