# website

Website giới thiệu VOBS tại <https://vobs.dxa.io.vn>, phục vụ bằng Cloudflare Workers (chỉ static assets, không có
Worker script).

Website song ngữ: tiếng Việt ở `/`, tiếng Anh ở `/en/`, chuyển qua lại bằng nút có cờ ở header.

| Trang | Tiếng Việt | Tiếng Anh | Template |
| --- | --- | --- | --- |
| Trang chủ | `/` | `/en/` | [`templates/home.html`](templates/home.html) |
| Đối chiếu Thông tư 64 | `/thong-tu-64/` | `/en/circular-64/` | [`templates/tt64.html`](templates/tt64.html) |
| Đặc tả API (Redoc) | `/api/` | `/en/api/` | [`templates/api.html`](templates/api.html) |

| Thư mục | Nội dung |
| --- | --- |
| `templates/` | Khung HTML dùng chung cho hai ngôn ngữ; chữ là khóa `{{t:...}}` |
| `i18n/vi.json`, `i18n/en.json` | Từ điển; build dừng nếu hai file lệch khóa |
| `data/` | Danh sách module và các điều khoản Thông tư 64, có nội dung cho cả hai ngôn ngữ |
| `static/` | CSS, logo, script, `_headers` (CSP), chép nguyên trạng |

`build.mjs` đọc đặc tả trong `vobs-spec` mỗi lần build, nên website luôn khớp đặc tả cùng commit. Build dừng nếu
đặc tả thiếu API nào của Điều 6 Thông tư 64, hoặc còn placeholder chưa thay. Font Be Vietnam Pro và Redoc được tự
host; ngoài Cloudflare Web Analytics (bật ở cấp zone, không dùng cookie), trang không gọi dịch vụ bên ngoài.

## Nhận diện

VOBS là hạ tầng Open Banking mã nguồn mở, phù hợp nhu cầu của mọi ngân hàng Việt Nam. Nhận diện xoay quanh ý "kết nối
mà mỗi bên vẫn nguyên vẹn":

- Logo: chữ V từ hai mắt xích dài bằng nhau móc vào nhau ở đầu dưới. Mắt trái màu Đồng là ngân hàng, mắt phải màu
  Hổ phách là bên thứ ba. Lòng mắt chỉ rộng hơn thanh một chút để xích có cảm giác chặt
  ([`static/assets/logo.svg`](static/assets/logo.svg)). Favicon có thanh mảnh hơn và lòng rộng hơn để vẫn đọc được
  ở 16px ([`static/assets/favicon.svg`](static/assets/favicon.svg)). Hình học của cả hai sinh bằng
  [`tools/logo.py`](tools/logo.py); đổi tham số trong script rồi chạy lại để cập nhật logo, favicon, header và hero.
- Màu nhận diện: Đồng `#C2693A` và Hổ phách `#E9A33D`; trên nền tối dùng `#E1884F` và `#F2B65B`. Đồng là vật liệu
  của dây dẫn, thứ nối các hệ thống với nhau.
- Nền kem `#FBF7F1`, chữ nâu `#2A1D19`, nút chính nâu `#6B3A22`; nền tối là nâu đêm `#2A1D19`. Không dùng đen, vì
  đen là màu kiêng trong tài chính ở Việt Nam.
- Màu không cần né ngân hàng nào, chỉ cần không bị nhầm với cách một thương hiệu cụ thể dùng màu (ví dụ cam tươi đi
  với xanh dương, hay vàng tươi trên nền đen).

Khi viết nội dung tiếng Việt:

- Nói rõ "mã nguồn mở", không dùng "mở" đứng một mình: chữ "Open" đã nằm trong "Open Banking", còn "mã nguồn mở" là
  thuật ngữ công nghệ rõ nghĩa ở Việt Nam và hàm ý tùy chỉnh được.
- Nói VOBS "phù hợp nhu cầu của mọi ngân hàng", không nói "dùng chung cho mọi ngân hàng": VOBS có sẵn phần mà ngân
  hàng nào cũng cần, và mỗi ngân hàng tự chủ tùy chỉnh phần còn lại theo hiện trạng hệ thống của mình.
- Không dùng "trung lập": trong tiếng Việt từ này không mang nghĩa tốt như "neutral" trong tiếng Anh. Bản tiếng
  Anh vẫn dùng "neutral".
- Không đưa con số mục tiêu chưa kiểm chứng (ví dụ "đáp ứng sẵn 80% nhu cầu") vào tài liệu hay website; nói
  "phần lớn", "phần đa" nhu cầu.
- Chữ: Be Vietnam Pro, phông thiết kế cho tiếng Việt.

## Nội dung website và tài liệu nội bộ

Website viết cho người dùng: ngân hàng, bên thứ ba (fintech, ví điện tử), đơn vị tích hợp và cơ quan quản lý. Kế
hoạch và cách làm việc của nhóm chỉ nằm trong markdown của repo để theo dõi.

| Website (cho người dùng) | Chỉ trong repo (nội bộ) |
| --- | --- |
| VOBS là gì, giải quyết vấn đề gì, kiến trúc | Lộ trình, giai đoạn, cổng đi tiếp, mốc thời gian ([`docs/roadmap.md`](../docs/roadmap.md)) |
| Module nào dùng được ngay, mức sẵn sàng hiện tại | Mô hình kinh doanh, nguồn doanh thu, kế hoạch pháp nhân ([`GOVERNANCE.md`](../GOVERNANCE.md)) |
| Đối chiếu Thông tư 64 và giới hạn hiện có của đặc tả | Quy trình nội bộ: CI, script kiểm tra, cách build, tên file |
| Giấy phép, an toàn chuỗi cung ứng, cách báo lỗi và đóng góp | Ý tưởng, việc cần làm, quyết định còn đang cân nhắc |

Khi viết nội dung cho website:

- Chỉ mô tả cái đã có. Tính năng chưa làm thì không nhắc, hoặc nói rõ "chưa có" ở đúng module; không hứa thời gian.
- Nhãn module nói mức sẵn sàng theo góc nhìn người dùng: Dùng được, Thử nghiệm, Đang phát triển, Nháp.
- Gọi module bằng tên thân thiện (Thư viện VietQR, Quản lý sự đồng ý, Integration Hub...). Mã `vobs-*` là tên thư
  mục và artifact, chỉ hiện kèm để lập trình viên tra cứu. Bảng tên đầy đủ ở [README gốc](../README.md#module);
  tên tiếng Anh nằm trong [`data/modules.json`](data/modules.json).
- Không nhắc công cụ nội bộ (CI, script, tên file trong repo) hay quy trình của nhóm. Viết lợi ích cho người đọc,
  ví dụ "mỗi API ghi rõ điều khoản tương ứng" thay vì "CI kiểm tra độ phủ bằng script".
- Đặc tả OpenAPI hiển thị trên `/api/` cũng là nội dung cho người dùng: phần `description` viết cho lập trình viên
  của ngân hàng và bên thứ ba, không viết cho người bảo trì repo.

## Chạy và triển khai

```bash
cd website
npm ci
npm run dev      # build rồi chạy cục bộ tại http://localhost:8787
npm run deploy   # build rồi deploy lên Cloudflare (cần đăng nhập wrangler hoặc CLOUDFLARE_API_TOKEN)
```

Workflow [`website.yml`](../.github/workflows/website.yml) tự deploy khi `main` thay đổi trong `website/` hoặc
`vobs-spec/`. Workflow cần secret `CLOUDFLARE_API_TOKEN` (quyền Workers Scripts: Edit, Workers Routes: Edit cho zone
`dxa.io.vn`) và biến `CLOUDFLARE_ACCOUNT_ID`; thiếu secret thì workflow chỉ build, không deploy.
