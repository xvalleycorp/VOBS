# vobs-consent

Quản lý consent theo Nghị định 13/2023/NĐ-CP và Thông tư 64/2024/TT-NHNN: cấp, xem, thu hồi theo phạm vi dữ
liệu và thời hạn; mọi lần đổi trạng thái đều ghi log kiểm toán không sửa được.

```
AWAITING_AUTHORISATION --authorise--> AUTHORISED --revoke--> REVOKED
         |                                 |
         +--reject--> REJECTED             +--(hết hạn)--> EXPIRED
```

| Lớp | Vai trò |
| --- | --- |
| `ConsentService` | Vòng đời consent và `check()` cho gateway |
| `Consent`, `ConsentScope` | Consent và phạm vi theo nhóm Open API tại Điều 6 Thông tư 64 |
| `ThirdParty` | Bên thứ ba và loại tổ chức; nhóm thanh toán chỉ cho ngân hàng, trung gian thanh toán (Điều 5) |
| `AuditLog` | Log nối tiếp, mỗi bản ghi chứa hash SHA-256 của bản ghi trước; `verify()` phát hiện sửa, xóa, chèn |
| `ConsentRepository` | Giao diện lưu trữ; bản trong bộ nhớ cho kiểm thử |

Quy tắc chính:

- Khách hàng chỉ thấy và thao tác consent của mình; consent của người khác trả về "không tìm thấy".
- Phạm vi thanh toán và ví điện tử chỉ cấp cho ngân hàng và tổ chức trung gian thanh toán (Điều 5 Thông tư 64).
- Thời hạn consent mặc định tối đa 180 ngày (`TT64_MAX_VALIDITY`, Điều 11).
- Consent đọc dữ liệu phải chọn ít nhất một tài khoản; consent chỉ gồm phạm vi thanh toán thì không bắt buộc.
- Consent tự chuyển sang `EXPIRED` khi quá hạn, và việc chuyển này cũng được ghi log.

Giai đoạn 2: lớp HTTP cho API đối tác và API nội bộ, lưu trữ PostgreSQL sao chép đồng bộ, ghi log kiểm toán vào
Kafka, kiểm tra quyền chi tiết bằng OpenFGA, luồng duyệt consent trên Temporal.
