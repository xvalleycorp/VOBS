# Mô hình dữ liệu (`vobs-model`)

Mô hình dữ liệu chuẩn của VOBS, bám theo service domain của BIAN. Các module khác trao đổi dữ liệu qua những
kiểu này thay vì dùng cấu trúc bảng của core.

| Kiểu | BIAN service domain | Ghi chú |
| --- | --- | --- |
| `Customer` | Party Reference Data Directory | Cá nhân hoặc tổ chức |
| `Account` | Current Account, Savings Account | `maskedNumber()` che số tài khoản |
| `Balance` | Current Account | Số dư khả dụng và sổ sách, kèm thời điểm |
| `Transaction` | Current Account | Số tiền luôn dương, chiều tiền ở `direction` |
| `Product` | Product Directory | |

Kiểu đặc thù Việt Nam:

| Kiểu | Vai trò |
| --- | --- |
| `Money` | Số tiền theo ISO 4217; VND không có phần lẻ |
| `CitizenId` | Số định danh 12 chữ số trên CCCD gắn chip: mã tỉnh, giới tính, năm sinh; `toString()` tự che số |
| `BankId` | Mã BIN 6 chữ số và mã BIC |
| `VietnameseText` | Bỏ dấu (kể cả `đ`), viết hoa cho chuyển mạch, so khớp tên không phân biệt dấu |
