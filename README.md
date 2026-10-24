# VOBS — Vietnam Open Banking Stack

[![CI](https://github.com/xvalleycorp/vobs/actions/workflows/ci.yml/badge.svg)](https://github.com/xvalleycorp/vobs/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Website: **<https://vobs.dxa.io.vn>** · [Đặc tả API](https://vobs.dxa.io.vn/api/) ·
[Đối chiếu Thông tư 64](https://vobs.dxa.io.vn/thong-tu-64/)

Bộ nền tảng Open Banking mã nguồn mở cho Việt Nam. VOBS giúp ngân hàng tuân thủ Thông tư 64/2024/TT-NHNN về
Open API mà không phải mở thẳng core banking ra ngoài, và giúp fintech kết nối nhiều ngân hàng qua một kiểu API.

- **Là gì:** API Gateway theo chuẩn Thông tư 64, quản lý sự đồng ý (consent) của khách hàng, Integration Hub đồng bộ dữ liệu từ core, và các
  thư viện nền (ISO 20022, VietQR, connector tới các hệ thống chuyển mạch NAPAS và MDP).
- **Cho ai:** ngân hàng cần tuân thủ Open API, fintech cần kết nối nhiều ngân hàng, công ty tích hợp hệ thống (SI).
- **Nguyên tắc:** Việt Nam trước, theo chuẩn quốc tế (OpenAPI 3.1, FAPI 2.0, ISO 20022, BIAN), module dùng riêng
  được, không ghi vào core qua đường tắt, chạy on-premise, kiểm toán được từ đầu.

## Module

| Module | Mã | Chức năng | Giai đoạn | Trạng thái |
| --- | --- | --- | --- | --- |
| [Đặc tả Open API](vobs-spec) | `vobs-spec` | Đặc tả Open API theo Thông tư 64, bộ kiểm thử tuân thủ | 1 | Nháp 0.2: đủ 23 API của Điều 6; chưa đối chiếu Phụ lục 01, 02 |
| [Mô hình dữ liệu](vobs-model) | `vobs-model` | Mô hình dữ liệu chuẩn theo BIAN; CCCD, BIN, VND, tên tiếng Việt | 1 | Dùng được |
| [Thư viện VietQR](vobs-vietqr) | `vobs-vietqr` | Tạo và đọc mã VietQR | 1 | Dùng được |
| [Thư viện ISO 20022](vobs-iso20022) | `vobs-iso20022` | pacs.008 cho chuyển khoản VND nội địa, trên Prowide ISO 20022 | 1 | Dùng được |
| [Kết nối chuyển mạch](vobs-switch) | `vobs-switch` | Adapter chung tới chuyển mạch; bộ giả lập | 1–2 | SPI và giả lập; chưa có NAPAS, MDP |
| [Sandbox cho bên thứ ba](vobs-sandbox) | `vobs-sandbox` | API giả lập bằng Microcks; service Quản lý sự đồng ý và API Gateway chạy thật để thử luồng consent | 1 | Chạy trên máy bằng Docker Compose |
| [Quản lý sự đồng ý](vobs-consent) | `vobs-consent` | Vòng đời consent; chính sách theo Điều 5, 11 viết bằng bảng quyết định RuleStone; kiểm tra quyền; log kiểm toán chuỗi hash; API HTTP | 2 | Lõi, API HTTP, lưu trữ PostgreSQL; chạy một bản |
| [Integration Hub](vobs-hub) | `vobs-hub` | CDC từ core (Debezium), chuẩn hóa, kho đọc | 2 | Parser và projector; chưa có connector core |
| [API Gateway](vobs-gateway) | `vobs-gateway` | Plugin APISIX: consent, giới hạn tần suất, log kiểm toán | 2 | Plugin consent chạy trên APISIX 3.19 trong sandbox; chưa thử với Keycloak |
| [Bộ triển khai](vobs-deploy) | `vobs-deploy` | Helm chart tổng, GitOps, bộ cài air-gapped | 2 | Khung |

Module Decision (trên [RuleStone](https://github.com/xvalleycorp/RuleStone)), Fraud và AML (tích hợp Tazama) thuộc giai
đoạn 3.

## Bắt đầu

Cần JDK 21, Maven 3.9 trở lên và git. [RuleStone](https://github.com/xvalleycorp/RuleStone) (bộ máy bảng quyết định,
dùng trong `vobs-consent`) chưa có trên Maven Central, nên cài từ mã nguồn một lần trước khi build:

```bash
./scripts/install-rulestone.sh  # cài RuleStone tại commit cố định vào kho Maven cục bộ
mvn verify                      # build và chạy test mọi module Java
mvn -pl vobs-vietqr -am verify  # chỉ một module và các module nó cần
mvn -Plicense-check verify      # kiểm tra giấy phép của dependency
```

Ví dụ dùng thư viện VietQR:

```java
String payload = VietQr.toAccount("999901", "0123456789").withAmount(150_000).encode();
VietQr qr = VietQr.parse(payload);
```

## Tài liệu

- [Đối chiếu Thông tư 64/2024/TT-NHNN](docs/tt64.md)
- [Kiến trúc và yêu cầu phi chức năng](docs/architecture.md)
- [Lộ trình](docs/roadmap.md)
- [Open-source dùng lại và giấy phép cần tránh](docs/licensing.md)
- [Quản trị dự án và mô hình kinh doanh](GOVERNANCE.md)
- [Hướng dẫn đóng góp](CONTRIBUTING.md) · [Chính sách bảo mật](SECURITY.md)

## Giấy phép

[Apache License 2.0](LICENSE). Phần lõi cam kết không đổi giấy phép.

> Các văn bản pháp lý nhắc trong repo (Thông tư 64/2024/TT-NHNN, Thông tư 09/2020/TT-NHNN, Nghị định 85/2016/NĐ-CP,
> Luật Dữ liệu 2024, Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 thay cho Nghị định 13/2023/NĐ-CP) cần được kiểm
> tra lại hiệu lực và nội dung trước khi dùng chính thức.
