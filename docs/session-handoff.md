# Bàn giao phiên làm việc (24/10/2026)

Tài liệu nội bộ để tiếp tục công việc ở phiên khác. Không đưa nội dung này lên website.

## Trạng thái repo

- Nhánh làm việc: `feat/website`. Mọi commit có `Signed-off-by: Steve VU <stevevu.xvalley@gmail.com>` (repo bắt
  buộc DCO; khi commit dùng `git commit -s`).
- Đã merge vào `main`:
  - [#15](https://github.com/xvalleycorp/VOBS/pull/15): đặc tả và consent theo Thông tư 64.
  - [#17](https://github.com/xvalleycorp/VOBS/pull/17): website, logo H3, bảng màu Đồng và Hổ phách.
  - [#18](https://github.com/xvalleycorp/VOBS/pull/18): nội dung "mã nguồn mở, phù hợp nhu cầu", nút sáng/tối.
  - [#19](https://github.com/xvalleycorp/VOBS/pull/19) (merge 24/10/2026): API HTTP và lưu trữ PostgreSQL cho Quản
    lý sự đồng ý; tách nội dung website khỏi kế hoạch nội bộ; tên thân thiện cho module; trang API; Luật 91/2025/QH15.
- Từ sau #19, mỗi việc một nhánh tạo từ `main` mới và một PR riêng (không dùng lại `feat/website`).
- Đã merge thêm ngày 24/10/2026 (phiên local): #20 (tên "Quản lý sự đồng ý"), #21 (service consent trong sandbox),
  #22 (ngoại lệ giấy phép cho driver PostgreSQL), #23 (API Gateway APISIX trong sandbox), #24 (gộp các bản nâng
  dependency, gom nhóm Dependabot), #27 (Dependabot chỉ nhận bản vá trong Java 21).
- Website tự deploy lên https://vobs.dxa.io.vn khi `main` thay đổi (workflow `website.yml`, secret
  `CLOUDFLARE_API_TOKEN` đã cài). Lần deploy gần nhất: sau khi merge #18.

Trước khi làm tiếp ở máy local, đồng bộ nhánh (đã từng mất một commit vì nhánh local cũ bị force-push đè):

```bash
git fetch origin
git checkout feat/website
git reset --hard origin/feat/website   # chỉ khi local không có thay đổi chưa push
```

Nếu #19 đã merge: tạo lại nhánh từ `main` mới (`git checkout -B feat/website origin/main`) và mở PR mới, không dùng
lại PR đã merge.

## Quyết định đã chốt

### Nhận diện

- **Logo H3:** chữ V từ hai mắt xích dài bằng nhau, móc vào nhau ở đầu dưới. Mắt trái màu Đồng (ngân hàng), mắt
  phải màu Hổ phách (bên thứ ba). Lòng mắt chỉ rộng hơn thanh khoảng 17% để xích có cảm giác chặt. Ở giao điểm dưới,
  mắt Đồng nằm trên; ở giao điểm trên, mắt Hổ phách nằm trên. Khe hở cắt theo mép mắt nằm trên.
- Tham số hình học và cách dựng nằm trong [`website/tools/logo.py`](../website/tools/logo.py); chạy script để
  sinh lại `logo.svg`, `favicon.svg` và đoạn SVG trong header, hero sau khi đổi tham số.
- **Favicon** riêng: thanh mảnh hơn, lòng rộng hơn để đọc được ở 16px.
- **Màu:** Đồng `#C2693A`, Hổ phách `#E9A33D`; trên nền tối `#E1884F`, `#F2B65B`. Chữ nâu `#2A1D19`, nút nâu
  `#6B3A22`, nền kem `#FBF7F1`, nâu đêm `#2A1D19`. **Không dùng đen** (kiêng trong tài chính ở Việt Nam).
- Màu không cần né ngân hàng nào, chỉ cần không nhầm với nhận diện của một thương hiệu cụ thể.
- Trang duyệt các vòng thiết kế logo: https://claude.ai/artifact/TZevTHQo7ShQrnB8TVSYGj (riêng tư).

### Cách viết nội dung

- Nói "mã nguồn mở", không dùng "mở" đứng một mình (chữ Open đã có trong Open Banking).
- Nói VOBS "phù hợp nhu cầu của mọi ngân hàng" và ngân hàng "tự chủ tùy chỉnh", không nói "dùng chung cho mọi ngân
  hàng". Không dùng "trung lập" (bản tiếng Anh vẫn dùng "neutral"). Không dùng con số mục tiêu "80%"; nói "phần lớn", "phần đa".
- Website chỉ cho người dùng cuối (ngân hàng, bên thứ ba, đơn vị tích hợp, cơ quan quản lý). Lộ trình, giai đoạn,
  mô hình kinh doanh, quy trình nội bộ (CI, script, tên file) chỉ nằm trong markdown của repo. Quy ước chi tiết:
  [`website/README.md`](../website/README.md#nội-dung-website-và-tài-liệu-nội-bộ).
- Chỉ mô tả cái đã có; nhãn module: Dùng được, Thử nghiệm, Đang phát triển, Nháp.
- Gọi module bằng tên thân thiện (bảng trong [README gốc](../README.md#module)); mã `vobs-*` chỉ để tra cứu.
- Trích dẫn pháp lý: Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 (đã thay Nghị định 13/2023/NĐ-CP).

### Quản lý sự đồng ý (`vobs-consent`)

- Hai cổng HTTP tách biệt: cổng đối tác (`/consents`) và cổng nội bộ (`/internal/...` cho gateway và kênh ngân
  hàng). Danh tính đối tác lấy từ header `x-vobs-client-id` do gateway đặt và ghi đè.
- Đối tác tạo consent khi chưa biết khách hàng; khách hàng được gắn vào consent khi duyệt hoặc từ chối.
- Lưu trữ PostgreSQL khi có `VOBS_CONSENT_DATABASE_URL`, không thì trong bộ nhớ. Bảng log kiểm toán chỉ ghi thêm
  (trigger chặn sửa, xóa); service kiểm tra chuỗi hash khi khởi động.
- Driver PostgreSQL dùng ngoại lệ giấy phép theo artifact, ghim phiên bản; nâng driver thì sửa
  `vobs-consent/src/license/override-THIRD-PARTY.properties` sau khi xác nhận giấy phép (`docs/licensing.md`).

## Quyết định ngày 24/10/2026 (phiên local)

| Câu hỏi | Quyết định |
| --- | --- |
| Merge #19 | Đã merge |
| Tiêu đề trang chủ | Giữ "Open Banking mã nguồn mở cho ngân hàng Việt Nam" |
| Chữ "trung lập" trong `GOVERNANCE.md`, README Thư viện ISO 20022 | Sửa cả hai |
| Chữ "neutral" ở bản tiếng Anh | Giữ |
| Tên thân thiện của `vobs-consent` | "Quản lý sự đồng ý" (tiếng Anh giữ "Consent Manager") |
| Link `docs/architecture.md` ở chân trang website | Bỏ |
| Con số "đáp ứng sẵn >80% nhu cầu" | Chỉ là mục tiêu nội bộ, không kiểm chứng được; tài liệu nói "phần lớn", "phần đa" |
| Việc làm tiếp | Đưa service Quản lý sự đồng ý vào sandbox |

Giấy phép BSD-2-Clause: không mở rộng danh sách cho phép chung; dùng ngoại lệ cho riêng driver JDBC PostgreSQL
42.7.14 (sổ ngoại lệ trong `docs/licensing.md`). Image Docker kèm thông báo giấy phép của thư viện đi kèm.

## Việc tiếp theo đề xuất

- **Quản lý sự đồng ý:**
  - chạy nhiều bản service cùng lúc (khóa khi ghi log kiểm toán, khóa lạc quan khi đổi trạng thái consent);
  - connection pool;
  - đẩy log kiểm toán sang Kafka;
  - kiểm tra quyền chi tiết bằng OpenFGA;
  - luồng duyệt consent trên Temporal.
- **API Gateway:** đã chạy plugin `vobs-consent` trên APISIX 3.19 trong sandbox (`try-gateway-flow.sh`, CI); `proxy-rewrite`
  đọc được `$vobs_client_id`. Việc kế: thử với `openid-connect` và Keycloak thật, plugin token gắn chứng thư mTLS
  (RFC 8705), WAF.
- **Sandbox cho bên thứ ba:** đã đưa service Quản lý sự đồng ý vào `docker compose` (nhánh `feat/sandbox-consent`), kèm
  `try-consent-flow.sh` và job CI `sandbox` build image, chạy thử luồng, quét Trivy. Máy local chưa có Docker nên
  phần container chỉ được kiểm chứng trên CI. Việc kế: dữ liệu mẫu phong phú hơn, nối bộ giả lập chuyển mạch.
- **Lộ trình** ([`docs/roadmap.md`](roadmap.md)):
  - tra cứu nhãn hiệu VOBS và logo tại Cục Sở hữu trí tuệ (nhóm 9, 36, 42);
  - đối chiếu Phụ lục 01, 02 Thông tư 64;
  - tìm hiểu đặc tả kết nối NAPAS, MDP;
  - phát hành Thư viện VietQR và Thư viện ISO 20022 lên Maven Central (cần namespace và khóa GPG).

## Dependabot

- Gom nhóm: plugin Maven, bản nâng nhỏ của thư viện, GitHub Actions, npm của website; bản nâng lớn là PR riêng. Chỉ
  đề xuất phiên bản đã ra ít nhất 14 ngày.
- PR nâng driver PostgreSQL cố ý đỏ ở bước kiểm tra giấy phép: xác nhận giấy phép bản mới, rồi sửa phiên bản trong
  `vobs-consent/src/license/override-THIRD-PARTY.properties`.
- Image Docker chỉ nhận bản vá trong Java 21; image build (`maven:*`) cập nhật tay cùng lúc với JDK. Đổi bản Java
  lớn (ví dụ Java 25 LTS) là quyết định riêng, đổi cùng `pom.xml` và CI.

## Lưu ý khi làm tiếp

- **Kiểm tra trước khi push:**
  - `mvn -B verify`
  - `mvn -B -Plicense-check -DskipTests verify`
  - `npx --yes @redocly/cli@1.25.0 lint vobs-spec/openapi/*.yaml`
  - `python3 vobs-spec/scripts/check_tt64_coverage.py`
  - `cd website && npm ci && node build.mjs`
- **Test PostgreSQL** (`PostgresStorageTest`) chỉ chạy khi có `VOBS_TEST_DATABASE_URL`, ví dụ
  `jdbc:postgresql://localhost:5432/vobs_test?user=vobs`. CI luôn chạy với service PostgreSQL 16.
- **CSP của website chặt** (`style-src 'self'`, không script inline): mọi script là file trong `static/assets`.
- **Trang API (Redoc)** chỉ có giao diện sáng; trang này dùng `body.page-api` để header trải hết bề ngang.
- **Dependency mới phải qua quét lỗ hổng bằng Trivy trong CI.** Lần thêm driver PostgreSQL 42.7.7 từng làm CI đỏ vì hai CVE mức HIGH, nên chọn bản mới nhất.
