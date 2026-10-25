# Hạ tầng tham khảo và chi phí

Tài liệu này đưa ra cấu hình hạ tầng và chi phí tham khảo cho VOBS ở ba quy mô, theo hai cách triển khai:
on-premise và cloud. Phía cloud gồm AWS, Azure, Google Cloud, Alibaba Cloud và các nhà cung cấp trong nước
(Viettel IDC, CMC Cloud, FPT Cloud, VNG Cloud).

Giá thu thập ngày 25/10/2026, tỷ giá 26.100 đ/USD, chưa gồm VAT. Số liệu do
[scripts/sizing.py](../scripts/sizing.py) tính. Ngân hàng sửa giả định hoặc đơn giá ở đầu file rồi chạy lại để có
bảng mới. Các con số dùng để lập ngân sách sơ bộ, không thay cho báo giá.

Kết luận chính:

- **Nơi đặt production.** Production có dữ liệu khách hàng chỉ nên đặt on-premise, trên cloud trong nước, hoặc
  trên AWS Local Zone Hà Nội. Azure, Google Cloud và Alibaba Cloud chưa có hạ tầng tại Việt Nam. Ba nhà cung cấp
  này hợp với sandbox và môi trường phát triển, nơi không có dữ liệu thật.
- **Chi phí 5 năm.** Ở quy mô vừa và lớn, on-premise có tổng chi phí 5 năm thấp nhất: khoảng 0,8 triệu USD (M)
  và 1,7 triệu USD (L) cho hai site. Ở quy mô nhỏ, cloud cam kết 3 năm và on-premise ngang nhau, khoảng 0,4 đến
  0,6 triệu USD.
- **Chi phí lớn nhất.** Máy ảo chiếm khoảng 53–77% chi phí cloud. Khoản lớn thứ hai là đĩa SSD cho Kafka, PostgreSQL và
  hệ thống quan sát. Ở quy mô L, phần ngoài máy ảo lên tới 40–47%.

## Giả định về tải

| Quy mô | Đối tượng | Tải đỉnh | Lượt gọi API mỗi ngày | Đối tác | Khách hàng trong kho đọc |
|---|---|---|---|---|---|
| S | Thí điểm hoặc ngân hàng nhỏ | 150 TPS | 3 triệu | 30 | 2 triệu |
| M | Ngân hàng quy mô trung bình | 800 TPS | 20 triệu | 150 | 8 triệu |
| L | Ngân hàng có tập khách hàng lớn | 3.000 TPS | 80 triệu | 500 | 30 triệu |

Các giả định khác:

- Mỗi lượt gọi trả về trung bình 4 KB cho đối tác.
- Mỗi lượt gọi sinh 1 KB log kiểm toán sau nén.
- Log kiểm toán lưu 1 năm trên lưu trữ đối tượng không sửa được (WORM).
- Bản sao lưu PostgreSQL chiếm gấp đôi dung lượng cơ sở dữ liệu.

Mục tiêu hiệu năng (p99 đọc dưới 200 ms, sẵn sàng 99,95%, RPO gần 0, RTO dưới 30 phút) lấy từ phần yêu cầu phi
chức năng của [kiến trúc](architecture.md).

## Kiến trúc triển khai

Mỗi ngân hàng chạy ba môi trường:

1. **Site production.** Một cụm Kubernetes trải trên 3 vùng sự cố: 3 zone trên cloud, hoặc 3 nhóm máy chủ khác
   nguồn điện và switch ở on-premise. Cụm có hai nhóm node:
    - nhóm **app** (8 vCPU/32 GB) chạy các service không trạng thái;
    - nhóm **data** (8 vCPU/64 GB) chạy Kafka, PostgreSQL, Valkey và hệ thống quan sát.
2. **Site DR** ở trung tâm dữ liệu thứ hai, chạy ở chế độ chờ nóng:
    - nhóm data giữ đủ số node để nhận bản sao liên tục (CloudNativePG, Kafka MirrorMaker 2);
    - nhóm app chạy một nửa số node và tăng lên khi chuyển vùng.
3. **Phi production** gồm dev, UAT và sandbox cho đối tác, đặt ở site chính.

VOBS cần kết nối riêng tới trung tâm dữ liệu của ngân hàng để Debezium đọc thay đổi từ core và để gửi lệnh
thanh toán. Khi chạy trên cloud, kết nối này dùng Direct Connect, ExpressRoute, Interconnect hoặc kênh thuê riêng
của nhà mạng.

Kafka, PostgreSQL và Valkey tự vận hành trên Kubernetes bằng operator (Strimzi, CloudNativePG) ở mọi nơi triển
khai. Nhờ vậy cấu hình giống nhau giữa on-premise và các cloud. Cách này cũng không phụ thuộc dịch vụ có quản lý
mà AWS Local Zone và cloud trong nước chưa có đủ.

## Tài nguyên từng thành phần

Mỗi ô ghi số bản × vCPU/RAM, kèm đĩa dữ liệu mỗi bản nếu có.

| Thành phần | S | M | L |
|---|---|---|---|
| API Gateway (APISIX, Coraza WAF) | 3 × 2/4 GB | 3 × 4/8 GB | 6 × 4/8 GB |
| Keycloak | 3 × 2/4 GB | 3 × 4/8 GB | 4 × 4/8 GB |
| Quản lý sự đồng ý | 3 × 1/2 GB | 3 × 2/4 GB | 6 × 2/4 GB |
| OpenFGA | 3 × 1/2 GB | 3 × 2/4 GB | 6 × 2/4 GB |
| Service đọc dữ liệu, payment adapter | 3 × 2/4 GB | 4 × 2/4 GB | 8 × 2/4 GB |
| Kafka Connect, Debezium | 2 × 2/8 GB | 3 × 4/16 GB | 4 × 4/16 GB |
| OpenBao | 3 × 1/2 GB, 20 GB | 3 × 1/2 GB, 20 GB | 3 × 2/4 GB, 20 GB |
| Nền tảng (ingress, cert-manager, Argo CD) | 1 × 4/8 GB | 1 × 8/16 GB | 1 × 16/32 GB |
| Kafka (KRaft) | 3 × 4/16 GB, 1.000 GB | 3 × 8/32 GB, 2.000 GB | 5 × 8/64 GB, 4.000 GB |
| Kafka controller riêng | | | 3 × 2/4 GB, 50 GB |
| PostgreSQL kho đọc, consent, Keycloak (CloudNativePG) | 3 × 4/32 GB, 1.000 GB | 3 × 8/64 GB, 2.000 GB | |
| PostgreSQL kho đọc (CloudNativePG) | | | 3 × 16/128 GB, 4.000 GB |
| PostgreSQL consent, Keycloak (CloudNativePG) | | | 3 × 8/64 GB, 1.000 GB |
| Valkey | 3 × 2/8 GB | 3 × 4/16 GB | 6 × 4/32 GB |
| Quan sát (Prometheus, Loki, Tempo, Grafana) | 3 × 2/8 GB, 1.000 GB | 3 × 8/32 GB, 3.000 GB | 5 × 8/32 GB, 6.000 GB |

Keycloak được định cỡ theo [hướng dẫn của Keycloak](https://www.keycloak.org/high-availability/multi-cluster/concepts-memory-and-cpu-sizing):
mỗi vCPU xử lý khoảng 120 lượt cấp token client credentials mỗi giây, cộng 150% dự phòng cho tải đột biến. Với
cấu hình trên, quy mô S cấp được khoảng 290 token mỗi giây và quy mô L khoảng 770 token mỗi giây. Đối tác dùng lại
token trong thời hạn hiệu lực, nên số lượt cấp token thấp hơn nhiều so với số lượt gọi API.

## Định cỡ mỗi site production

Số node bằng tổng tài nguyên các thành phần nhân 1,3, làm tròn lên bội số của 3 để chia đều cho 3 vùng sự cố.
Phần nhân 1,3 dự phòng cho một node hỏng, DaemonSet và tải đột biến.

| Quy mô | Tải đỉnh | Node app (8 vCPU/32 GB) | Node data (8 vCPU/64 GB) | Tổng vCPU / RAM | Đĩa SSD dữ liệu | Lưu trữ đối tượng năm đầu | Băng thông ra/tháng |
|---|---|---|---|---|---|---|---|
| S | 150 TPS | 6 | 6 | 96 vCPU / 576 GB | 9,1 TB | 3,1 TB | 0,4 TB |
| M | 800 TPS | 12 | 15 | 216 vCPU / 1.344 GB | 21,1 TB | 11,3 TB | 2,4 TB |
| L | 3.000 TPS | 21 | 30 | 408 vCPU / 2.592 GB | 65,2 TB | 39,2 TB | 9,6 TB |

Ở quy mô L, PostgreSQL kho đọc cần node 16 vCPU/128 GB. Mỗi node như vậy tính bằng 2 node data trong bảng.

## Chọn nơi triển khai

| Nơi triển khai | Hạ tầng tại Việt Nam | Kubernetes có quản lý | Phù hợp |
|---|---|---|---|
| On-premise (2 trung tâm dữ liệu của ngân hàng hoặc thuê chỗ đặt) | Có | Tự vận hành (RKE2, kubeadm hoặc bản phân phối có hỗ trợ thương mại) | Production, DR, mọi quy mô |
| [AWS Local Zone Hà Nội](https://aws.amazon.com/about-aws/whats-new/2026/06/aws-local-zones-hanoi-vietnam/) (từ 19/06/2026) | Có, một zone; điều khiển từ vùng Singapore | EKS | Production; DR phải đặt ở nơi khác trong nước vì Hà Nội chỉ có một zone |
| Viettel IDC, CMC Cloud, FPT Cloud, VNG Cloud | Có | CMC Kubernetes, FPT Kubernetes Engine, [VNG VKS](https://docs.vngcloud.vn/vng-cloud-document/vn/vks/vks-la-gi) | Production, DR, hoặc DR cho on-premise |
| Azure (Southeast Asia), Google Cloud (asia-southeast1), Alibaba Cloud (Singapore) | Không, gần nhất là Singapore | AKS, GKE, ACK | Sandbox, dev, kiểm thử tải; production chỉ khi pháp chế chấp thuận chuyển dữ liệu ra nước ngoài |

Thông tư 09/2020/TT-NHNN cho phép ngân hàng dùng cloud của bên thứ ba. Điều 33 đến 36 đặt điều kiện về phân loại
hệ thống, tiêu chí chọn nhà cung cấp và phương án dự phòng cho hệ thống từ cấp độ 3 trở lên
([Tạp chí Ngân hàng](https://tapchinganhang.gov.vn/ung-dung-dien-toan-dam-may-trong-hoat-dong-ngan-hang-thach-thuc-va-mot-so-giai-phap-nang-cao-hieu-qua-563.html)).
Yêu cầu lưu dữ liệu trong nước của VOBS dựa trên Luật An ninh mạng và Luật Bảo vệ dữ liệu cá nhân. Bộ phận pháp
chế của ngân hàng cần xác nhận lựa chọn cụ thể.

## Chi phí cloud

Bảng dưới là chi phí mỗi tháng cho cả ba môi trường: production, DR và phi production. Đơn vị USD, chưa gồm VAT.

| Nhà cung cấp | S theo yêu cầu | S cam kết 3 năm | M theo yêu cầu | M cam kết 3 năm | L theo yêu cầu | L cam kết 3 năm |
|---|---|---|---|---|---|---|
| AWS (Local Zone Hà Nội) | 15.602 | 9.538 | 33.163 | 20.241 | 64.649 | 41.776 |
| Azure (Southeast Asia, Singapore) | 14.413 | 7.205 | 31.086 | 15.695 | 60.908 | 33.645 |
| GCP (asia-southeast1, Singapore) | 12.853 | 8.536 | 27.904 | 18.647 | 56.442 | 40.023 |
| Alibaba Cloud (Singapore) | 14.477 | báo giá | 29.820 | báo giá | 57.732 | báo giá |
| Viettel IDC | 9.521 | báo giá | 20.278 | báo giá | 41.742 | báo giá |
| CMC Cloud | 8.710 | báo giá | 18.533 | báo giá | 38.654 | báo giá |
| FPT Cloud, VNG Cloud | báo giá | báo giá | báo giá | báo giá | báo giá | báo giá |

Cách tính:

- **Máy ảo:**
    - AWS: m7i.2xlarge và r7i.2xlarge. Local Zone Hà Nội có cùng giá với Singapore, kể cả Compute Savings Plan.
    - Azure: D8s v5 và E8s v5.
    - Google Cloud: n2-standard-8 và n2-highmem-8. Giá theo yêu cầu đã trừ sustained use discount.
    - Alibaba Cloud: ecs.g7.2xlarge và ecs.r7.2xlarge.
    - Viettel IDC: gói T2.Gen 06 (8 vCPU/32 GB/160 GB) và T2.Mem Burst 03 (8 vCPU/64 GB/180 GB).
    - CMC Cloud: không có gói 8 vCPU/32 GB, nên nội suy từ bảng giá node Kubernetes. Mức nội suy là 150.000 đ mỗi
      vCPU và 75.000 đ mỗi GB RAM mỗi tháng.
- **Cam kết 3 năm:**
    - AWS dùng Compute Savings Plan, Azure dùng Reserved Instance, Google Cloud dùng committed use. Cả ba đều
      không trả trước.
    - Alibaba và cloud trong nước giảm giá khi ký hợp đồng dài hạn, nhưng mức giảm theo báo giá.
- **Đĩa SSD:**
    - Hyperscaler: gp3 0,096 USD, Premium SSD v2 0,0897 USD, pd-balanced 0,11 USD, ESSD PL0 0,0924 USD mỗi GB
      mỗi tháng.
    - Cloud trong nước: khoảng 2.700 đ mỗi GB mỗi tháng, nội suy từ bảng giá CMC.
- **Kubernetes:**
    - EKS, AKS (Standard) và GKE: 0,10 USD/giờ mỗi cụm.
    - Alibaba Cloud và cloud trong nước: tính 3 node control plane 4 vCPU/16 GB tự vận hành. Nếu nhà cung cấp miễn
      phí control plane (như VNG VKS), chi phí giảm tương ứng.
- **Lưu trữ đối tượng:** giả định 0,025 USD mỗi GB mỗi tháng.
- **Băng thông ra Internet:** AWS 0,12 USD, Azure và Google Cloud 0,11 USD, Alibaba 0,081 USD mỗi GB. Cloud trong
  nước thường tính băng thông theo cổng nên bảng ghi 0.
- **Kết nối riêng tới ngân hàng:** chỉ tính cho AWS, gồm 2 cổng Direct Connect 1 Gbps. Phí kênh truyền của nhà
  mạng không tính cho nhà cung cấp nào.

FPT Cloud và VNG Cloud không công bố đơn giá máy ảo. FPT Cloud niêm yết phần lớn dịch vụ ở mức "liên hệ". VNG
Cloud tính giá qua [công cụ ước tính](https://docs.greennode.ai/vn/vserver/compute-hcm03-1a/cach-tinh-gia-vserver).
Khi xin báo giá, gửi kèm bảng định cỡ ở trên.

Chi phí AWS cam kết 3 năm chia theo môi trường (USD/tháng):

| Quy mô | Production | DR | Phi production | Lưu trữ, băng thông, kết nối riêng | Tổng |
|---|---|---|---|---|---|
| S | 3.636 | 3.048 | 2.296 | 559 | 9.538 |
| M | 8.239 | 7.062 | 3.931 | 1.008 | 20.241 |
| L | 18.034 | 16.268 | 4.903 | 2.570 | 41.776 |

## Chi phí on-premise

Mỗi quy mô cần hai site: site chính chạy production và phi production, site DR có cùng số máy chủ với production.
Đơn vị USD.

| Quy mô | Máy chủ tính toán mỗi site | Máy lưu trữ mỗi site | Tủ rack (2 site) | Đầu tư ban đầu | Thuê chỗ đặt/tháng | Quy ra tháng (khấu hao 5 năm) | Tổng 5 năm |
|---|---|---|---|---|---|---|---|
| S | 3 + 2 phi production | 0 | 2 | 352.000 | 2.299 | 8.166 | 489.931 |
| M | 3 + 3 phi production | 0 | 3 | 602.000 | 3.448 | 13.482 | 808.897 |
| L | 8 + 4 phi production | 3 | 5 | 1.332.000 | 5.747 | 27.947 | 1.676.828 |

Giả định cho on-premise (cần thay bằng báo giá thực tế):

- **Máy chủ tính toán** 2U, đã gồm bảo hành 5 năm. Giá đã tính mức tăng giá RAM máy chủ trong 2025–2026: giá hợp
  đồng DRAM quý 1/2026 tăng khoảng 93–98% so với quý trước
  ([TrendForce qua Network World](https://www.networkworld.com/article/4093752/server-memory-prices-could-double-by-2026-as-ai-demand-strains-supply.html)).
    - Quy mô S: 2 × 16 nhân, 256 GB, 4 × 1,92 TB NVMe, khoảng 25.000 USD mỗi máy.
    - Quy mô M và L: 2 × 32 nhân, 512 GB, 4 × 3,84 TB NVMe, khoảng 50.000 USD mỗi máy.
- **Số máy mỗi site** đủ cho tải và còn dư một máy (N+1). Ở quy mô L, số máy bị giới hạn bởi dung lượng NVMe.
- **Control plane Kubernetes:** 3 máy 1U, mỗi máy khoảng 12.000 USD, mỗi site.
- **Lưu trữ đối tượng:** khi cần trên 20 TB thì dùng máy lưu trữ riêng chạy Ceph RGW, khoảng 30.000 USD cho
  100 TB dùng được. Ít hơn 20 TB thì đặt trên NVMe của máy tính toán.
- **Mạng:** 2 switch ToR 25 GbE và switch quản trị, khoảng 40.000 USD mỗi site. Tường lửa, chống DDoS và cân bằng
  tải dùng hạ tầng ngân hàng đã có.
- **Thuê chỗ đặt:**
    - Tủ 42U tại trung tâm dữ liệu Tier III, tính 30 triệu đ mỗi tháng.
    - Giá niêm yết dao động 20–42 triệu đ: [TND](https://www.tnd.vn/colocation/),
      [VNPT](https://vnpthcm.vn/bang-gia-vnpt-colocation-thue-cho-dat-may-chu/),
      [Viettel IDC](https://viettel-idc.com.vn/index.php/tin-tuc/tin-cong-nghe/dich-vu-cho-thue-cho-dat-may-chu-viettel-colocation-15.html).
    - Mỗi tủ đặt tối đa 8 máy chủ do giới hạn điện 6–8 kW.
    - Nếu đặt trong trung tâm dữ liệu của ngân hàng, dòng này thay bằng chi phí điện và chỗ đặt nội bộ.
- **Phần mềm:** toàn bộ là open-source, không có phí bản quyền. Hỗ trợ thương mại cho hệ điều hành, Kubernetes
  hoặc VOBS là tùy chọn và tính theo báo giá.

## So sánh tổng chi phí 5 năm

Bảng dưới chưa gồm nhân sự vận hành. Đơn vị nghìn USD.

| Phương án | S | M | L |
|---|---|---|---|
| On-premise (2 site) | 490 | 809 | 1.677 |
| AWS (Local Zone Hà Nội) | 572 | 1.214 | 2.507 |
| Azure (Southeast Asia, Singapore) | 432 | 942 | 2.019 |
| GCP (asia-southeast1, Singapore) | 512 | 1.119 | 2.401 |
| Alibaba Cloud (Singapore) | 869 | 1.789 | 3.464 |
| Viettel IDC | 571 | 1.217 | 2.505 |
| CMC Cloud | 523 | 1.112 | 2.319 |

Cách tính:

- Cloud tính theo giá cam kết 3 năm và gia hạn cùng mức cho 2 năm cuối.
- Alibaba Cloud và cloud trong nước tính theo giá niêm yết hằng tháng, nên chênh lệch sẽ giảm khi có báo giá dài
  hạn.

Đọc bảng này cần tính thêm nhân sự:

- On-premise cần đội vận hành phần cứng, Kubernetes và các hệ thống có trạng thái: khoảng 3 người ở quy mô S đến
  6 người ở quy mô L.
- Cloud bớt phần phần cứng, nhưng vẫn cần người vận hành Kafka, PostgreSQL và Kubernetes.

## Chưa tính trong bảng

- **HSM.** VOBS dùng HSM sẵn có của ngân hàng qua PKCS#11, chỉ cần thêm phân vùng. Mua HSM mới hoặc dùng HSM trên
  cloud thì tính theo báo giá.
- **Kênh truyền tới ngân hàng.** Gồm kênh thuê riêng của nhà mạng, phí cross-connect và Internet cho site
  on-premise.
- **Kiểm thử và chứng nhận.** Pentest độc lập, đánh giá an toàn hệ thống thông tin theo cấp độ, chứng nhận
  PCI DSS nếu có.
- **Công cụ bảo mật của ngân hàng.** SIEM, PAM, quét lỗ hổng, nếu ngân hàng bắt buộc cài agent trên node.
- **Thuế.** VAT, và thuế nhà thầu nước ngoài với dịch vụ cloud nước ngoài.

## Cập nhật số liệu

```sh
python3 scripts/sizing.py
```

Script in lại toàn bộ các bảng trong tài liệu này. Đổi tải ở `TIERS`, đơn giá cloud ở `CLOUDS`, giá phần cứng và
chỗ đặt ở `ONPREM`, tỷ giá ở `USD_VND`. Nên cập nhật giá mỗi 6 tháng hoặc khi có báo giá chính thức.

Trang [Hạ tầng](https://vobs.dxa.io.vn/ha-tang/) trên website dựng từ cùng số liệu. Sau khi đổi script, sinh lại dữ
liệu cho website; CI báo lỗi nếu hai bên lệch nhau:

```sh
python3 scripts/sizing.py --json > website/data/infrastructure.json
```

## Nguồn

Thu thập ngày 25/10/2026.

- **AWS:**
    - [Local Zone Hà Nội](https://aws.amazon.com/about-aws/whats-new/2026/06/aws-local-zones-hanoi-vietnam/)
    - [EC2 On-Demand](https://aws.amazon.com/ec2/pricing/on-demand/) và
      [Compute Savings Plans](https://aws.amazon.com/savingsplans/compute-pricing/), vùng Singapore và Vietnam
      (Hanoi)
    - [EKS](https://aws.amazon.com/eks/pricing/)
    - [Direct Connect](https://aws.amazon.com/directconnect/pricing/)
- **Azure:** [Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices),
  vùng southeastasia, gồm D8s v5, E8s v5, AKS và Premium SSD v2.
- **Google Cloud:** [n2-standard-8](https://gcloud-compute.com/n2-standard-8.html) và
  [n2-highmem-8](https://gcloud-compute.com/n2-highmem-8.html), dữ liệu cập nhật 18/10/2026.
- **Alibaba Cloud:** [ecs.g7.2xlarge](https://sparecores.com/server/alicloud/ecs.g7.2xlarge) và
  [ecs.r7.2xlarge](https://sparecores.com/server/alicloud/ecs.r7.2xlarge) trên Spare Cores.
- **Đĩa và băng thông của hyperscaler:** [Spare Cores API](https://keeper.sparecores.net/docs), dữ liệu
  24/10/2026.
- **Cloud trong nước:**
    - [Viettel IDC Cloud Server](https://viettel-idc.com.vn/)
    - [CMC Cloud Kubernetes](https://cmccloud.vn/dich-vu/kubernetes)
    - [CMC Cloud RDS](https://cmccloud.vn/dich-vu/relational-database-service)
    - [FPT Cloud bảng giá](https://fptcloud.com/bang-gia/)
    - [VNG Cloud cách tính giá vServer](https://docs.greennode.ai/vn/vserver/compute-hcm03-1a/cach-tinh-gia-vserver)
- **Tỷ giá:** [Vietcombank ngày 20/10/2026](https://webgia.com/ty-gia/vietcombank/20-10-2026.html), giá bán
  26.100 đ/USD.
- **Định cỡ Keycloak:** [Keycloak: memory and CPU sizing](https://www.keycloak.org/high-availability/multi-cluster/concepts-memory-and-cpu-sizing).
