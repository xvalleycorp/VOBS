# website

Website giới thiệu VOBS tại <https://vobs.dxa.io.vn>, phục vụ bằng Cloudflare Workers (chỉ static assets, không có
Worker script).

| Trang | Nguồn |
| --- | --- |
| `/` | [`src/index.html`](src/index.html) |
| `/thong-tu-64/` | [`src/thong-tu-64/index.html`](src/thong-tu-64/index.html); bảng đối chiếu sinh từ đặc tả |
| `/api/` | Redoc hiển thị [`vobs-spec/openapi/vobs-open-api.yaml`](../vobs-spec/openapi/vobs-open-api.yaml) |
| `/spec/vobs-open-api.yaml` | Bản sao đặc tả để tải về |

`build.mjs` đọc đặc tả trong `vobs-spec` mỗi lần build, nên website luôn khớp đặc tả cùng commit. Build dừng nếu
đặc tả thiếu API nào của Điều 6 Thông tư 64, hoặc còn placeholder chưa thay. Font Be Vietnam Pro và Redoc được tự
host; ngoài Cloudflare Web Analytics (bật ở cấp zone, không dùng cookie), trang không gọi dịch vụ bên ngoài. CSP nằm ở
[`src/_headers`](src/_headers).

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
