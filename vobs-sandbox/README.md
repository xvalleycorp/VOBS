# Sandbox cho bên thứ ba (`vobs-sandbox`)

Sandbox cho đối tác, gồm các phần:

| Phần | Địa chỉ | Dùng để |
| --- | --- | --- |
| API giả lập, dựng từ [bộ đặc tả](../vobs-spec/openapi) bằng [Microcks](https://microcks.io) (Apache 2.0) | `http://localhost:8585` | Viết tích hợp với tài khoản, giao dịch, thanh toán trước khi có ngân hàng thật |
| Service [Quản lý sự đồng ý](../vobs-consent) chạy thật, lưu trong bộ nhớ | đối tác `http://localhost:8090`, nội bộ `http://localhost:8091` | Thử trọn luồng consent: tạo, khách hàng duyệt, kiểm tra quyền, thu hồi |
| [Keycloak](https://www.keycloak.org) (Apache 2.0), realm `open-banking` | `https://localhost:8443` | Cấp mã truy cập gắn chứng thư (mTLS, RFC 8705) cho đối tác mẫu; trang khách hàng đăng nhập và duyệt consent ([`vobs-keycloak`](../vobs-keycloak)) |
| [API Gateway](../vobs-gateway) (Apache APISIX) với plugin kiểm tra consent | `https://localhost:9443/open-banking/v1` | Gọi API như ở môi trường thật: mTLS, mã truy cập gắn chứng thư (FAPI 2.0), kiểm tra consent, giới hạn tần suất |

## Chạy cục bộ

Cần Docker, Docker Compose và OpenSSL. Lần đầu `--build` dựng image service consent và image Keycloak kèm phần mở
rộng của VOBS từ mã nguồn (vài phút).

```bash
cd vobs-sandbox
./pki.sh                   # tạo CA và chứng thư cho sandbox vào pki/ (một lần)
docker compose up -d --build
./import-specs.sh          # nạp đặc tả và dữ liệu mẫu vào Microcks
./try-consent-flow.sh      # chạy thử luồng consent, gọi thẳng service
./try-gateway-flow.sh      # chạy thử luồng qua API Gateway
./try-authorization-flow.sh  # chạy thử luồng khách hàng đăng nhập ngân hàng để duyệt consent
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

Mọi số liệu là giả, không phản ánh ngân hàng nào; ngân hàng mẫu dùng BIN giả `999901`. Gọi API giả lập với một `x-consent-id` bất kỳ; qua API Gateway
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

Mỗi đối tác mẫu có một chứng thư client do CA của sandbox ký (`pki/<client>.crt`, `pki/<client>.key`), như chứng thư
ngân hàng cấp khi ký hợp đồng kết nối (Điều 8 Thông tư 64). Không có client secret:

1. Đối tác kết nối Keycloak bằng mTLS và lấy mã truy cập (client credentials). Keycloak nhận diện đối tác qua chứng
   thư, rồi gắn token với chứng thư đó: claim `cnf.x5t#S256` là SHA-256 của chứng thư (RFC 8705). Token có hiệu lực
   10 phút, `aud` là `vobs-gateway`.
2. Đối tác gọi gateway bằng mTLS với **cùng chứng thư**, kèm `Authorization: Bearer <token>`. Gateway kiểm tra:
   chứng thư client do CA tin cậy ký; chữ ký, issuer và `aud` của token; token gắn với đúng chứng thư của kết nối
   (plugin `vobs-fapi`). Token bị lộ không dùng được từ máy không có khóa riêng của đối tác.

| Đối tác (client id) | Chứng thư | Subject |
| --- | --- | --- |
| `fintech-a` | `pki/fintech-a.crt` | `CN=fintech-a, O=VOBS Sandbox` |
| `wallet-b` | `pki/wallet-b.crt` | `CN=wallet-b, O=VOBS Sandbox` |
| `bank-c` | `pki/bank-c.crt` | `CN=bank-c, O=VOBS Sandbox` |

Khác môi trường thật: CA tự ký của sandbox, Keycloak chạy chế độ dev (dữ liệu mất khi dừng), khóa riêng để mở quyền
đọc. Trang quản trị Keycloak tại `https://localhost:8443/admin`, tài khoản `admin`/`admin`.

| Đường dẫn | Gateway làm gì |
| --- | --- |
| `/open-banking/v1/consents`, `/consents/{id}` | Ghi đè `x-vobs-client-id` bằng đối tác đã xác thực, chuyển tới service Quản lý sự đồng ý |
| `/open-banking/v1/accounts/{accountId}` | Kiểm tra consent có phạm vi `ACCOUNTS_READ` và tài khoản này, rồi trả dữ liệu giả lập |
| `/open-banking/v1/accounts/{accountId}/transactions` | Như trên, với phạm vi `TRANSACTIONS_READ` |

Mỗi đối tác được gọi tối đa 30 lần mỗi phút trên mỗi đường dẫn; vượt hạn mức nhận `429`.

```bash
# Kết nối bằng chứng thư của fintech-a
TLS=(--cacert pki/ca.crt --cert pki/fintech-a.crt --key pki/fintech-a.key)
# Lấy mã truy cập gắn chứng thư
TOKEN=$(curl -s "${TLS[@]}" -d grant_type=client_credentials -d client_id=fintech-a \
  https://localhost:8443/realms/open-banking/protocol/openid-connect/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
# Tạo consent, rồi cho khách hàng duyệt qua cổng nội bộ (đóng vai kênh ngân hàng)
curl -s "${TLS[@]}" https://localhost:9443/open-banking/v1/consents -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"scopes":["ACCOUNTS_READ"],"validityDays":90}'
curl -s localhost:8091/internal/consents/<consentId>/authorisation -H 'Content-Type: application/json' \
  -d '{"customerId":"cif-1001","accountIds":["acc-1001"]}'
# Đọc tài khoản qua gateway
curl -s "${TLS[@]}" https://localhost:9443/open-banking/v1/accounts/acc-1001 -H "Authorization: Bearer $TOKEN" \
  -H 'x-consent-id: <consentId>'
```

[`try-gateway-flow.sh`](try-gateway-flow.sh) đi qua 14 bước, gồm cả kết nối không có chứng thư, token bị lộ dùng từ
chứng thư khác, token giả (ghép payload của đối tác khác) và trường hợp đối tác tự gửi `x-vobs-client-id`,
`X-Userinfo` để giả danh đối tác khác.

## Khách hàng duyệt consent

Luồng như ở môi trường thật theo FAPI 2.0: khách hàng đăng nhập ngân hàng để duyệt consent, không qua cổng nội bộ.

1. Đối tác tạo consent qua gateway bằng mã truy cập client credentials.
2. Đối tác gửi Pushed Authorization Request (PAR) tới Keycloak qua mTLS, kèm PKCE S256 và `consent_id`, nhận
   `request_uri`. Thiếu PKCE, hay gửi tham số thẳng tới trang ủy quyền thay vì qua PAR, đều bị từ chối.
3. Đối tác chuyển khách hàng tới
   `https://localhost:8443/realms/open-banking/protocol/openid-connect/auth?client_id=<client>&request_uri=<request_uri>`.
   Khách hàng mẫu: `cif-2001`, mật khẩu `sandbox`, có tài khoản `acc-1001` và `acc-2002`.
4. Trang duyệt hiện tên đối tác, phạm vi dữ liệu, thời hạn và tài khoản để chọn. Khách hàng đồng ý thì Keycloak chuyển
   về `https://<client>.example/callback` kèm `code`, `state`, `iss`; từ chối thì kèm `error=access_denied`.
5. Đối tác đổi `code` lấy mã truy cập qua mTLS, kèm `code_verifier`. Token có claim `consent_id` và gắn với chứng
   thư của đối tác.
6. Đối tác gọi gateway bằng token này, không cần `x-consent-id`. Gửi kèm `x-consent-id` khác consent trong token thì
   bị từ chối (`consent_mismatch`).

`redirect_uri` của đối tác mẫu là tên miền không có thật: mở trên trình duyệt, trang cuối báo lỗi kết nối, còn `code`
nằm trên thanh địa chỉ. [`try-authorization-flow.sh`](try-authorization-flow.sh) đóng vai cả đối tác và trình duyệt của
khách hàng, đi qua 14 bước, gồm:

- PAR thiếu PKCE;
- dùng lại authorization code;
- đọc tài khoản khách hàng không chọn;
- làm mới token bằng refresh token;
- khách hàng từ chối;
- đối tác dùng consent của đối tác khác trong PAR của mình.

## Kiểm thử hợp đồng API

Microcks cũng chạy được ngược lại: gọi vào API thật của một ngân hàng và đối chiếu với đặc tả. Đây là nền cho
bộ kiểm thử tuân thủ trong [`vobs-spec/conformance`](../vobs-spec/conformance).

## Kế hoạch

- Dữ liệu cho luồng khởi tạo thanh toán và nạp ví (request `POST` chọn phản hồi theo nội dung, ví dụ vượt hạn mức).
- Nối `SimulatorSwitch` của `vobs-switch` để thử luồng chuyển khoản đầu-cuối.
- Sandbox đa ngân hàng dạng dịch vụ cho fintech (xem mô hình kinh doanh trong [GOVERNANCE.md](../GOVERNANCE.md)).
