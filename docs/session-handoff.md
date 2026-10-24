# Bàn giao phiên làm việc (24/10/2026)

Tài liệu nội bộ để tiếp tục công việc ở phiên khác. Không đưa nội dung này lên website.

## Trạng thái repo

- Nhánh làm việc: `feat/website`. Mọi commit có `Signed-off-by: Steve VU <stevevu.xvalley@gmail.com>` (repo bắt
  buộc DCO; khi commit dùng `git commit -s`).
- Đã merge vào `main`:
  - [#15](https://github.com/xvalleycorp/VOBS/pull/15): đặc tả và consent theo Thông tư 64.
  - [#17](https://github.com/xvalleycorp/VOBS/pull/17): website, logo H3, bảng màu Đồng và Hổ phách.
  - [#18](https://github.com/xvalleycorp/VOBS/pull/18): nội dung "mã nguồn mở, phù hợp nhu cầu", nút sáng/tối.
- **Đang mở:** [#19](https://github.com/xvalleycorp/VOBS/pull/19), CI xanh cả 5 check trên `dd47f56`, chờ merge.
  Gồm: API HTTP và lưu trữ PostgreSQL cho Quản lý consent; tách nội dung website khỏi kế hoạch nội bộ; tên thân
  thiện cho module; sửa trang API; tiêu đề trang chủ ngắn; trích Luật 91/2025/QH15; driver PostgreSQL 42.7.14.
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
  hàng". Không dùng "trung lập".
- Website chỉ cho người dùng cuối (ngân hàng, bên thứ ba, đơn vị tích hợp, cơ quan quản lý). Lộ trình, giai đoạn,
  mô hình kinh doanh, quy trình nội bộ (CI, script, tên file) chỉ nằm trong markdown của repo. Quy ước chi tiết:
  [`website/README.md`](../website/README.md#nội-dung-website-và-tài-liệu-nội-bộ).
- Chỉ mô tả cái đã có; nhãn module: Dùng được, Thử nghiệm, Đang phát triển, Nháp.
- Gọi module bằng tên thân thiện (bảng trong [README gốc](../README.md#module)); mã `vobs-*` chỉ để tra cứu.
- Trích dẫn pháp lý: Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 (đã thay Nghị định 13/2023/NĐ-CP).

### Quản lý consent (`vobs-consent`)

- Hai cổng HTTP tách biệt: cổng đối tác (`/consents`) và cổng nội bộ (`/internal/...` cho gateway và kênh ngân
  hàng). Danh tính đối tác lấy từ header `x-vobs-client-id` do gateway đặt và ghi đè.
- Đối tác tạo consent khi chưa biết khách hàng; khách hàng được gắn vào consent khi duyệt hoặc từ chối.
- Lưu trữ PostgreSQL khi có `VOBS_CONSENT_DATABASE_URL`, không thì trong bộ nhớ. Bảng log kiểm toán chỉ ghi thêm
  (trigger chặn sửa, xóa); service kiểm tra chuỗi hash khi khởi động.
- Giấy phép BSD-2-Clause được thêm vào danh sách cho phép cho driver PostgreSQL (`docs/licensing.md`).

## Câu hỏi đang chờ quyết định

1. Merge #19?
2. Tiêu đề trang chủ: giữ "Open Banking mã nguồn mở cho ngân hàng Việt Nam", hay dùng "…, hợp với mọi ngân hàng" /
   "…, ngân hàng tự chủ tùy chỉnh"?
3. Còn chữ "trung lập" ngoài website: `GOVERNANCE.md` ("quản trị trung lập") và `vobs-iso20022/README.md` (nghĩa
   kỹ thuật "dạng trung lập"). Có sửa không?
4. Bản tiếng Anh giữ chữ "neutral" (nghĩa tốt trong tiếng Anh)?
5. Chân trang website còn link tới `docs/architecture.md` (có phần yêu cầu phi chức năng). Giữ hay bỏ?
6. Tên module: giữ chữ "consent" hay đổi "Quản lý sự đồng ý"? Đổi `<name>` trong `pom.xml` (hiện "VOBS Consent"…)
   theo tên thân thiện?
7. Pháp chế có cần duyệt việc thêm BSD-2-Clause vào danh sách giấy phép cho phép?
8. Con số "đáp ứng sẵn >80% nhu cầu" chưa đưa lên website vì chưa có số liệu chứng minh. Có đưa không?

## Việc tiếp theo đề xuất

- **Quản lý consent:**
  - chạy nhiều bản service cùng lúc (khóa khi ghi log kiểm toán, khóa lạc quan khi đổi trạng thái consent);
  - connection pool;
  - đẩy log kiểm toán sang Kafka;
  - kiểm tra quyền chi tiết bằng OpenFGA;
  - luồng duyệt consent trên Temporal.
- **API Gateway:** chạy thử plugin `vobs-consent` và route `/consents` trên APISIX thật. Route `/consents` cần xác nhận `proxy-rewrite` đọc được biến `$vobs_client_id`.
- **Sandbox cho bên thứ ba:** đưa service consent vào `docker compose` của sandbox, để fintech thử trọn luồng tạo, duyệt, kiểm tra consent.
- **Lộ trình** ([`docs/roadmap.md`](roadmap.md)):
  - tra cứu nhãn hiệu VOBS và logo tại Cục Sở hữu trí tuệ (nhóm 9, 36, 42);
  - đối chiếu Phụ lục 01, 02 Thông tư 64;
  - tìm hiểu đặc tả kết nối NAPAS, MDP;
  - phát hành Thư viện VietQR và Thư viện ISO 20022 lên Maven Central (cần namespace và khóa GPG).

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
