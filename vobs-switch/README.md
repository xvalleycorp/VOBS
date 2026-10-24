# Kết nối chuyển mạch (`vobs-switch`)

Giao diện adapter chung tới các hệ thống chuyển mạch. Thêm một chuyển mạch mới chỉ cần viết một adapter.

| Lớp | Vai trò |
| --- | --- |
| `SwitchAdapter` | SPI: tra tên chủ tài khoản, gửi lệnh, tra trạng thái |
| `SwitchRegistry` | Tra adapter theo id; nạp adapter qua `ServiceLoader` |
| `SimulatorSwitch` | Chuyển mạch giả lập trong bộ nhớ cho sandbox và kiểm thử |
| `TransferRequest`, `TransferResult` | Lệnh và kết quả; mã lý do theo ExternalStatusReason1Code của ISO 20022 |

Mọi adapter phải idempotent theo mã tham chiếu, trả `UNKNOWN` khi hết thời gian chờ thay vì tự gửi lại, và không
ghi log số tài khoản đầy đủ hay tên khách hàng.

## Adapter NAPAS và MDP

Chưa có. Cần đặc tả kết nối và quy trình tham gia của từng hệ thống (bước tiếp theo trong
[lộ trình](../docs/roadmap.md)). Mỗi adapter sẽ là một gói riêng khai báo trong
`META-INF/services/io.vobs.switching.SwitchAdapter`.
