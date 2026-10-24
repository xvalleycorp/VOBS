# Sandbox cho bên thứ ba (`vobs-sandbox`)

Sandbox cho đối tác, gồm hai phần:

| Phần | Địa chỉ | Dùng để |
| --- | --- | --- |
| API giả lập, dựng từ [bộ đặc tả](../vobs-spec/openapi) bằng [Microcks](https://microcks.io) (Apache 2.0) | `http://localhost:8585` | Viết tích hợp với tài khoản, giao dịch, thanh toán trước khi có ngân hàng thật |
| Service [Quản lý sự đồng ý](../vobs-consent) chạy thật, lưu trong bộ nhớ | đối tác `http://localhost:8090`, nội bộ `http://localhost:8091` | Thử trọn luồng consent: tạo, khách hàng duyệt, kiểm tra quyền, thu hồi |

## Chạy cục bộ

Cần Docker và Docker Compose. Lần đầu `--build` dựng image service consent từ mã nguồn (vài phút).

```bash
cd vobs-sandbox
docker compose up -d --build
./import-specs.sh          # nạp đặc tả vào Microcks
./try-consent-flow.sh      # chạy thử luồng consent, in kết quả từng bước
```

Gọi API giả lập:

```bash
curl -H 'x-consent-id: 6f1c2a7e-3b0d-4f5e-9a8c-1d2e3f4a5b6c' \
  http://localhost:8585/rest/VOBS+Open+API/0.2.0-draft/accounts/acc-1001
```

Phản hồi giả lập lấy từ các `examples` trong đặc tả; Microcks ghép request với phản hồi theo tên example
(ví dụ `acc-1001`). Thêm tình huống mới bằng cách thêm example cùng tên ở cả request và response.

## Luồng consent

[`try-consent-flow.sh`](try-consent-flow.sh) đi qua 10 bước và dừng với mã lỗi nếu kết quả khác mong đợi; CI chạy
script này trên image vừa build. Đối tác mẫu đã đăng ký sẵn:

| Mã client | Loại | Dùng được nhóm thanh toán và ví điện tử |
| --- | --- | --- |
| `fintech-a` | Fintech | Không (Điều 5 Thông tư 64) |
| `wallet-b` | Trung gian thanh toán | Có |
| `bank-c` | Ngân hàng | Có |

Khác với môi trường thật:

- Đối tác tự gửi header `x-vobs-client-id`. Ở môi trường thật, API Gateway xác thực mã truy cập rồi đặt header này.
- Cổng nội bộ (`8091`) đóng vai kênh của ngân hàng: gọi thẳng `POST /internal/consents/{id}/authorisation` thay
  cho bước khách hàng đăng nhập và xác nhận. Ở môi trường thật, cổng này không mở ra ngoài cụm.
- Dữ liệu nằm trong bộ nhớ và mất khi dừng sandbox.

Ví dụ tạo consent bằng tay:

```bash
curl -s localhost:8090/consents -H 'x-vobs-client-id: fintech-a' -H 'Content-Type: application/json' \
  -d '{"scopes":["ACCOUNTS_READ"],"validityDays":90}'
```

## Kiểm thử hợp đồng API

Microcks cũng chạy được ngược lại: gọi vào API thật của một ngân hàng và đối chiếu với đặc tả. Đây là nền cho
bộ kiểm thử tuân thủ trong [`vobs-spec/conformance`](../vobs-spec/conformance).

## Kế hoạch

- Dữ liệu mẫu tiếng Việt phong phú hơn (nhiều ngân hàng, nhiều loại tài khoản, tình huống lỗi).
- Nối `SimulatorSwitch` của `vobs-switch` để thử luồng chuyển khoản đầu-cuối.
- Sandbox đa ngân hàng dạng dịch vụ cho fintech (xem mô hình kinh doanh trong [GOVERNANCE.md](../GOVERNANCE.md)).
