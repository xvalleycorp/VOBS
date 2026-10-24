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
| Quy tắc nghiệp vụ | RuleStone (sản phẩm cùng nhóm phát triển) | Apache 2.0 | Bảng quyết định có phiên bản, ngày hiệu lực: chính sách nhận consent; sau này hạn mức, phí thanh toán, quy tắc Fraud và AML |
| Sandbox | Microcks | Apache 2.0 | API giả lập, kiểm thử hợp đồng |
| Bí mật | OpenBao | MPL 2.0 | Secret, khóa, chứng thư |
| WAF | Coraza + OWASP CRS | Apache 2.0 | Chặn tấn công tầng ứng dụng |
| Quan sát | OpenTelemetry, Prometheus, Jaeger | Apache 2.0 | Trace, metric, log |
| Chuỗi cung ứng | Sigstore cosign, Syft, Trivy, OSS Review Toolkit | Apache 2.0 | Ký số, SBOM, quét lỗ hổng, kiểm tra giấy phép |
| Developer portal | Backstage | Apache 2.0 | Tài liệu API, đăng ký ứng dụng |
| Fraud và AML | Tazama | Apache 2.0 | Giám sát giao dịch thời gian thực (giai đoạn 3) |

## Giấy phép cần tránh

Pháp chế ngân hàng thường chặn các giấy phép dưới đây. Profile `license-check` của Maven dùng danh sách cho phép
(Apache-2.0, MIT, BSD-3-Clause, EDL-1.0): dependency mang giấy phép khác làm build thất bại cho tới khi được xem xét.
Mở rộng danh sách cho phép là thay đổi chính sách; trường hợp lẻ được xử lý bằng ngoại lệ theo từng artifact (mục
dưới).

| Tránh | Vì sao | Dùng thay |
| --- | --- | --- |
| Redis bản mới | RSAL/SSPL, sau đó thêm AGPL | Valkey |
| HashiCorp Vault, Terraform | BSL | OpenBao, OpenTofu |
| MongoDB | SSPL | PostgreSQL |
| Elasticsearch | Elastic License/SSPL/AGPL | OpenSearch |
| Confluent Schema Registry, ksqlDB | Confluent Community License | Apicurio Registry, Kafka Streams |
| Camunda 8 | Không còn giấy phép open-source cho production | Temporal |
| MinIO | AGPL | Storage sẵn có của ngân hàng, Ceph, SeaweedFS |

## Ngoại lệ giấy phép

Ngoại lệ chỉ áp dụng cho một artifact ở một phiên bản cụ thể, không cho cả loại giấy phép. Ngoại lệ khai báo trong
`src/license/override-THIRD-PARTY.properties` của module dùng artifact đó. Khi nâng phiên bản, build dừng cho tới
khi có người xác nhận giấy phép của bản mới và sửa khóa.

| Artifact | Phiên bản | Giấy phép | Dùng ở | Lý do | Quyết định |
| --- | --- | --- | --- | --- | --- |
| `org.postgresql:postgresql` (driver JDBC chính thức của PostgreSQL) | 42.7.14 | BSD-2-Clause | Quản lý sự đồng ý | Lưu consent và log kiểm toán vào PostgreSQL. Driver đóng gói kèm thư viện SCRAM, SASLprep của OnGres, cùng giấy phép BSD-2-Clause | Chủ dự án, 24/10/2026 |

BSD-2-Clause dễ dãi hơn BSD-3-Clause đang được cho phép: chỉ bỏ điều khoản cấm dùng tên tác giả để quảng bá; tương
thích với Apache 2.0. Nghĩa vụ duy nhất là giữ thông báo bản quyền và nội dung giấy phép khi phân phối lại.

## Thông báo giấy phép khi phân phối

Image Docker của dự án kèm thư mục `/app/licenses`: `LICENSE`, `NOTICE` của VOBS, danh sách `THIRD-PARTY.txt` và
file giấy phép, thông báo lấy nguyên văn từ từng thư viện đi kèm. Một số thư viện không đóng file giấy phép vào JAR
(CEL, Protobuf, RE2/J, ANTLR, SnakeYAML và vài thư viện annotation, đều đến cùng RuleStone); văn bản giấy phép của
chúng được lấy từ repo gốc đúng tag phiên bản, lưu trong
[`vobs-consent/src/license/third-party`](../vobs-consent/src/license/third-party). Build image dừng nếu một thư viện
không có giấy phép ở cả hai nơi.

RuleStone chưa phát hành lên Maven Central nên được build từ mã nguồn tại một commit cố định
([`scripts/install-rulestone.sh`](../scripts/install-rulestone.sh)); mã commit là hash nội dung nên không bị thay
nội dung ngầm. Dependabot không theo dõi được commit này; đổi bằng tay. Tên giấy phép "Go License" của RE2/J được gộp
vào BSD-3-Clause vì giấy phép của Go chính là BSD-3-Clause. Build image chạy profile `license-check`, nên không
dựng được image khi có dependency mang giấy phép ngoài danh sách cho phép.

Grafana và Loki (AGPL) dùng nguyên bản được, nhưng không nhúng vào sản phẩm. Giấy phép có thể thay đổi; kiểm tra
lại tại thời điểm chọn dùng.
