// Dựng website tĩnh song ngữ vào dist/:
// - mỗi trang trong templates/ được render cho từng ngôn ngữ, chữ lấy từ i18n/<lang>.json;
// - chép static/ (CSS, ảnh, script, _headers) nguyên trạng;
// - tự host font Be Vietnam Pro và Redoc (không gọi CDN bên ngoài);
// - đọc đặc tả trong vobs-spec để sinh trang tài liệu API và bảng đối chiếu Thông tư 64,
//   nên website luôn khớp với đặc tả trong cùng commit;
// - dựng trang Hạ tầng từ data/infrastructure.json (sinh bằng scripts/sizing.py --json).
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
  infra: { template: "infrastructure.html", vi: "/ha-tang/", en: "/en/infrastructure/" },
  api: { template: "api.html", vi: "/api/", en: "/en/api/" },
  notFound: { template: "404.html", vi: "/404.html", en: "/en/404.html", noAlternates: true },
};
const NAV_PAGE = { tt64: "tt64", infra: "infra", api: "api" };

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
          <header><h3>${escapeHtml(m[lang].title)}</h3><span class="status status-${m.status}">${m[lang].label}</span></header>
          <code class="module-id">${escapeHtml(m.name)}</code>
          <p>${escapeHtml(m[lang].desc)}</p>
        </article>`)
    .join("\n");
}

// Số trên trang Hạ tầng: dấu phân cách theo ngôn ngữ (vi: 1.332.000 và 9,1; en: 1,332,000 và 9.1)
const numberFormat = (lang, digits = 0) =>
  new Intl.NumberFormat(lang === "vi" ? "vi-VN" : "en-US", { minimumFractionDigits: digits, maximumFractionDigits: digits });
const num = (value, lang, digits = 0) => numberFormat(lang, digits).format(value);
const thousandUsd = (value, lang) => (lang === "vi" ? `${num(value / 1000, lang)} nghìn USD` : `USD ${num(value / 1000, lang)}k`);

function infraValues(infra, t, lang) {
  // Cột đầu là tiêu đề hàng; textCols là các cột chữ, còn lại căn phải như cột số
  const row = (cells, textCols = []) =>
    `<tr>${cells.map((c, i) => (i === 0 ? `<th scope="row">${c}</th>` : `<td${textCols.includes(i) ? "" : ' class="num"'}>${c}</td>`)).join("")}</tr>`;
  const tiers = infra.tiers;
  const name = (item) => escapeHtml(item[lang]);
  const spec = (c) =>
    c ? `${c.replicas} × ${c.vcpu}/${c.ramGb} GB${c.diskGb ? `, ${num(c.diskGb, lang)} GB` : ""}` : "";
  const quote = escapeHtml(t("infra.quote"));

  const assumptions = tiers.map((x) =>
    row([`${x.id}`, name(x), `${num(x.peakTps, lang)} TPS`, num(x.callsPerDay, lang), num(x.partners, lang), num(x.customers, lang)], [1]));
  const sites = tiers.map((x) =>
    row([x.id, x.nodesApp, x.nodesData, `${x.vcpu} vCPU / ${num(x.ramGb, lang)} GB`, `${num(x.diskTb, lang, 1)} TB`,
      `${num(x.objectTb, lang, 1)} TB`, `${num(x.egressTb, lang, 1)} TB`]));
  const components = infra.components.map((c) => row([name(c), ...c.cells.map(spec)], [1, 2, 3]));
  const cloud = infra.clouds.map((c) =>
    row([name(c), ...tiers.flatMap((_, i) => [num(c.onDemand[i], lang), c.commit ? num(c.commit[i], lang) : quote])]));
  const onprem = tiers.map((x) => {
    const o = x.onprem;
    return row([x.id, `${o.prodServers} + ${o.nonprodServers}`, o.storageServers, o.racks, num(o.capex, lang),
      num(o.colo, lang), num(o.monthly, lang), num(o.tco, lang)]);
  });

  // Tổng chi phí 5 năm: thanh ngang theo tỷ lệ với phương án đắt nhất của cùng quy mô
  const options = [
    { label: escapeHtml(t("infra.tco.onprem")), values: tiers.map((x) => x.onprem.tco), onprem: true },
    ...infra.clouds.map((c) => ({ label: name(c), values: c.tco5y })),
  ];
  const max = tiers.map((_, i) => Math.max(...options.map((o) => o.values[i])));
  const tco = options.map((o) =>
    `<tr${o.onprem ? ' class="highlight"' : ""}><th scope="row">${o.label}</th>` +
      o.values.map((v, i) => `<td class="bar-cell"><span class="bar-value">${num(v / 1000, lang)}</span>` +
        // SVG thay cho style inline để giữ CSP style-src 'self'
        `<svg class="bar" viewBox="0 0 100 6" preserveAspectRatio="none" aria-hidden="true">` +
        `<rect width="${((v / max[i]) * 100).toFixed(1)}" height="6" rx="1"/></svg></td>`).join("") + "</tr>");

  const committed = infra.clouds.filter((c) => c.commit).map((c) => c.tco5y[0]);
  const smallOnprem = tiers[0].onprem.tco;
  const low = Math.min(smallOnprem, ...committed);
  const high = Math.max(smallOnprem, ...committed);
  return {
    "{{INFRA_AS_OF}}": lang === "vi" ? infra.asOf.split("-").reverse().join("/") : infra.asOf,
    "{{INFRA_USD_VND}}": num(infra.usdVnd, lang),
    "{{INFRA_ASSUMPTIONS}}": assumptions.join("\n"),
    "{{INFRA_SITES}}": sites.join("\n"),
    "{{INFRA_COMPONENTS}}": components.join("\n"),
    "{{INFRA_CLOUD}}": cloud.join("\n"),
    "{{INFRA_ONPREM}}": onprem.join("\n"),
    "{{INFRA_TCO}}": tco.join("\n"),
    "{{INFRA_TCO_ONPREM_M}}": thousandUsd(tiers[1].onprem.tco, lang),
    "{{INFRA_TCO_ONPREM_L}}": thousandUsd(tiers[2].onprem.tco, lang),
    "{{INFRA_TCO_S_RANGE}}": lang === "vi"
      ? `${num(low / 1000, lang)}–${num(high / 1000, lang)} nghìn USD`
      : `USD ${num(low / 1000, lang)}k–${num(high / 1000, lang)}k`,
  };
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
  const infra = await readJson(join(root, "data", "infrastructure.json"));
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
        ...infraValues(infra, t, lang),
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
