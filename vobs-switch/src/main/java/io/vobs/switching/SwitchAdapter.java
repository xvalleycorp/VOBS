package io.vobs.switching;

import io.vobs.model.BankId;
import java.util.Optional;

/**
 * Giao diện chung tới một hệ thống chuyển mạch (NAPAS, MDP, ...).
 *
 * <p>Mỗi chuyển mạch có một adapter riêng, đóng gói giao thức, định dạng thông điệp và chứng thư kết nối. Phần còn
 * lại của VOBS chỉ làm việc qua giao diện này, nên thêm một chuyển mạch mới chỉ cần viết một adapter và đăng ký qua
 * {@link java.util.ServiceLoader} (file {@code META-INF/services/io.vobs.switching.SwitchAdapter}).
 *
 * <p>Yêu cầu với mọi adapter:
 * <ul>
 *   <li>{@link #submit} idempotent theo {@link TransferRequest#reference()}: gửi lại cùng lệnh trả về cùng kết quả,
 *       không tạo giao dịch thứ hai.</li>
 *   <li>Hết thời gian chờ thì trả {@link TransferResult.Status#UNKNOWN}, không tự gửi lại.</li>
 *   <li>Không ghi log số tài khoản đầy đủ hoặc tên khách hàng.</li>
 * </ul>
 */
public interface SwitchAdapter {

    /** Mã định danh của chuyển mạch, ví dụ {@code napas} hoặc {@code mdp}. */
    String id();

    /** Tra tên chủ tài khoản thụ hưởng trước khi chuyển. Rỗng nếu không tìm thấy tài khoản. */
    Optional<String> inquireAccountName(BankId bank, String accountNumber);

    /** Gửi lệnh chuyển khoản. */
    TransferResult submit(TransferRequest request);

    /** Tra trạng thái một lệnh đã gửi. Rỗng nếu chuyển mạch không biết mã tham chiếu này. */
    Optional<TransferResult> status(String reference);
}
