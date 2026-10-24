# API Gateway (`vobs-gateway`)

Plugin và cấu hình mẫu cho Apache APISIX, lớp API Gateway của VOBS (giai đoạn 2).

| Thành phần | Trạng thái |
| --- | --- |
| [`apisix/plugins/vobs-consent.lua`](apisix/plugins/vobs-consent.lua) — kiểm tra consent trước mỗi request, fail closed | Đã chạy trên APISIX 3.19 với Keycloak 26.8 trong [sandbox](../vobs-sandbox) |
| [`apisix/apisix.yaml`](apisix/apisix.yaml) — route mẫu: openid-connect → limit-count → vobs-consent → upstream, log kiểm toán qua kafka-logger; route `/consents` chuyển tới service consent | Ví dụ |
| Plugin kiểm tra token gắn chứng thư mTLS (RFC 8705) cho FAPI 2.0 | Chưa làm |
| WAF: Coraza + OWASP Core Rule Set | Chưa làm |

## Cách plugin `vobs-consent` hoạt động

1. `openid-connect` xác thực access token do Keycloak cấp. APISIX 3.19 không đặt payload token vào context của
   request; với `set_userinfo_header: true`, plugin xóa header `X-Userinfo` do client tự gửi rồi đặt lại bằng payload
   của token đã xác thực. `vobs-consent` chỉ đọc `X-Userinfo` khi route có `openid-connect`, nên **phải bật
   `set_userinfo_header`**. Route dùng plugin xác thực consumer (`key-auth`, `jwt-auth`) thì client id là tên consumer.
2. `limit-count` giới hạn tần suất theo bên thứ ba (Điều 11 Thông tư 64), khóa bằng biến `$vobs_client_id`.
3. `vobs-consent` đọc `x-consent-id`, client id (claim `azp`, hoặc tên consumer) và mã tài khoản trên đường dẫn, rồi gọi
   `POST /internal/consent-checks` của service consent
   ([đặc tả](../vobs-spec/openapi/internal-consent-check.yaml)).
4. Từ chối với `403` và mã lý do nếu consent không cho phép; `503` nếu không gọi được service consent.
5. Đặt biến `$vobs_consent_id`; cùng `$vobs_client_id`, để `kafka-logger` ghi vào log kiểm toán.

## Cài plugin

Thêm thư mục `apisix/plugins` vào `extra_lua_path` và khai báo plugin trong `config.yaml` của APISIX:

```yaml
apisix:
  extra_lua_path: "/opt/vobs/?.lua"
  router:
    http: radixtree_uri_with_parameter   # cần cho route có tham số như /accounts/:account_id
plugins:
  - openid-connect
  - vobs-consent
  - kafka-logger
```

## Việc cần làm trước khi dùng thật

- Đã kiểm chứng trong sandbox (`vobs-sandbox/try-gateway-flow.sh`, chạy trên CI): `proxy-rewrite` đọc được biến
  `$vobs_client_id` và ghi đè header giả danh; plugin đọc đúng mã tài khoản trên đường dẫn; `limit-count` chặn ở lần
  gọi vượt hạn mức; consent chờ duyệt, sai phạm vi, sai tài khoản, đã thu hồi đều bị từ chối.
- Đã chạy với `openid-connect` và Keycloak trong sandbox (client credentials, kiểm tra token bằng JWKS). Chưa có mTLS
  và token gắn chứng thư (RFC 8705), chưa kiểm tra `aud` của token.
- Service Quản lý sự đồng ý đã có API HTTP và lưu trữ PostgreSQL, nhưng mỗi lúc chỉ chạy được một bản.
- Upstream `vobs-read-api` trong route mẫu là API đọc của Integration Hub, chưa có.
