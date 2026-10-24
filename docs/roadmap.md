# Lộ trình

Mốc quan trọng nhất là một ngân hàng thí điểm chạy production, dự kiến quanh tháng 18. Mốc bên ngoài quyết định
nhu cầu: ngân hàng đã kết nối với bên thứ ba phải tuân thủ đầy đủ Thông tư 64 **trước 01/03/2027**
([đối chiếu](tt64.md)). Chỉ sang giai đoạn tiếp theo khi đã qua cổng tương ứng. Thời gian là ước tính.

| Giai đoạn | Thời gian | Nội dung | Cổng để đi tiếp |
| --- | --- | --- | --- |
| 1. Nền móng | Tháng 0–6 | Thư viện VietQR, ISO 20022; mô hình dữ liệu chuẩn; đặc tả API Thông tư 64; sandbox cho fintech | Ít nhất 3 fintech dùng sandbox |
| 2. Thí điểm | Tháng 6–18 | API Gateway, FAPI, consent; Integration Hub; 1–2 connector core; connector NAPAS, MDP; 1 ngân hàng thí điểm | Ngân hàng thí điểm lên production |
| 3. Mở rộng | Từ tháng 18 | Fraud và AML; bản doanh nghiệp; hỗ trợ SLA, gói tuân thủ; cân nhắc chuyển foundation | |

Giai đoạn 1 phát hành các thư viện nhỏ, hữu ích ngay, để kéo developer và fintech về trước khi có ngân hàng.

## Bước tiếp theo

- [ ] Hoàn thành kiểm tra tên: tên miền (vobs.vn, vobs.dev, vobs.io), namespace Maven Central và npm scope
      `@vobs`, tra cứu nhãn hiệu "VOBS" tại Cục Sở hữu trí tuệ (nhóm 9, 36, 42)
- [x] Lập danh mục Open API theo Điều 6 Thông tư 64 và đưa vào `vobs-spec` ([đối chiếu](tt64.md))
- [ ] Lấy toàn văn Phụ lục 01, 02 của Thông tư 64; đối chiếu đường dẫn, trường dữ liệu, yêu cầu an toàn thông tin
- [ ] Tìm hiểu đặc tả kết nối và quy trình tham gia của NAPAS và MDP
- [ ] Phát hành bản đầu của `vobs-vietqr` và `vobs-iso20022` lên Maven Central
- [ ] Tiếp cận 2–3 fintech và 1 ngân hàng vừa để xác nhận nhu cầu và mục tiêu phi chức năng
- [ ] Tìm hiểu nhóm đứng sau org `OpenBankingVN` (repo `OBVN-API`, `BRIDGE-API`): đối thủ hay đối tác

## Rủi ro chính

| Rủi ro | Mức độ | Giảm thiểu |
| --- | --- | --- |
| Không có ngân hàng thí điểm | Cao | Vào qua yêu cầu tuân thủ Thông tư 64; thí điểm miễn phí kèm hỗ trợ; bắt đầu từ ngân hàng vừa và nhỏ |
| Khó lấy CDC từ core cũ | Cao | Bắt đầu từ một loại core; dự phòng đọc qua API hoặc file của core |
| Lỗ hổng trong dependency | Cao | SBOM, quét tự động, chính sách công bố lỗ hổng, bản vá có SLA |
| Chuẩn Thông tư 64 thay đổi | Trung bình | Đặc tả có phiên bản; bộ kiểm thử tách khỏi code |
| Vendor core không hợp tác | Trung bình | Connector đọc ở tầng database, không sửa core; làm việc qua SI |
| Thiếu người đóng góp | Trung bình | Phát hành thư viện nhỏ trước; tài liệu tốt; issue cho người mới |
| Dependency đổi giấy phép | Trung bình | Ưu tiên dự án thuộc foundation; có sẵn phương án thay thế |
| Đối thủ thương mại hạ giá | Trung bình | Cạnh tranh bằng tính mở, connector bản địa và bộ kiểm thử tuân thủ |
