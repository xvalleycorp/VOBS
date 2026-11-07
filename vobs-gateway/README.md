# API Gateway (`vobs-gateway`)

Plugin và cấu hình mẫu cho Apache APISIX, lớp API Gateway của VOBS (giai đoạn 2).

| Thành phần | Trạng thái |
| --- | --- |
| [`apisix/plugins/vobs-consent.lua`](apisix/plugins/vobs-consent.lua) — kiểm tra consent trước mỗi request, fail closed | Đã chạy trên APISIX 3.19 với Keycloak 26.8 trong [sandbox](../vobs-sandbox) |
| [`apisix/apisix.yaml`](apisix/apisix.yaml) — route mẫu: openid-connect → limit-count → vobs-consent → upstream, log kiểm toán qua kafka-logger; route `/consents` chuyển tới service consent | Ví dụ |
| [`apisix/plugins/vobs-fapi.lua`](apisix/plugins/vobs-fapi.lua) — token gắn chứng thư mTLS (RFC 8705) cho FAPI 2.0: so claim `cnf.x5t#S256` với chứng thư client của kết nối | Đã chạy trong sandbox |
| [`apisix/plugins/vobs/identity.lua`](apisix/plugins/vobs/identity.lua) — đọc claim của token đã xác thực, dùng chung cho các plugin | Đã chạy trong sandbox |
| WAF: Coraza + OWASP Core Rule Set | Chưa làm |

## Cách plugin `vobs-consent` hoạt động

1. `openid-connect` hỏi Keycloak về access token qua introspection (mục dưới). APISIX 3.19 không đặt claim của token
   vào context của request; với `set_userinfo_header: true`, plugin xóa header `X-Userinfo` do client tự gửi rồi đặt
   lại bằng kết quả introspection. `vobs-consent` chỉ đọc `X-Userinfo` khi route có `openid-connect`, nên **phải bật
   `set_userinfo_header`**. Route dùng plugin xác thực consumer (`key-auth`, `jwt-auth`) thì client id là tên consumer.
2. `limit-count` giới hạn tần suất theo bên thứ ba (Điều 11 Thông tư 64), khóa bằng biến `$vobs_client_id`.
3. `vobs-consent` xác định consent, client id (claim `azp`, hoặc tên consumer) và mã tài khoản trên đường dẫn, rồi
   gọi `POST /internal/consent-checks` của service consent
   ([đặc tả](../vobs-spec/openapi/internal-consent-check.yaml)).
    - **Token do khách hàng duyệt consent.** Consent lấy từ claim `consent_id`, do
      [`vobs-keycloak`](../vobs-keycloak) gắn khi khách hàng duyệt. Header `x-consent-id` gửi kèm phải trùng, nếu
      không thì bị từ chối với `consent_mismatch`.
    - **Token client credentials.** Token không có claim `consent_id`, nên consent lấy từ header `x-consent-id`.
    - **Bắt buộc token của khách hàng.** Đặt `require_bound_consent: true` trên route thì chỉ nhận token do khách hàng
      duyệt consent. Thiếu thì bị từ chối với `customer_authorisation_required`.
4. Từ chối với `403` và mã lý do nếu consent không cho phép; `503` nếu không gọi được service consent.
5. Đặt biến `$vobs_consent_id`; cùng `$vobs_client_id`, để `kafka-logger` ghi vào log kiểm toán.

## Xác thực token bằng introspection

Gateway hỏi Keycloak về token (RFC 7662), không tự kiểm tra chữ ký bằng JWKS. Có hai lý do:

- **Thuật toán ký.** `openid-connect` (lua-resty-openidc 1.9) chỉ xác minh được RS* và HS* từ JWKS, còn FAPI 2.0 yêu
  cầu PS256, ES256 hoặc EdDSA. Keycloak ký token bằng PS256.
- **Thu hồi.** Token đã bị Keycloak thu hồi (authorization code bị dùng lại, phiên bị đăng xuất) bị gateway chặn, không
  phải đợi hết hạn.

Khi cấu hình, cần lưu ý:

- **Luôn đặt `introspection_interval`.** Bỏ trống thì thư viện giữ kết quả tới khi token hết hạn, và token thu hồi vẫn
  dùng được. Đây là độ trễ tối đa để chặn token thu hồi, đổi lại là tải cho Keycloak: cấu hình mẫu đặt 30 giây, sandbox
  đặt 1 giây để kiểm tra.
- **Gateway xác thực bằng `private_key_jwt`.** Client policy FAPI 2.0 áp cho cả lời gọi introspection, nên không dùng
  được client secret. JWT ký ES256, `aud` là đúng issuer của realm. Client `vobs-gateway` khai báo khóa công khai.
- **Keycloak nằm trên đường đi của mọi request.** Không gọi được Keycloak thì token chưa có trong cache bị từ chối với
  `401` (fail closed). Khi định cỡ cần tính tải này: xem [`docs/infrastructure.md`](../docs/infrastructure.md).

## Cài plugin

Thêm thư mục `apisix/plugins` vào `extra_lua_path` và khai báo plugin trong `config.yaml` của APISIX:

```yaml
apisix:
  extra_lua_path: "/opt/vobs/?.lua"
  router:
    http: radixtree_uri_with_parameter   # cần cho route có tham số như /accounts/:account_id
plugins:
  - openid-connect
  - vobs-fapi
  - vobs-consent
  - kafka-logger
```

## Việc cần làm trước khi dùng thật

- Đã kiểm chứng trong sandbox (`vobs-sandbox/try-gateway-flow.sh`, chạy trên CI): `proxy-rewrite` đọc được biến
  `$vobs_client_id` và ghi đè header giả danh; plugin đọc đúng mã tài khoản trên đường dẫn; `limit-count` chặn ở lần
  gọi vượt hạn mức; consent chờ duyệt, sai phạm vi, sai tài khoản, đã thu hồi đều bị từ chối.
- Đã chạy trong sandbox với Keycloak: mTLS tới cả Keycloak và gateway, client xác thực bằng X.509, token gắn chứng
  thư (RFC 8705), token ký PS256 xác thực qua introspection, kiểm tra `aud`. APISIX `openid-connect` không tự kiểm tra `cnf`, nên cần `vobs-fapi`.
- Đã chạy trong sandbox (`vobs-sandbox/try-authorization-flow.sh`):
    - luồng authorization code với PAR, PKCE S256 và mTLS cho khách hàng duyệt consent;
    - client policy FAPI 2.0 của Keycloak;
    - mã truy cập gắn consent.
- Chưa đo tải của introspection lên Keycloak.
- Service Quản lý sự đồng ý đã có API HTTP và lưu trữ PostgreSQL, nhưng mỗi lúc chỉ chạy được một bản.
- Upstream `vobs-read-api` trong route mẫu là API đọc của Integration Hub, chưa có.
