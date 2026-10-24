# vobs-spec

Bộ đặc tả Open API dùng chung theo Thông tư 64/2024/TT-NHNN và bộ kiểm thử tuân thủ. Đây là phần giá trị cốt
lõi của VOBS: mọi ngân hàng triển khai cùng một đặc tả, fintech viết tích hợp một lần.

| File | Nội dung | Trạng thái |
| --- | --- | --- |
| [`openapi/vobs-open-api.yaml`](openapi/vobs-open-api.yaml) | API cho đối tác: consent, tài khoản, số dư, giao dịch, thanh toán | Nháp 0.1.0 |
| [`openapi/internal-consent-check.yaml`](openapi/internal-consent-check.yaml) | API nội bộ gateway → service consent | Nháp 0.1.0 |
| [`conformance/`](conformance) | Bộ kiểm thử tuân thủ | Chưa có |

Bản nháp dựng theo kiến trúc VOBS và thông lệ Open Banking quốc tế (OpenAPI 3.1, FAPI 2.0, RFC 9457). Danh mục
nhóm API, tên trường và yêu cầu bảo mật sẽ được đối chiếu với nội dung Thông tư 64 trước bản 1.0.

## Kiểm tra đặc tả

```bash
npx @redocly/cli@1.25.0 lint vobs-spec/openapi/*.yaml
```

CI chạy lệnh này trên mọi pull request.

## Quy tắc phiên bản

- Đặc tả có phiên bản riêng theo SemVer, tách khỏi phiên bản code.
- Thay đổi phá vỡ tương thích chỉ ở bản major, có thời gian song song hai phiên bản.
- Bộ kiểm thử tuân thủ gắn với phiên bản đặc tả, không gắn với code của VOBS, để ngân hàng dùng nền tảng khác
  cũng tự kiểm được.
