# vobs-consent

Quản lý consent theo Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 và Thông tư 64/2024/TT-NHNN: cấp, xem, thu hồi theo phạm vi dữ
liệu và thời hạn; mọi lần đổi trạng thái đều ghi log kiểm toán không sửa được.

```
AWAITING_AUTHORISATION --authorise--> AUTHORISED --revoke--> REVOKED
         |                                 |
         +--reject--> REJECTED             +--(hết hạn)--> EXPIRED
```

| Lớp | Vai trò |
| --- | --- |
| `ConsentService` | Vòng đời consent và `check()` cho gateway |
| `Consent`, `ConsentScope` | Consent và phạm vi theo nhóm Open API tại Điều 6 Thông tư 64 |
| `ThirdParty` | Bên thứ ba và loại tổ chức; nhóm thanh toán chỉ cho ngân hàng, trung gian thanh toán (Điều 5) |
| `AuditLog` | Log nối tiếp, mỗi bản ghi chứa hash SHA-256 của bản ghi trước; `verify()` phát hiện sửa, xóa, chèn |
| `ConsentRepository` | Giao diện lưu trữ; bản trong bộ nhớ cho kiểm thử |
| `http.ConsentHttpServer` | API HTTP trên máy chủ HTTP có sẵn của JDK, hai cổng tách biệt cho đối tác và nội bộ |
| `http.ThirdPartyDirectory` | Danh sách bên thứ ba và loại tổ chức, tra theo mã client |
| `http.ConsentServerMain` | Chạy service với lưu trữ trong bộ nhớ, cho sandbox |

Quy tắc chính:

- Khách hàng chỉ thấy và thao tác consent của mình; consent của người khác trả về "không tìm thấy".
- Phạm vi thanh toán và ví điện tử chỉ cấp cho ngân hàng và tổ chức trung gian thanh toán (Điều 5 Thông tư 64).
- Thời hạn consent mặc định tối đa 180 ngày (`TT64_MAX_VALIDITY`, Điều 11).
- Consent đọc dữ liệu phải chọn ít nhất một tài khoản; consent chỉ gồm phạm vi thanh toán thì không bắt buộc.
- Consent tự chuyển sang `EXPIRED` khi quá hạn, và việc chuyển này cũng được ghi log.
- Đối tác tạo consent khi chưa biết khách hàng là ai; khách hàng được gắn vào consent khi duyệt hoặc từ chối trên
  kênh của ngân hàng.

## API HTTP

Hai cổng tách biệt, để API nội bộ không bị lộ ra ngoài dù route ở gateway cấu hình nhầm.

| Cổng | Endpoint | Ai gọi | Đặc tả |
| --- | --- | --- | --- |
| Đối tác (mặc định 8080) | `POST /consents`, `GET /consents/{id}`, `DELETE /consents/{id}` | Đối tác, qua API Gateway | [`vobs-open-api.yaml`](../vobs-spec/openapi/vobs-open-api.yaml) |
| Nội bộ (mặc định 8081) | `POST /internal/consent-checks` | API Gateway, trước mỗi request của đối tác | [`internal-consent-check.yaml`](../vobs-spec/openapi/internal-consent-check.yaml) |
| Nội bộ | `POST /internal/consents/{id}/authorisation`, `/rejection`, `/revocation` | Kênh của ngân hàng, sau khi xác thực khách hàng | như trên |

- Cổng đối tác lấy danh tính đối tác từ header `x-vobs-client-id`. Gateway đặt header này từ mã truy cập đã xác thực
  và ghi đè giá trị đối tác tự gửi ([route mẫu](../vobs-gateway/apisix/apisix.yaml)). Thiếu header trả `401`, đối tác
  chưa đăng ký trả `403 client_not_registered`.
- Đối tác chỉ thấy consent của mình và không thấy mã khách hàng nội bộ của ngân hàng.
- Lỗi trả theo RFC 9457 (`application/problem+json`) kèm mã lý do, ví dụ `scope_not_permitted`,
  `consent_state_conflict`. Header `x-fapi-interaction-id` được trả lại, hoặc tạo mới nếu đối tác không gửi.
- Máy chủ nghe HTTP thường; TLS và mTLS do sidecar hoặc service mesh đảm nhận. Chỉ mở trong mạng nội bộ của cụm.

Chạy thử với lưu trữ trong bộ nhớ:

```bash
mvn -pl vobs-consent -am package -DskipTests
VOBS_CONSENT_THIRD_PARTIES="fintech-a:OTHER,wallet-b:PAYMENT_INTERMEDIARY" \
  java -cp "vobs-consent/target/classes:$(mvn -q -pl vobs-consent dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
  io.vobs.consent.http.ConsentServerMain

curl -s localhost:8080/consents -H 'x-vobs-client-id: fintech-a' \
  -d '{"scopes":["ACCOUNTS_READ"],"validityDays":90}'
```

Việc tiếp theo của giai đoạn 2: lưu trữ PostgreSQL sao chép đồng bộ, ghi log kiểm toán vào Kafka, API cho khách
hàng xem danh sách consent của mình (Điều 12), kiểm tra quyền chi tiết bằng OpenFGA, luồng duyệt consent trên
Temporal.
