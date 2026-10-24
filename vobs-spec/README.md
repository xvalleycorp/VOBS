# vobs-spec

Bộ đặc tả Open API dùng chung theo Thông tư 64/2024/TT-NHNN và bộ kiểm thử tuân thủ. Đây là phần giá trị cốt
lõi của VOBS: mọi ngân hàng triển khai cùng một đặc tả, fintech viết tích hợp một lần.

| File | Nội dung | Trạng thái |
| --- | --- | --- |
| [`openapi/vobs-open-api.yaml`](openapi/vobs-open-api.yaml) | Đủ 23 Open API cơ bản theo Điều 6 Thông tư 64, mỗi operation gắn `x-tt64` | Nháp 0.2.0 |
| [`tt64-catalog.json`](tt64-catalog.json) | Danh mục Điều 6 dạng máy đọc được | |
| [`scripts/check_tt64_coverage.py`](scripts/check_tt64_coverage.py) | Kiểm tra đặc tả phủ đủ danh mục; chạy trong CI | |
| [`openapi/internal-consent-check.yaml`](openapi/internal-consent-check.yaml) | API nội bộ của service consent: gateway kiểm tra consent; kênh ngân hàng duyệt, từ chối, thu hồi | Nháp |
| [`conformance/`](conformance) | Bộ kiểm thử tuân thủ | Chưa có |

Danh mục API bám đúng Điều 6 Thông tư 64. Đường dẫn, tên trường và yêu cầu bảo mật là thiết kế của VOBS theo thông
lệ quốc tế (OpenAPI 3.1, FAPI 2.0, RFC 9457), chờ đối chiếu Phụ lục 01 và 02 trước bản 1.0. Xem
[docs/tt64.md](../docs/tt64.md).

## Kiểm tra đặc tả

```bash
npx @redocly/cli@1.25.0 lint vobs-spec/openapi/*.yaml
python3 vobs-spec/scripts/check_tt64_coverage.py
```

CI chạy hai lệnh này trên mọi pull request.

## Quy tắc phiên bản

- Đặc tả có phiên bản riêng theo SemVer, tách khỏi phiên bản code.
- Thay đổi phá vỡ tương thích chỉ ở bản major, có thời gian song song hai phiên bản.
- Bộ kiểm thử tuân thủ gắn với phiên bản đặc tả, không gắn với code của VOBS, để ngân hàng dùng nền tảng khác
  cũng tự kiểm được.
