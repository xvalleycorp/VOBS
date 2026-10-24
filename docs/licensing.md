# Open-source dùng lại và giấy phép

Phần hạ tầng được lắp từ các dự án đã trưởng thành; VOBS chỉ tự xây lớp nghiệp vụ Việt Nam và phần kết nối.
Ngôn ngữ chính là Java 21, cùng hệ sinh thái với Keycloak, Debezium và Kafka.

## Dự án dùng lại

| Lớp | Dự án | Giấy phép | Vai trò |
| --- | --- | --- | --- |
| API Gateway | Apache APISIX (hoặc Kong Gateway OSS) | Apache 2.0 | Định tuyến, giới hạn tần suất, mTLS, plugin |
| Xác thực | Keycloak | Apache 2.0 | OAuth 2.0, OIDC, FAPI 2.0, đăng ký client |
| Phân quyền | OpenFGA, Open Policy Agent | Apache 2.0 | Kiểm tra quyền theo consent và chính sách |
| CDC | Debezium | Apache 2.0 | Lấy thay đổi từ database của core |
| Streaming | Apache Kafka + Strimzi | Apache 2.0 | Luồng sự kiện, log kiểm toán |
| Schema | Apicurio Registry | Apache 2.0 | Phiên bản schema sự kiện |
| Kho đọc | PostgreSQL + CloudNativePG, Valkey | PostgreSQL, Apache 2.0, BSD-3 | Dữ liệu đã chuẩn hóa, cache |
| Workflow | Temporal | MIT | Luồng consent, onboarding đối tác, saga thanh toán |
| Thông điệp thanh toán | Prowide ISO 20022 | Apache 2.0 | Model và parser ISO 20022 |
| Sandbox | Microcks | Apache 2.0 | API giả lập, kiểm thử hợp đồng |
| Bí mật | OpenBao | MPL 2.0 | Secret, khóa, chứng thư |
| WAF | Coraza + OWASP CRS | Apache 2.0 | Chặn tấn công tầng ứng dụng |
| Quan sát | OpenTelemetry, Prometheus, Jaeger | Apache 2.0 | Trace, metric, log |
| Chuỗi cung ứng | Sigstore cosign, Syft, Trivy, OSS Review Toolkit | Apache 2.0 | Ký số, SBOM, quét lỗ hổng, kiểm tra giấy phép |
| Developer portal | Backstage | Apache 2.0 | Tài liệu API, đăng ký ứng dụng |
| Fraud và AML | Tazama | Apache 2.0 | Giám sát giao dịch thời gian thực (giai đoạn 3) |

## Giấy phép cần tránh

Pháp chế ngân hàng thường chặn các giấy phép dưới đây. Profile `license-check` của Maven dùng danh sách cho phép
(Apache-2.0, MIT, BSD-3-Clause, EDL-1.0): dependency mang giấy phép khác làm build thất bại cho tới khi được
xem xét và thêm vào danh sách.

| Tránh | Vì sao | Dùng thay |
| --- | --- | --- |
| Redis bản mới | RSAL/SSPL, sau đó thêm AGPL | Valkey |
| HashiCorp Vault, Terraform | BSL | OpenBao, OpenTofu |
| MongoDB | SSPL | PostgreSQL |
| Elasticsearch | Elastic License/SSPL/AGPL | OpenSearch |
| Confluent Schema Registry, ksqlDB | Confluent Community License | Apicurio Registry, Kafka Streams |
| Camunda 8 | Không còn giấy phép open-source cho production | Temporal |
| MinIO | AGPL | Storage sẵn có của ngân hàng, Ceph, SeaweedFS |

Grafana và Loki (AGPL) dùng nguyên bản được, nhưng không nhúng vào sản phẩm. Giấy phép có thể thay đổi; kiểm tra
lại tại thời điểm chọn dùng.
