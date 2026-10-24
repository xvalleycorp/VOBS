# API Gateway (`vobs-gateway`)

Plugin và cấu hình mẫu cho Apache APISIX, lớp API Gateway của VOBS (giai đoạn 2).

| Thành phần | Trạng thái |
| --- | --- |
| [`apisix/plugins/vobs-consent.lua`](apisix/plugins/vobs-consent.lua) — kiểm tra consent trước mỗi request, fail closed | Bản đầu, đã kiểm cú pháp, chưa chạy thử trên APISIX |
| [`apisix/apisix.yaml`](apisix/apisix.yaml) — route mẫu: openid-connect → limit-count → vobs-consent → upstream, log kiểm toán qua kafka-logger; route `/consents` chuyển tới service consent | Ví dụ |
| Plugin kiểm tra token gắn chứng thư mTLS (RFC 8705) cho FAPI 2.0 | Chưa làm |
| WAF: Coraza + OWASP Core Rule Set | Chưa làm |

## Cách plugin `vobs-consent` hoạt động

1. `openid-connect` xác thực access token do Keycloak cấp; payload token nằm ở `ctx.external_user`.
2. `limit-count` giới hạn tần suất theo bên thứ ba (Điều 11 Thông tư 64), khóa bằng biến `$vobs_client_id`.
3. `vobs-consent` đọc `x-consent-id`, client id (claim `azp`) và mã tài khoản trên đường dẫn, rồi gọi
   `POST /internal/consent-checks` của service consent
   ([đặc tả](../vobs-spec/openapi/internal-consent-check.yaml)).
4. Từ chối với `403` và mã lý do nếu consent không cho phép; `503` nếu không gọi được service consent.
5. Đặt biến `$vobs_consent_id`; cùng `$vobs_client_id`, để `kafka-logger` ghi vào log kiểm toán.

## Cài plugin

Thêm thư mục `apisix/plugins` vào `extra_lua_path` và khai báo plugin trong `config.yaml` của APISIX:

```yaml
apisix:
  extra_lua_path: "/opt/vobs/?.lua"
plugins:
  - openid-connect
  - vobs-consent
  - kafka-logger
```

## Việc cần làm trước khi dùng thật

- Chạy thử plugin với APISIX bằng bộ test của APISIX (`t/` dùng Test::Nginx).
- Service consent (`vobs-consent`) đã có API HTTP nhưng lưu trữ còn trong bộ nhớ; cần PostgreSQL trước khi dùng thật.
- Route `consents` chưa chạy thử: cần xác nhận `proxy-rewrite` đọc được biến `$vobs_client_id` do plugin
  `vobs-consent` đăng ký.
- Upstream `vobs-read-api` trong route mẫu là API đọc của Integration Hub, chưa có.
