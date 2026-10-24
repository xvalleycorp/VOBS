# Bộ kiểm thử tuân thủ (conformance suite)

Bộ kiểm thử mở, ai cũng chạy được, để kiểm một triển khai Open API có đúng [đặc tả](../openapi) hay không.
Ngân hàng chạy trong CI của mình; cơ quan quản lý và fintech dùng để đối chiếu.

## Phạm vi dự kiến

| Nhóm | Kiểm tra |
| --- | --- |
| Hợp đồng API | Mọi phản hồi khớp schema; mã lỗi theo RFC 9457 (Microcks contract test) |
| Bảo mật | Từ chối khi thiếu mTLS, token không gắn chứng thư, token hết hạn, sai scope |
| Consent | Consent chờ duyệt, bị thu hồi, hết hạn đều bị từ chối; truy cập ngoài tài khoản được chọn bị từ chối |
| Thanh toán | `Idempotency-Key` gửi lại trả cùng kết quả; số tiền VND có phần lẻ bị từ chối |
| Vận hành | `x-fapi-interaction-id` được trả lại; độ trễ đọc trong ngưỡng cam kết |

Bộ kiểm thử sẽ được viết sau khi danh mục API theo Thông tư 64 được chốt.
