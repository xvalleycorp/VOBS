# Đóng góp cho VOBS

Cảm ơn bạn quan tâm. Mọi đóng góp — báo lỗi, tài liệu, code, connector — đều được chào đón.

## Developer Certificate of Origin (DCO)

Mỗi commit phải có dòng `Signed-off-by`, xác nhận bạn có quyền đóng góp phần code đó theo
[DCO 1.1](https://developercertificate.org/):

```bash
git commit -s -m "vietqr: hỗ trợ trường 62.01"
```

Pull request có commit thiếu sign-off sẽ không được merge.

## Quy trình

1. Với thay đổi lớn, mở issue trước để thống nhất hướng làm.
2. Tạo nhánh từ `main`, viết code kèm test.
3. Chạy `mvn verify` (lần đầu chạy `./scripts/install-rulestone.sh` trước; và `npx @redocly/cli@1.25.0 lint vobs-spec/openapi/*.yaml` nếu sửa đặc tả).
4. Mở pull request, mô tả vấn đề và cách giải quyết.

## Quy ước

- Java 21, không thêm framework vào thư viện nền (`vobs-model`, `vobs-vietqr`, `vobs-iso20022`).
- Mỗi module dùng riêng được; tránh phụ thuộc chéo ngoài `vobs-model`.
- Thông báo lỗi và Javadoc viết tiếng Việt; tên lớp, phương thức, biến viết tiếng Anh.
- Không ghi log số tài khoản đầy đủ, số định danh, tên khách hàng.
- Ví dụ, kiểm thử và dữ liệu mẫu không dùng mã của ngân hàng thật: dùng BIN giả `999901` (ngân hàng mẫu A), `999902`
  (ngân hàng mẫu B) và BIC giả `ZZZZVNVX`. Dải `9999xx` nằm ngoài dải BIN `9704xx` của ngân hàng trong nước.
- Dependency mới phải có giấy phép nằm ngoài danh sách cần tránh trong [docs/licensing.md](docs/licensing.md);
  CI chạy `mvn -Plicense-check verify` để kiểm tra.
- Tiền tố commit theo module: `vietqr:`, `consent:`, `spec:`, `docs:`, `ci:`...

## Báo lỗ hổng bảo mật

Không mở issue công khai. Xem [SECURITY.md](SECURITY.md).
