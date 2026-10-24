# Sandbox cho bên thứ ba (`vobs-sandbox`)

Sandbox cho đối tác, gồm hai phần:

| Phần | Địa chỉ | Dùng để |
| --- | --- | --- |
| API giả lập, dựng từ [bộ đặc tả](../vobs-spec/openapi) bằng [Microcks](https://microcks.io) (Apache 2.0) | `http://localhost:8585` | Viết tích hợp với tài khoản, giao dịch, thanh toán trước khi có ngân hàng thật |
| Service [Quản lý sự đồng ý](../vobs-consent) chạy thật, lưu trong bộ nhớ | đối tác `http://localhost:8090`, nội bộ `http://localhost:8091` | Thử trọn luồng consent: tạo, khách hàng duyệt, kiểm tra quyền, thu hồi |
| [API Gateway](../vobs-gateway) (Apache APISIX) với plugin kiểm tra consent | `http://localhost:9080/open-banking/v1` | Gọi API như ở môi trường thật: xác thực, kiểm tra consent, giới hạn tần suất |

## Chạy cục bộ

Cần Docker và Docker Compose. Lần đầu `--build` dựng image service consent từ mã nguồn (vài phút).

```bash
cd vobs-sandbox
docker compose up -d --build
./import-specs.sh          # nạp đặc tả và dữ liệu mẫu vào Microcks
./try-consent-flow.sh      # chạy thử luồng consent, gọi thẳng service
./try-gateway-flow.sh      # chạy thử luồng qua API Gateway
./try-sandbox-data.sh      # kiểm tra dữ liệu mẫu, trực tiếp và qua API Gateway
```

Gọi API giả lập:

```bash
curl -H 'x-consent-id: 6f1c2a7e-3b0d-4f5e-9a8c-1d2e3f4a5b6c' \
  http://localhost:8585/rest/VOBS+Open+API/0.2.0-draft/accounts/acc-1001
```

Phản hồi giả lập lấy từ hai nguồn: các `examples` trong đặc tả (mỗi API một ví dụ, làm tài liệu) và dữ liệu mẫu
trong [`data/`](data). Microcks chọn phản hồi theo tham số path và query của request; request không khớp dữ liệu nào
nhận `400`.

## Dữ liệu mẫu

Mọi số liệu là giả, không phản ánh ngân hàng nào. Gọi API giả lập với một `x-consent-id` bất kỳ; qua API Gateway
thì cần consent thật (mục dưới).

| Khách hàng | Tài khoản | Loại, tiền tệ | Trạng thái | Lịch sử giao dịch |
| --- | --- | --- | --- | --- |
| `cif-1001` | `acc-1001` | Thanh toán, VND | Hoạt động | 1 giao dịch |
| | `acc-1002` | Tiền gửi có kỳ hạn 12 tháng, VND | Hoạt động | 1 giao dịch gửi tiền |
| | `acc-1003` | Thanh toán ngoại tệ, USD | Hoạt động | 2 giao dịch |
| `cif-2001` | `acc-2001` | Thanh toán, VND | Hoạt động | 7 giao dịch, 2 trang: đang xử lý, đã hạch toán, đã hoàn |
| | `acc-2002` | Thanh toán, VND | Ngủ (`DORMANT`) | Không có |
| | `acc-2009` | Thanh toán, VND | Bị phong tỏa (`BLOCKED`) | 1 giao dịch |
| | `acc-0000` | | Không tồn tại: `404` | `404` |

| API | Dữ liệu mẫu |
| --- | --- |
| Lãi suất | `productType=TERM_DEPOSIT`, `SAVINGS`, `LOAN`; không có tham số thì trả biểu lãi suất trong đặc tả |
| Tỷ giá | `currency=USD`, `EUR`, `JPY` |
| Trạng thái thanh toán | `pay-20261024-0001` đã quyết toán, `-0002` bị từ chối (`AM04`, không đủ số dư), `-0003` chờ khách hàng xác thực (luồng decoupled), `-0004` chưa rõ kết quả; `pay-00000000-0000` không tồn tại |
| Trạng thái nạp ví | `top-20261024-0001` đã quyết toán, `-0002` bị từ chối (`AM02`, vượt hạn mức) |

Lịch sử giao dịch phân trang bằng `cursor`, lấy từ `links.next`:

```bash
curl -H 'x-consent-id: 6f1c2a7e-3b0d-4f5e-9a8c-1d2e3f4a5b6c' \
  'http://localhost:8585/rest/VOBS+Open+API/0.2.0-draft/accounts/acc-2001/transactions?cursor=acc-2001-p2'
```

Các tham số lọc `fromDate`, `toDate`, `limit` được nhận nhưng không lọc dữ liệu giả lập.

Thêm dữ liệu: thêm example vào [`data/vobs-open-api.examples.yaml`](data/vobs-open-api.examples.yaml) với tên khác
các example sẵn có của cùng API, chạy `python3 check-data.py` để đối chiếu với đặc tả (cần `jsonschema`, `PyYAML`;
CI chạy bước này), rồi chạy lại `./import-specs.sh`.

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

## Gọi qua API Gateway

Đối tác mẫu xác thực bằng API key trong header `apikey`. Ở môi trường thật, đối tác dùng mã truy cập FAPI 2.0 do
Keycloak của ngân hàng cấp.

| Đối tác | API key |
| --- | --- |
| `fintech-a` | `sandbox-fintech-a` |
| `wallet-b` | `sandbox-wallet-b` |
| `bank-c` | `sandbox-bank-c` |

| Đường dẫn | Gateway làm gì |
| --- | --- |
| `/open-banking/v1/consents`, `/consents/{id}` | Ghi đè `x-vobs-client-id` bằng đối tác đã xác thực, chuyển tới service Quản lý sự đồng ý |
| `/open-banking/v1/accounts/{accountId}` | Kiểm tra consent có phạm vi `ACCOUNTS_READ` và tài khoản này, rồi trả dữ liệu giả lập |
| `/open-banking/v1/accounts/{accountId}/transactions` | Như trên, với phạm vi `TRANSACTIONS_READ` |

Mỗi đối tác được gọi tối đa 30 lần mỗi phút trên mỗi đường dẫn; vượt hạn mức nhận `429`.

```bash
# Tạo consent, rồi cho khách hàng duyệt qua cổng nội bộ (đóng vai kênh ngân hàng)
curl -s localhost:9080/open-banking/v1/consents -H 'apikey: sandbox-fintech-a' -H 'Content-Type: application/json' \
  -d '{"scopes":["ACCOUNTS_READ"],"validityDays":90}'
curl -s localhost:8091/internal/consents/<consentId>/authorisation -H 'Content-Type: application/json' \
  -d '{"customerId":"cif-1001","accountIds":["acc-1001"]}'
# Đọc tài khoản qua gateway
curl -s localhost:9080/open-banking/v1/accounts/acc-1001 -H 'apikey: sandbox-fintech-a' -H 'x-consent-id: <consentId>'
```

[`try-gateway-flow.sh`](try-gateway-flow.sh) đi qua 11 bước, gồm cả trường hợp đối tác tự gửi `x-vobs-client-id`
để giả danh đối tác khác.

## Kiểm thử hợp đồng API

Microcks cũng chạy được ngược lại: gọi vào API thật của một ngân hàng và đối chiếu với đặc tả. Đây là nền cho
bộ kiểm thử tuân thủ trong [`vobs-spec/conformance`](../vobs-spec/conformance).

## Kế hoạch

- Dữ liệu cho luồng khởi tạo thanh toán và nạp ví (request `POST` chọn phản hồi theo nội dung, ví dụ vượt hạn mức).
- Nối `SimulatorSwitch` của `vobs-switch` để thử luồng chuyển khoản đầu-cuối.
- Sandbox đa ngân hàng dạng dịch vụ cho fintech (xem mô hình kinh doanh trong [GOVERNANCE.md](../GOVERNANCE.md)).
