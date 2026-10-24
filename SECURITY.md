# Chính sách bảo mật

## Báo lỗ hổng

Không báo lỗ hổng qua issue, discussion hay pull request công khai.

Gửi báo cáo riêng qua GitHub:
[Report a vulnerability](https://github.com/xvalleycorp/vobs/security/advisories/new).
Báo cáo nên có module và phiên bản bị ảnh hưởng, cách tái hiện, và mức ảnh hưởng bạn đánh giá.

## Cam kết xử lý

| Bước | Thời hạn |
| --- | --- |
| Xác nhận đã nhận báo cáo | 3 ngày làm việc |
| Đánh giá mức độ và phản hồi kế hoạch | 10 ngày làm việc |
| Phát hành bản vá với lỗ hổng nghiêm trọng | Trong vòng 30 ngày, hoặc theo thỏa thuận công bố |

Sau khi có bản vá, dự án công bố GitHub Security Advisory kèm mã CVE nếu phù hợp và ghi nhận người báo cáo
(nếu họ đồng ý). Ngân hàng có hợp đồng hỗ trợ được thông báo trước khi công bố công khai.

## Phiên bản được hỗ trợ

Dự án đang ở giai đoạn trước 1.0; chỉ bản mới nhất trên nhánh `main` nhận bản vá bảo mật.

## Chuỗi cung ứng

Bản phát hành được ký bằng Sigstore cosign và kèm SBOM (SPDX, tạo bằng Syft). Mọi dependency được quét lỗ hổng
bằng Trivy trong CI.
