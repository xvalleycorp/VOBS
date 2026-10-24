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

VOBS phải trung lập giữa các ngân hàng, nên không có màu chủ đạo đơn lẻ: gần như mọi màu (xanh lá, xanh ngọc, đỏ,
xanh dương, tím, cam) đều đã gắn với một ngân hàng Việt Nam. Thay vào đó:

- Nền mực than `#121317` và giấy ấm `#F6F5F1`; nút chính dùng màu mực.
- Màu nhận diện là dải phổ `#FF6B4A → #FFC145 → #3DD6C6 → #7A6CFF`: lớp hạ tầng chung nối mọi ngân hàng. Chỉ
  dùng cho logo, đồ họa và đường nhấn, không lấy một màu đơn trong dải làm màu thương hiệu.
- Logo: chữ V vẽ bằng nét dải phổ trên nền mực ([`static/assets/logo.svg`](static/assets/logo.svg)).
- Chữ: Be Vietnam Pro, phông thiết kế cho tiếng Việt.

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
