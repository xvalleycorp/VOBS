# vobs-iso20022

Mở rộng [Prowide ISO 20022](https://github.com/prowide/prowide-iso20022) (Apache 2.0) cho chuyển khoản VND nội địa.

```java
MxPacs00800108 mx = Pacs008.build(transfer);           // DomesticCreditTransfer -> pacs.008.001.08
List<String> violations = VnPaymentRules.validate(mx); // quy tắc Việt Nam, báo đủ lỗi trong một lần
DomesticCreditTransfer back = Pacs008.read(mx.message());
```

| Lớp | Vai trò |
| --- | --- |
| `DomesticCreditTransfer` | Lệnh chuyển VND giữa hai ngân hàng, dạng trung lập |
| `Pacs008` | Dựng và đọc pacs.008.001.08: tên bỏ dấu, viết hoa; ngân hàng định danh bằng mã BIN |
| `VnPaymentRules` | VND không có phần lẻ, `NbOfTxs` khớp số giao dịch, tên và nội dung chỉ dùng ký tự Latin cơ bản |

Ngân hàng được định danh qua `ClrSysMmbId` với mã hệ thống proprietary `VNBIN`. Đây là quy ước tạm của VOBS;
adapter của NAPAS hoặc MDP sẽ đổi theo đặc tả kết nối chính thức.
