# Sandbox cho bên thứ ba (`vobs-sandbox`)

Sandbox cho đối tác: API giả lập dựng từ [bộ đặc tả](../vobs-spec/openapi) bằng [Microcks](https://microcks.io)
(Apache 2.0). Fintech dùng sandbox để viết và kiểm thử tích hợp trước khi có ngân hàng thật.

## Chạy cục bộ

```bash
cd vobs-sandbox
docker compose up -d
./import-specs.sh
curl -H 'x-consent-id: 6f1c2a7e-3b0d-4f5e-9a8c-1d2e3f4a5b6c' \
  http://localhost:8585/rest/VOBS+Open+API/0.2.0-draft/accounts/acc-1001
```

Phản hồi giả lập lấy từ các `examples` trong đặc tả; Microcks ghép request với phản hồi theo tên example
(ví dụ `acc-1001`). Thêm tình huống mới bằng cách thêm example cùng tên ở cả request và response.

## Kiểm thử hợp đồng API

Microcks cũng chạy được ngược lại: gọi vào API thật của một ngân hàng và đối chiếu với đặc tả. Đây là nền cho
bộ kiểm thử tuân thủ trong [`vobs-spec/conformance`](../vobs-spec/conformance).

## Kế hoạch

- Dữ liệu mẫu tiếng Việt phong phú hơn (nhiều ngân hàng, nhiều loại tài khoản, tình huống lỗi).
- Nối `SimulatorSwitch` của `vobs-switch` để thử luồng chuyển khoản đầu-cuối.
- Sandbox đa ngân hàng dạng dịch vụ cho fintech (xem mô hình kinh doanh trong [GOVERNANCE.md](../GOVERNANCE.md)).
