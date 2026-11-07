# Quản lý sự đồng ý (`vobs-consent`)

Quản lý sự đồng ý (consent) của khách hàng theo Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 và Thông tư 64/2024/TT-NHNN: cấp, xem, thu hồi theo phạm vi dữ
liệu và thời hạn; mọi lần đổi trạng thái đều ghi log kiểm toán không sửa được.

```
AWAITING_AUTHORISATION --authorise--> AUTHORISED --revoke--> REVOKED
         |                                 |
         +--reject--> REJECTED             +--(hết hạn)--> EXPIRED
```

| Lớp | Vai trò |
| --- | --- |
| `ConsentService` | Vòng đời consent và `check()` cho gateway |
| `ConsentPolicy` | Xét yêu cầu consent mới theo bảng quyết định RuleStone `consent_request` (mục dưới) |
| `Consent`, `ConsentScope` | Consent và phạm vi theo nhóm Open API tại Điều 6 Thông tư 64 |
| `ThirdParty` | Bên thứ ba và loại tổ chức |
| `AuditLog` | Log nối tiếp, mỗi bản ghi chứa hash SHA-256 của bản ghi trước; `verify()` phát hiện sửa, xóa, chèn |
| `ConsentRepository` | Giao diện lưu trữ; bản trong bộ nhớ cho kiểm thử |
| `jdbc.PostgresConsentRepository`, `jdbc.PostgresAuditStore` | Lưu consent và log kiểm toán vào PostgreSQL; `jdbc.ConsentSchema` tạo bảng |
| `http.ConsentHttpServer` | API HTTP trên máy chủ HTTP có sẵn của JDK, hai cổng tách biệt cho đối tác và nội bộ |
| `http.ThirdPartyDirectory` | Danh sách bên thứ ba và loại tổ chức, tra theo mã client |
| `http.ConsentServerMain` | Chạy service, lưu trữ PostgreSQL hoặc trong bộ nhớ |

Quy tắc chính:

- Khách hàng chỉ thấy và thao tác consent của mình; consent của người khác trả về "không tìm thấy".
- Phạm vi thanh toán và ví điện tử chỉ cấp cho ngân hàng và tổ chức trung gian thanh toán (Điều 5 Thông tư 64).
- Thời hạn consent mặc định tối đa 180 ngày (Điều 11).

Hai quy tắc trên nằm trong bảng quyết định, không nằm trong mã Java (mục [Chính sách nhận consent](#chính-sách-nhận-consent)).
- Consent đọc dữ liệu phải chọn ít nhất một tài khoản; consent chỉ gồm phạm vi thanh toán thì không bắt buộc.
- Consent tự chuyển sang `EXPIRED` khi quá hạn, và việc chuyển này cũng được ghi log.
- Đối tác tạo consent khi chưa biết khách hàng là ai; khách hàng được gắn vào consent khi duyệt hoặc từ chối trên
  kênh của ngân hàng.

## Chính sách nhận consent

Yêu cầu consent mới được xét theo bảng quyết định `consent_request` của
[RuleStone](https://github.com/xvalleycorp/RuleStone) (Apache-2.0), bộ máy quy tắc nghiệp vụ cho ngân hàng:
[`consent_request.v1.stone.json`](src/main/resources/io/vobs/consent/rules/consent_request.v1.stone.json).

| Quy tắc | Khi nào | Kết quả |
| --- | --- | --- |
| `VALIDITY` | `validityDays` ngoài khoảng 1–180 (Điều 11) | `400 invalid_request` |
| `PAYMENT_GROUP` | Bên thứ ba không phải ngân hàng hay trung gian thanh toán mà xin phạm vi nhóm thanh toán (Điều 5) | `403 scope_not_permitted` |
| `ALLOW` | Còn lại | Tạo consent |

- **Có phiên bản và ngày hiệu lực.** Bảng v1 có hiệu lực từ 01/03/2025, ngày Thông tư 64 có hiệu lực. Khi quy định
  đổi, hoặc ngân hàng có thỏa thuận khác với đối tác, thêm file phiên bản mới với `effectiveFrom` là ngày áp dụng;
  không sửa file cũ. Phiên bản cũ tự hết hiệu lực khi phiên bản mới bắt đầu. Phiên bản được chọn theo ngày nghiệp vụ
  (giờ Việt Nam) của yêu cầu.
- **Quy tắc riêng của ngân hàng.** Quy tắc mới đặt `allowed` là `false` kèm `reasonCode`; đối tác nhận `403` với
  `reason` là mã đó viết thường, ví dụ `TRANSACTIONS_WITHOUT_ACCOUNTS` thành `transactions_without_accounts`.
- **Dùng bảng riêng** bằng biến môi trường `VOBS_CONSENT_RULES_DIR`: thư mục chứa mọi phiên bản bảng
  (`*.stone.json`), kể cả phiên bản cũ. Bảng được kiểm tra kiểu khi khởi động: tên trường sai hay sai kiểu thì
  service dừng, không để lỗi xảy ra lúc đối tác gọi.
- **Kiểm toán được.** Mỗi consent lưu phiên bản bảng đã nhận nó (`policyVersion`, dạng `1@sha256:…`, hash nội dung
  bảng), thấy ở API nội bộ. Mỗi lần xét, kể cả bị từ chối, ghi một bản ghi quyết định ra log của service
  (`vobs-consent: decision {...}`): phiên bản bảng, quy tắc khớp, mã lý do, mã consent, hash của input. Bản ghi
  không chứa giá trị input.

Sửa bất kỳ chữ nào trong một file bảng, kể cả mô tả, đều đổi hash của phiên bản đó. Vì thêm phiên bản mới không
phải sửa file cũ, các file phiên bản chỉ được thêm, không bị sửa: hash lưu trong consent cũ luôn khớp với file đang
chạy. Chỉ đặt `effectiveTo` khi chính sách hết hiệu lực mà không có phiên bản thay thế.

## API HTTP

Hai cổng tách biệt, để API nội bộ không bị lộ ra ngoài dù route ở gateway cấu hình nhầm.

| Cổng | Endpoint | Ai gọi | Đặc tả |
| --- | --- | --- | --- |
| Đối tác (mặc định 8080) | `POST /consents`, `GET /consents/{id}`, `DELETE /consents/{id}` | Đối tác, qua API Gateway | [`vobs-open-api.yaml`](../vobs-spec/openapi/vobs-open-api.yaml) |
| Nội bộ (mặc định 8081) | `POST /internal/consent-checks` | API Gateway, trước mỗi request của đối tác | [`internal-consent-check.yaml`](../vobs-spec/openapi/internal-consent-check.yaml) |
| Nội bộ | `GET /internal/customers/{customerId}/consents` (Điều 12) | Kênh của ngân hàng, sau khi xác thực khách hàng | như trên |
| Nội bộ | `GET /internal/consents/{id}` | Trang duyệt consent ([`vobs-keycloak`](../vobs-keycloak)), trước khi khách hàng quyết định | như trên |
| Nội bộ | `POST /internal/consents/{id}/authorisation`, `/rejection`, `/revocation` | Kênh của ngân hàng, sau khi xác thực khách hàng | như trên |

- Cổng đối tác lấy danh tính đối tác từ header `x-vobs-client-id`. Gateway đặt header này từ mã truy cập đã xác thực
  và ghi đè giá trị đối tác tự gửi ([route mẫu](../vobs-gateway/apisix/apisix.yaml)). Thiếu header trả `401`, đối tác
  chưa đăng ký trả `403 client_not_registered`.
- Đối tác chỉ thấy consent của mình và không thấy mã khách hàng nội bộ của ngân hàng.
- Lỗi trả theo RFC 9457 (`application/problem+json`) kèm mã lý do, ví dụ `scope_not_permitted`,
  `consent_state_conflict`. Header `x-fapi-interaction-id` được trả lại, hoặc tạo mới nếu đối tác không gửi.
- Máy chủ nghe HTTP thường; TLS và mTLS do sidecar hoặc service mesh đảm nhận. Chỉ mở trong mạng nội bộ của cụm.

## Lưu trữ

Đặt `VOBS_CONSENT_DATABASE_URL` (kèm `VOBS_CONSENT_DATABASE_USER`, `VOBS_CONSENT_DATABASE_PASSWORD`) để lưu vào
PostgreSQL 14 trở lên; bảng được tạo khi khởi động ([`schema.sql`](src/main/resources/io/vobs/consent/jdbc/schema.sql)).
Không đặt thì dữ liệu nằm trong bộ nhớ và mất khi dừng service.

- Bảng `consent_audit_log` chỉ ghi thêm: trigger chặn mọi lệnh sửa và xóa bản ghi. Khi khởi động, service kiểm tra
  lại toàn bộ chuỗi hash và dừng nếu chuỗi bị gãy.
- Mỗi lúc chỉ chạy một bản service trên cùng một cơ sở dữ liệu, vì chuỗi hash được nối tiếp trong tiến trình.
- Kiểm thử trên PostgreSQL thật chạy khi có `VOBS_TEST_DATABASE_URL`, ví dụ
  `jdbc:postgresql://localhost:5432/vobs_test?user=vobs`; CI luôn chạy với một service PostgreSQL.

Chạy thử với lưu trữ trong bộ nhớ:

```bash
mvn -pl vobs-consent -am package -DskipTests
VOBS_CONSENT_THIRD_PARTIES="fintech-a:OTHER,wallet-b:PAYMENT_INTERMEDIARY" \
  java -cp "vobs-consent/target/classes:$(mvn -q -pl vobs-consent dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
  io.vobs.consent.http.ConsentServerMain

curl -s localhost:8080/consents -H 'x-vobs-client-id: fintech-a' \
  -d '{"scopes":["ACCOUNTS_READ"],"validityDays":90}'
```

Chạy bằng Docker (build từ thư mục gốc repo):

```bash
docker build -f vobs-consent/Dockerfile -t vobs-consent .
docker run --rm -p 8080:8080 -p 8081:8081 -e VOBS_CONSENT_THIRD_PARTIES="fintech-a:OTHER" vobs-consent
```

Image chạy bằng user không có quyền root và nghe trên mọi địa chỉ trong container. Sandbox cho bên thứ ba dùng
sẵn image này, kèm script chạy thử trọn luồng ([`vobs-sandbox`](../vobs-sandbox)).

Kiểm thử và build cần RuleStone trong kho Maven cục bộ: chạy `./scripts/install-rulestone.sh` ở thư mục gốc repo
một lần. Image Docker tự cài khi build.

Việc tiếp theo của giai đoạn 2: chạy nhiều bản service cùng lúc (khóa khi ghi log kiểm toán, khóa lạc quan khi
đổi trạng thái consent), connection pool, đẩy log kiểm toán sang Kafka, kiểm tra quyền chi tiết bằng OpenFGA,
luồng duyệt consent trên Temporal.
