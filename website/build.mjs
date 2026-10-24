// Dựng website tĩnh song ngữ vào dist/:
// - mỗi trang trong templates/ được render cho từng ngôn ngữ, chữ lấy từ i18n/<lang>.json;
// - chép static/ (CSS, ảnh, script, _headers) nguyên trạng;
// - tự host font Be Vietnam Pro và Redoc (không gọi CDN bên ngoài);
// - đọc đặc tả trong vobs-spec để sinh trang tài liệu API và bảng đối chiếu Thông tư 64,
//   nên website luôn khớp với đặc tả trong cùng commit.
// Build dừng khi: đặc tả thiếu API của Điều 6, hai từ điển lệch khóa, hoặc còn placeholder chưa thay.
import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const root = dirname(fileURLToPath(import.meta.url));
const specDir = join(root, "..", "vobs-spec");
const dist = join(root, "dist");
const modules = join(root, "node_modules");
const SITE = "https://vobs.dxa.io.vn";

const LANGS = ["vi", "en"];
const DEFAULT_LANG = "vi";
// Đường dẫn của mỗi trang theo ngôn ngữ; trang tiếng Anh nằm dưới /en/
const PAGES = {
  home: { template: "home.html", vi: "/", en: "/en/" },
  tt64: { template: "tt64.html", vi: "/thong-tu-64/", en: "/en/circular-64/" },
  api: { template: "api.html", vi: "/api/", en: "/en/api/" },
  notFound: { template: "404.html", vi: "/404.html", en: "/en/404.html", noAlternates: true },
};
const NAV_PAGE = { tt64: "tt64", api: "api" };

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
  for (const [section, isWebhook] of [["paths", false], ["webhooks", true]]) {
    for (const [key, item] of Object.entries(spec[section] ?? {})) {
      for (const method of HTTP_METHODS) {
        const op = item[method];
        if (!op?.["x-tt64"]) continue;
        add(op["x-tt64"], { method: isWebhook ? "WEBHOOK" : method.toUpperCase(), path: key, operationId: op.operationId });
      }
    }
  }
  return byRef;
}

function tt64Table(catalog, byRef, t, lang) {
  const rows = [];
  let group = null;
  for (const api of catalog.apis) {
    if (api.group !== group) {
      group = api.group;
      rows.push(`<tr class="group"><th colspan="3" scope="colgroup">${escapeHtml(t(`tt64.group.${group}`))}</th></tr>`);
    }
    const ops = (byRef.get(api.ref) ?? [])
      .map((op) => {
        const anchor = op.method === "WEBHOOK" ? `webhook/${op.operationId}` : `operation/${op.operationId}`;
        return `<a class="op" href="${PAGES.api[lang]}#${anchor}"><span class="method m-${op.method.toLowerCase()}">${op.method}</span><code>${escapeHtml(op.path)}</code></a>`;
      })
      .join("");
    const name = lang === "vi" ? api.name : api.en;
    const note = api.verbatim ? "" : `<span class="note" title="${escapeHtml(t("tt64.translatedNote"))}">*</span>`;
    rows.push(
      `<tr><td class="ref"><code>${escapeHtml(api.ref)}</code></td>` +
        `<td>${escapeHtml(name)}${note}</td>` +
        `<td><div class="ops-list">${ops || `<span class="missing">${escapeHtml(t("tt64.missing"))}</span>`}</div></td></tr>`,
    );
  }
  return rows.join("\n");
}

function provisionRows(provisions, lang) {
  // Nội dung trong data/ do dự án viết, được phép chứa <code>
  return provisions
    .map((p) => `<tr><td>${escapeHtml(p.article)}</td><td>${p[lang].rule}</td><td>${p[lang].vobs}</td></tr>`)
    .join("\n");
}

function moduleCards(list, t, lang) {
  return list
    .map((m) => `        <article class="card module">
          <header><code>${escapeHtml(m.name)}</code><span class="status status-${m.status}">${m[lang].label}</span></header>
          <p>${escapeHtml(m[lang].desc)}</p>
        </article>`)
    .join("\n");
}

function headLinks(pageKey, lang) {
  const page = PAGES[pageKey];
  const links = [
    `<link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">`,
    // Chạy trước khi vẽ trang để áp giao diện người xem đã chọn, tránh nháy sai màu
    `<script src="/assets/theme.js"></script>`,
    `<link rel="stylesheet" href="/assets/style.css">`,
    `<script src="/assets/lang.js" defer></script>`,
  ];
  if (!page.noAlternates) {
    links.unshift(`<link rel="canonical" href="${SITE}${page[lang]}">`);
    for (const alt of LANGS) links.push(`<link rel="alternate" hreflang="${alt}" href="${SITE}${page[alt]}">`);
    links.push(`<link rel="alternate" hreflang="x-default" href="${SITE}${page[DEFAULT_LANG]}">`);
  }
  return links.join("\n  ");
}

function render(template, { pageKey, lang, dict, partials, values }) {
  const t = (key) => {
    if (!(key in dict)) throw new Error(`Thiếu khóa "${key}" trong i18n/${lang}.json`);
    return dict[key];
  };
  const navPage = NAV_PAGE[pageKey];
  const header = partials.header
    .replace(`data-page="${navPage}"`, `data-page="${navPage}" aria-current="page"`)
    .replace(`data-lang="${lang}"`, `data-lang="${lang}" aria-current="true"`);

  let html = template
    .replace("<!-- HEADER -->", header)
    .replace("<!-- FOOTER -->", partials.footer)
    .replaceAll("{{head:links}}", headLinks(pageKey, lang));
  // Chữ trong từ điển có thể chứa placeholder khác ({{url:...}}, {{SPEC_VERSION}}), nên thay t: trước
  html = html.replace(/\{\{t:([a-zA-Z0-9.]+)\}\}/g, (_, key) => t(key));
  html = html
    .replaceAll("{{lang}}", lang)
    .replaceAll("{{canonical}}", `${SITE}${PAGES[pageKey][lang]}`)
    .replace(/\{\{url:([a-zA-Z0-9]+)\}\}/g, (_, key) => PAGES[key][lang])
    .replace(/\{\{switch:([a-z]+)\}\}/g, (_, target) => (PAGES[pageKey].noAlternates ? PAGES.home : PAGES[pageKey])[target])
    .replaceAll("{{flag:current}}", lang === "vi" ? partials.flagVn : partials.flagUs)
    .replaceAll("{{flag:vn}}", partials.flagVn)
    .replaceAll("{{flag:us}}", partials.flagUs);
  // Giá trị sinh ra (ví dụ nhãn module) có thể chứa {{SPEC_VERSION}}; thay hai lượt
  const v = values(t);
  for (let pass = 0; pass < 2; pass++) {
    for (const [key, value] of Object.entries(v)) html = html.replaceAll(key, value);
  }

  const leftover = html.match(/\{\{[^}]+\}\}|<!-- (HEADER|FOOTER) -->/);
  if (leftover) throw new Error(`Còn placeholder "${leftover[0]}" trong trang ${pageKey} (${lang})`);
  return html;
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
  for (const file of await readdir(join(pkg, "files"))) {
    const wanted = file.endsWith(".woff2")
      && subsets.some((s) => file.startsWith(`be-vietnam-pro-${s}-`))
      && weights.some((w) => file.endsWith(`-${w}-normal.woff2`));
    if (wanted) await cp(join(pkg, "files", file), join(dist, "fonts", "files", file));
  }
}

async function readJson(path) {
  return JSON.parse(await readFile(path, "utf8"));
}

async function main() {
  await rm(dist, { recursive: true, force: true });
  await cp(join(root, "static"), dist, { recursive: true });
  await copyFonts();
  await cp(join(modules, "redoc", "bundles", "redoc.standalone.js"), join(dist, "api", "redoc.standalone.js"));

  const specYaml = await readFile(join(specDir, "openapi", "vobs-open-api.yaml"), "utf8");
  const spec = parse(specYaml);
  const catalog = await readJson(join(specDir, "tt64-catalog.json"));
  const byRef = operationsByRef(spec);
  const missing = catalog.apis.filter((api) => !byRef.has(api.ref)).map((api) => api.ref);
  if (missing.length) throw new Error(`Đặc tả thiếu API của Điều 6: ${missing.join(", ")}`);

  await mkdir(join(dist, "spec"), { recursive: true });
  await writeFile(join(dist, "spec", "vobs-open-api.yaml"), specYaml);
  await writeFile(join(dist, "api", "vobs-open-api.json"), JSON.stringify(spec));

  const dicts = Object.fromEntries(
    await Promise.all(LANGS.map(async (l) => [l, await readJson(join(root, "i18n", `${l}.json`))])),
  );
  const [base, ...others] = LANGS;
  for (const other of others) {
    const a = Object.keys(dicts[base]);
    const b = Object.keys(dicts[other]);
    const diff = [...a.filter((k) => !b.includes(k)), ...b.filter((k) => !a.includes(k))];
    if (diff.length) throw new Error(`i18n/${base}.json và i18n/${other}.json lệch khóa: ${diff.join(", ")}`);
  }

  const moduleList = await readJson(join(root, "data", "modules.json"));
  const provisions = await readJson(join(root, "data", "tt64-provisions.json"));
  const tpl = (name) => readFile(join(root, "templates", name), "utf8");
  const partials = {
    header: await tpl("_header.html"),
    footer: await tpl("_footer.html"),
    flagVn: (await tpl("_flag-vn.svg")).trim(),
    flagUs: (await tpl("_flag-us.svg")).trim(),
  };

  for (const lang of LANGS) {
    const values = (t) => {
      const v = {
        "{{SPEC_VERSION}}": escapeHtml(spec.info.version),
        "{{TT64_TOTAL}}": String(catalog.apis.length),
        "{{TT64_COVERED}}": String(catalog.apis.length - missing.length),
        "{{MODULE_COUNT}}": String(moduleList.length),
        "{{TT64_TABLE}}": tt64Table(catalog, byRef, t, lang),
        "{{TT64_PROVISIONS}}": provisionRows(provisions, lang),
        "{{MODULE_CARDS}}": moduleCards(moduleList, t, lang),
      };
      for (const group of ["a", "b", "c"]) {
        v[`{{TT64_GROUP_${group.toUpperCase()}}}`] = String(catalog.apis.filter((api) => api.group === group).length);
      }
      return v;
    };
    for (const [pageKey, page] of Object.entries(PAGES)) {
      const html = render(await tpl(page.template), { pageKey, lang, dict: dicts[lang], partials, values });
      const out = page[lang].endsWith(".html") ? join(dist, page[lang]) : join(dist, page[lang], "index.html");
      await mkdir(dirname(out), { recursive: true });
      await writeFile(out, html);
    }
  }
  console.log(`Đã dựng dist/: ${LANGS.join(", ")}; đặc tả ${spec.info.version}, ${catalog.apis.length} API của Điều 6`);
}

await main();
