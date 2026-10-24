# Bộ triển khai (`vobs-deploy`)

Triển khai VOBS lên Kubernetes của ngân hàng: Helm chart tổng, GitOps với Argo CD, bộ cài air-gapped.
Đang ở dạng khung; nội dung chính thuộc giai đoạn 2.

## Mục tiêu

| Yêu cầu | Cách đáp ứng |
| --- | --- |
| Chạy on-premise, dữ liệu trong nước | Mọi thành phần chạy trong cụm của ngân hàng, không phụ thuộc cloud ngoài |
| Cài không cần Internet | Gói image (OCI) và chart đi kèm, đẩy vào registry nội bộ qua `global.imageRegistry` |
| Nâng cấp không dừng dịch vụ | Rolling update, PodDisruptionBudget, migration database tương thích ngược |
| DC/DR, RTO dưới 30 phút | Kafka MirrorMaker 2, CloudNativePG replica cluster, kịch bản chuyển vùng diễn tập định kỳ |
| Chuỗi cung ứng | Image ký bằng cosign, kèm SBOM; chính sách admission chỉ nhận image đã ký |

## Thành phần dự kiến

APISIX, Keycloak, Strimzi (Kafka), Debezium, Apicurio Registry, CloudNativePG, Valkey, Temporal, OpenBao,
OpenTelemetry Collector, Prometheus, Jaeger. Mỗi chart phụ thuộc sẽ được ghim phiên bản và qua bước kiểm tra
giấy phép (xem [docs/licensing.md](../docs/licensing.md)).

## Thử chart khung

```bash
helm lint vobs-deploy/helm/vobs
helm template vobs vobs-deploy/helm/vobs
```
