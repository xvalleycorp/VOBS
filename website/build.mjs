// Dựng website tĩnh vào dist/:
// - chép src/ (HTML, CSS, ảnh viết tay);
// - tự host font Be Vietnam Pro và Redoc (không gọi CDN bên ngoài);
// - đọc đặc tả trong vobs-spec để sinh trang tài liệu API và bảng đối chiếu Thông tư 64,
//   nên website luôn khớp với đặc tả trong cùng commit.
import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const root = dirname(fileURLToPath(import.meta.url));
const specDir = join(root, "..", "vobs-spec");
const dist = join(root, "dist");
const modules = join(root, "node_modules");

const HTTP_METHODS = ["get", "post", "put", "patch", "delete"];

const escapeHtml = (text) =>
  String(text).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

// Ánh xạ mã điều khoản (x-tt64.ref) -> các operation trong đặc tả
function operationsByRef(spec) {
  const byRef = new Map();
  const add = (tt64, label) => {
    for (const entry of [tt64].flat()) {
      if (!byRef.has(entry.ref)) byRef.set(entry.ref, []);
      byRef.get(entry.ref).push(label);
    }
  };
  for (const [path, item] of Object.entries(spec.paths ?? {})) {
    for (const method of HTTP_METHODS) {
      const op = item[method];
      if (op?.["x-tt64"]) add(op["x-tt64"], { method: method.toUpperCase(), path, operationId: op.operationId });
    }
  }
  for (const [name, item] of Object.entries(spec.webhooks ?? {})) {
    for (const method of HTTP_METHODS) {
      const op = item[method];
      if (op?.["x-tt64"]) add(op["x-tt64"], { method: "WEBHOOK", path: name, operationId: op.operationId });
    }
  }
  return byRef;
}

const GROUPS = {
  a: "Điểm a — Tỷ giá và lãi suất",
  b: "Điểm b — Thông tin khách hàng",
  c: "Điểm c — Thanh toán và ví điện tử",
};

function tt64Table(catalog, byRef) {
  const rows = [];
  let group = null;
  for (const api of catalog.apis) {
    if (api.group !== group) {
      group = api.group;
      rows.push(`<tr class="group"><th colspan="3" scope="colgroup">${escapeHtml(GROUPS[group])}</th></tr>`);
    }
    const ops = byRef.get(api.ref) ?? [];
    const opsHtml = ops
      .map((op) => {
        const anchor = op.method === "WEBHOOK" ? `webhook/${op.operationId}` : `operation/${op.operationId}`;
        return `<a class="op" href="/api/#${anchor}"><span class="method m-${op.method.toLowerCase()}">${op.method}</span><code>${escapeHtml(op.path)}</code></a>`;
      })
      .join("");
    const note = api.verbatim ? "" : `<span class="note" title="Tên dịch lại từ bản tiếng Anh, chưa khớp nguyên văn">*</span>`;
    rows.push(
      `<tr><td class="ref"><code>${escapeHtml(api.ref)}</code></td>` +
        `<td>${escapeHtml(api.name)}${note}</td>` +
        `<td><div class="ops-list">${opsHtml || '<span class="missing">Chưa có</span>'}</div></td></tr>`,
    );
  }
  return rows.join("\n");
}

async function copyFonts() {
  const pkg = join(modules, "@fontsource", "be-vietnam-pro");
  const weights = ["400", "500", "600", "700"];
  const subsets = ["vietnamese", "latin", "latin-ext"];
  const css = [];
  for (const weight of weights) {
    // Chỉ giữ các subset cần cho tiếng Việt và tiếng Anh
    const text = await readFile(join(pkg, `${weight}.css`), "utf8");
    css.push(
      text
        .split("/*")
        .filter((block) => subsets.some((s) => block.startsWith(` be-vietnam-pro-${s}-${weight}-normal`)))
        .map((block) => "/*" + block)
        .join("")
        // Chỉ phát hành woff2 (mọi trình duyệt hiện đại đều hỗ trợ), bỏ nguồn woff dự phòng
        .replace(/, url\([^)]*\.woff\) format\('woff'\)/g, ""),
    );
  }
  await mkdir(join(dist, "fonts", "files"), { recursive: true });
  await writeFile(join(dist, "fonts", "be-vietnam-pro.css"), css.join("\n"));
  const files = await readdir(join(pkg, "files"));
  for (const file of files) {
    if (file.endsWith(".woff2") && file.includes("-normal") && subsets.some((s) => file.startsWith(`be-vietnam-pro-${s}-`))
      && weights.some((w) => file.includes(`-${w}-normal`))) {
      await cp(join(pkg, "files", file), join(dist, "fonts", "files", file));
    }
  }
}

// Chèn header, footer dùng chung; `<!-- HEADER:tt64 -->` đánh dấu mục điều hướng data-page="tt64" là trang hiện tại
function withPartials(html, header, footer) {
  return html
    .replace(/<!-- HEADER:([a-z0-9-]+) -->/, (_, page) =>
      header.replace(`data-page="${page}"`, `data-page="${page}" aria-current="page"`))
    .replace("<!-- FOOTER -->", footer);
}

async function main() {
  await rm(dist, { recursive: true, force: true });
  await cp(join(root, "src"), dist, { recursive: true, filter: (src) => !src.includes("_partials") });
  const header = await readFile(join(root, "src", "_partials", "header.html"), "utf8");
  const footer = await readFile(join(root, "src", "_partials", "footer.html"), "utf8");
  await copyFonts();
  await cp(join(modules, "redoc", "bundles", "redoc.standalone.js"), join(dist, "api", "redoc.standalone.js"));

  const specYaml = await readFile(join(specDir, "openapi", "vobs-open-api.yaml"), "utf8");
  const spec = parse(specYaml);
  const catalog = JSON.parse(await readFile(join(specDir, "tt64-catalog.json"), "utf8"));
  const byRef = operationsByRef(spec);

  const missing = catalog.apis.filter((api) => !byRef.has(api.ref)).map((api) => api.ref);
  if (missing.length) throw new Error(`Đặc tả thiếu API của Điều 6: ${missing.join(", ")}`);

  await mkdir(join(dist, "spec"), { recursive: true });
  await writeFile(join(dist, "spec", "vobs-open-api.yaml"), specYaml);
  await writeFile(join(dist, "api", "vobs-open-api.json"), JSON.stringify(spec));

  const values = {
    "{{SPEC_VERSION}}": escapeHtml(spec.info.version),
    "{{TT64_TOTAL}}": String(catalog.apis.length),
    "{{TT64_COVERED}}": String(catalog.apis.length - missing.length),
    "<!-- TT64_TABLE -->": tt64Table(catalog, byRef),
  };
  for (const group of Object.keys(GROUPS)) {
    values[`{{TT64_GROUP_${group.toUpperCase()}}}`] = String(catalog.apis.filter((api) => api.group === group).length);
  }
  for (const page of ["index.html", "thong-tu-64/index.html", "api/index.html", "404.html"]) {
    const file = join(dist, page);
    let html = await readFile(file, "utf8");
    html = withPartials(html, header, footer);
    for (const [key, value] of Object.entries(values)) html = html.replaceAll(key, value);
    if (/\{\{[A-Z0-9_]+\}\}|<!-- (HEADER|FOOTER|TT64_TABLE)/.test(html)) {
      throw new Error(`Còn placeholder chưa thay trong ${page}`);
    }
    await writeFile(file, html);
  }
  console.log(`Đã dựng dist/: đặc tả ${spec.info.version}, ${catalog.apis.length} API của Điều 6`);
}

await main();
