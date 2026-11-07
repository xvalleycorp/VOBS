# Đo tải API Gateway

Tài liệu này mô tả bộ đo tải trong [`vobs-sandbox/load`](../vobs-sandbox/load) và kết quả đo đầu tiên. Bộ đo gọi API đọc
tài khoản qua đường đi đầy đủ: mTLS, introspection với Keycloak, kiểm tra token gắn chứng thư, kiểm tra consent.

Kết luận chính:

- **Chưa kiểm chứng được bảng định cỡ.** Kết quả đo trên một máy 4 vCPU dùng chung, không phải cụm nhiều node, nên các
  mức 800 và 3.000 TPS trong [bảng định cỡ](infrastructure.md) vẫn là ước tính.
- **Phát hiện và sửa một lỗi độ trễ.** Mỗi lượt đọc API mất thêm khoảng 40 ms ở service consent. Sau khi sửa, p50 ở 800
  lượt/s giảm từ 41 ms xuống 1 ms.
- **Introspection không làm chậm đáng kể ở tải vừa.** Ở 400 lượt/s, không cache có p50 khoảng 2,5 ms, so với 1,0–1,2 ms
  khi có cache.

## Cách chạy

```bash
cd vobs-sandbox
./pki.sh
./load/run.sh
```

Script tự dựng sandbox, đo rồi tự dừng; kết quả nằm trong `load/results`. Trên GitHub Actions, chạy workflow "Đo tải"
bằng tay. Workflow này không chạy ở mọi PR, trừ PR sửa chính bộ đo.

Bộ đo tăng dần tốc độ từ 50 đến 1.600 lượt/s, mỗi mức 40 giây, sau một phút làm nóng. Một profile dừng ở mức đầu tiên
có p99 trên 200 ms, lỗi trên 1%, hoặc không đạt tốc độ yêu cầu. Có hai profile:

| Profile | Cách giữ kết quả introspection |
|---|---|
| `cached` | Giữ 30 giây, như cấu hình mẫu của gateway |
| `nocache` | Không giữ: mọi request đều hỏi Keycloak, trường hợp xấu nhất |

## Kết quả

Đo ngày 07/11/2026 trên runner GitHub Actions 4 vCPU, commit `0e4386a`, 50 mã truy cập. Mỗi ô độ trễ là kết quả của hai
lượt chạy độc lập, viết theo thứ tự lượt 1 / lượt 2.

| Profile | Tải (lượt/s) | p50 (ms) | p99 (ms) | vCPU gateway | vCPU Keycloak | vCPU consent |
|---|---:|---:|---:|---:|---:|---:|
| `cached` | 200 | 1,1 / 1,3 | 7,0 / 7,7 | 0,17 / 0,21 | 0,16 / 0,15 | 0,04 / 0,04 |
| `cached` | 400 | 1,0 / 1,2 | 12,0 / 9,5 | 0,36 / 0,40 | 0,03 / 0,05 | 0,09 / 0,09 |
| `cached` | 800 | 1,0 / 1,0 | 127,8 / 236,4 | 0,58 / 0,61 | 0,09 / 0,08 | 0,15 / 0,15 |
| `nocache` | 200 | 2,6 / 2,6 | 9,6 / 10,6 | 0,29 / 0,29 | 0,57 / 0,61 | 0,05 / 0,05 |
| `nocache` | 400 | 2,6 / 2,5 | 14,4 / 20,4 | 0,49 / 0,51 | 0,58 / 0,72 | 0,09 / 0,10 |
| `nocache` | 800 (đạt 784 / 775) | 8,2 / 8,5 | 1.383,6 / 1.948,5 | 1,09 / 1,10 | 1,31 / 1,26 | 0,22 / 0,19 |

Không có lỗi HTTP ở các mức trên. Lượt 1 chạy thêm `cached` ở 1.600 lượt/s và không đạt tốc độ (p99 768,7 ms). Lượt 2 đã
dừng ở `cached` 800 vì p99 vượt mục tiêu, nên `cached` 800 là mức sát ngưỡng trên máy này.

Từ bảng trên:

- **Mức đạt ổn định trên máy đo.** Cả hai profile đều đạt ở 400 lượt/s, p99 dưới 25 ms. Ở mức cao hơn, tổng CPU của ba
  service lên khoảng 2,6 vCPU trên máy 4 vCPU, chưa kể k6, upstream giả lập và phần còn lại của hệ thống. Vì vậy giới
  hạn này nhiều khả năng là của cả máy, không phải của riêng thành phần nào. Bộ đo chưa ghi CPU của k6 để xác nhận.
- **Chi phí introspection.** Ở 400 và 800 lượt/s không cache, mỗi vCPU Keycloak xử lý khoảng 550–700 lượt/s. Ở mức thấp
  hơn, con số này thấp hơn nhiều vì CPU của JVM khi chưa tải đủ dao động mạnh; không nên dùng các mức đó để suy ra.
- **Hiệu quả của cache.** Ở 400 lượt/s, CPU của Keycloak giảm từ 0,58–0,72 xuống 0,03–0,05 vCPU.

## Lỗi độ trễ đã sửa

Trước khi sửa, p50 giữ nguyên khoảng 41 ms từ 100 đến 800 lượt/s, trong khi CPU còn thấp. Service consent dùng
`HttpServer` của JDK: server này ghi header và nội dung thành hai lần và mặc định không bật `TCP_NODELAY`, nên lần ghi sau
chờ ACK của lần trước, mà Linux trì hoãn ACK khoảng 40 ms. Gateway gọi service consent ở mọi request, nên mọi lượt đọc
API đều chịu độ trễ này. Lỗi không xuất hiện khi chạy trên macOS.

`ConsentHttpServer` đã bật `sun.net.httpserver.nodelay`.

| 800 lượt/s, `cached` | p50 | p95 |
|---|---:|---:|
| Trước khi sửa | 41,0 ms | 42,1 ms |
| Sau khi sửa (hai lượt) | 1,0 ms | 3,0 / 5,3 ms |

**Điều chưa giải thích được.** Trước khi sửa, `nocache` ở 800 lượt/s có p99 50,5 ms; sau khi sửa, cả hai lượt đều không
đạt, p99 trên 1.300 ms. `cached` ở 800 lượt/s cũng tăng p99 từ 43,9 ms lên 127,8–236,4 ms, dù p50 giảm mạnh. Giả thuyết là
khoảng chờ 40 ms trước đây giãn request ra đều hơn, nên máy không bị nghẽn đột biến. Giả thuyết này chưa được kiểm chứng.

## Giới hạn của kết quả

Không dùng các con số trên để suy ra công suất production:

- **Máy đo.** Một máy dùng chung, k6 chạy cùng các service, và kết quả dao động giữa các lượt.
- **Một bản mỗi service.** Không đo được chia tải, đồng bộ giữa các bản Keycloak, hay độ trễ mạng giữa các node.
- **Keycloak chế độ dev.** Dùng cơ sở dữ liệu nhúng, không có PostgreSQL.
- **Chỉ có token client credentials.** Token của khách hàng còn tra phiên đăng nhập, nên introspection có thể tốn hơn.
- **Service consent lưu trong bộ nhớ.** Môi trường thật đọc từ PostgreSQL, nên chậm hơn.
- **Thiếu thành phần.** Không có WAF, `kafka-logger`, kho đọc thật; upstream là bản giả lập trả cố định 4 KB.
- **Thời gian ngắn.** Mỗi mức chỉ 40 giây, không phát hiện được rò bộ nhớ hay suy giảm theo thời gian.

## Việc cần làm để kiểm chứng bảng định cỡ

Chạy bộ đo trên một cụm có cấu hình như một quy mô trong [bảng định cỡ](infrastructure.md), k6 đặt trên máy riêng, với:

1. Keycloak chế độ production với PostgreSQL, service consent với PostgreSQL.
2. Token của khách hàng, theo tỷ lệ gần với thực tế.
3. WAF, `kafka-logger` và kho đọc thật.
4. Mức tải tới 800 hoặc 3.000 TPS, giữ ít nhất vài giờ.
