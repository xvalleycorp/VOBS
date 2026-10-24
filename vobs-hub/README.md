# vobs-hub

Integration Hub: lấy thay đổi từ database của core bằng Debezium, chuẩn hóa về [mô hình VOBS](../vobs-model) và
nạp vào kho đọc. API đọc (số dư, giao dịch) truy vấn kho đọc này, không gọi core (CQRS). Lệnh ghi không đi qua hub.

```
core DB --(Debezium CDC)--> Kafka --(DebeziumEventParser)--> ChangeEvent --(Projector + RowMapper)--> ReadStore
```

## Có gì

| Lớp | Vai trò |
| --- | --- |
| `DebeziumEventParser` | Đọc envelope Debezium (có hoặc không có schema), bỏ qua tombstone và truncate |
| `RowMapper` | SPI ánh xạ một bảng core sang thực thể VOBS; mỗi connector core cung cấp một bộ |
| `AccountRowMapper` | Mapper tài khoản cấu hình bằng tên cột và bảng mã, không cần viết code riêng |
| `Projector` | Áp sự kiện lên kho đọc; bỏ qua sự kiện cũ hơn dữ liệu đang có khi phát lại |
| `ReadStore` | Giao diện kho đọc; bản trong bộ nhớ cho kiểm thử, bản PostgreSQL + Valkey ở giai đoạn 2 |
| [`connectors/oracle-generic.json`](connectors/oracle-generic.json) | Cấu hình mẫu Debezium Oracle (LogMiner); giá trị `${env:...}` cần `EnvVarConfigProvider` trên Kafka Connect |

## Connector theo core

Cấu trúc dữ liệu của T24, Flexcube, Silverlake khác nhau và cần làm việc với từng ngân hàng thí điểm. Bắt đầu
từ một loại core; khi core không bật được CDC thì dự phòng bằng đọc qua API hoặc file xuất của core.
