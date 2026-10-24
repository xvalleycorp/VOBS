# Quản trị dự án

Code mở theo Apache 2.0. Doanh thu đến từ trách nhiệm và cam kết dịch vụ, không phải từ bán code.

## Giấy phép

- Toàn bộ module lõi dùng Apache 2.0, có điều khoản về bằng sáng chế và được pháp chế ngân hàng chấp nhận rộng rãi.
- Người đóng góp ký DCO (Developer Certificate of Origin) cho mỗi commit; xem [CONTRIBUTING.md](CONTRIBUTING.md).
- Cam kết công khai không đổi giấy phép của phần lõi.

## Pháp nhân và quản trị

- Một công ty Việt Nam đứng sau dự án, ký hợp đồng và chịu trách nhiệm trước ngân hàng.
- Tên dự án tách khỏi tên công ty, để sau này dễ chuyển sang quản trị trung lập.
- Giai đoạn đầu có hội đồng kỹ thuật với đại diện ngân hàng thí điểm, fintech và SI.
- Khi có từ 3 ngân hàng trở lên sử dụng, cân nhắc chuyển dự án vào FINOS hoặc Linux Foundation.

## Ra quyết định

- Thay đổi thường: pull request được một maintainer duyệt.
- Thay đổi đặc tả API (`vobs-spec`) hoặc kiến trúc: mở issue đề xuất, để ít nhất 7 ngày cho góp ý, hội đồng kỹ
  thuật chốt.
- Thêm dependency mới: phải qua kiểm tra giấy phép tự động trong CI và nằm ngoài danh sách cần tránh
  ([docs/licensing.md](docs/licensing.md)).

## Nguồn doanh thu

| Nguồn | Nội dung | Khách hàng |
| --- | --- | --- |
| Hỗ trợ theo SLA | Cam kết thời gian phản hồi, bản vá bảo mật có hạn, hỗ trợ 24/7 | Ngân hàng |
| Gói tuân thủ | Ánh xạ kiểm soát theo Thông tư 09/2020 và Nghị định 85/2016, kết quả pentest, hồ sơ cấp độ | Ngân hàng |
| Connector core thương mại | Connector T24, Flexcube... cần thỏa thuận với vendor core | Ngân hàng, SI |
| Bản doanh nghiệp | Multi-tenant, admin console nâng cao, báo cáo SLO theo đối tác | Ngân hàng lớn |
| Chương trình đối tác | Đào tạo và chứng nhận SI triển khai | SI |
| Sandbox dạng dịch vụ | Sandbox đa ngân hàng cho fintech | Fintech |
