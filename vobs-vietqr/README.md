# Thư viện VietQR (`vobs-vietqr`)

Tạo và đọc mã VietQR cho chuyển khoản nhanh liên ngân hàng (chuẩn EMVCo QR). Không phụ thuộc thư viện ngoài.

```java
// Tạo mã: chuyển 150.000 VND tới tài khoản 0123456789 tại ngân hàng có BIN 970436
String payload = VietQr.toAccount("970436", "0123456789")
        .withAmount(150_000)
        .withPurpose("Thanh toan don 1234")
        .encode();

// Đọc mã: kiểm tra CRC, GUID, loại tiền, mã quốc gia
VietQr qr = VietQr.parse(payload);
qr.bankBin();      // "970436"
qr.amountVnd();    // Optional[150000]
```

| Lớp | Vai trò |
| --- | --- |
| `VietQr` | Dựng và đọc mã VietQR; kiểm tra BIN, số tài khoản, số tiền, nội dung chuyển khoản |
| `Tlv` | Mã hóa, giải mã dữ liệu ID–độ dài–giá trị của EMVCo |
| `Crc16` | CRC-16/CCITT-FALSE dùng ở trường 63 |

Nội dung chuyển khoản phải là ASCII không dấu, tối đa 25 ký tự; bỏ dấu tiếng Việt bằng
`VietnameseText.removeDiacritics` của `vobs-model`.

Chưa hỗ trợ: mã QR của merchant (trường 26–51 khác GUID NAPAS), dữ liệu bổ sung ngoài trường 62.08, trường 64.
