// Khởi tạo Redoc với đặc tả dựng sẵn ở /api/vobs-open-api.json
Redoc.init("/api/vobs-open-api.json", {
  hideDownloadButton: true,
  expandResponses: "200,201,202",
  pathInMiddlePanel: true,
  scrollYOffset: ".site-header",
  jsonSampleExpandLevel: 3,
  theme: {
    colors: { primary: { main: "#121317" } },
    typography: {
      fontFamily: "'Be Vietnam Pro', system-ui, sans-serif",
      headings: { fontFamily: "'Be Vietnam Pro', system-ui, sans-serif", fontWeight: "700" },
      code: { fontFamily: "ui-monospace, 'SF Mono', Menlo, Consolas, monospace" }
    },
    sidebar: { width: "280px" }
  }
}, document.getElementById("redoc"));
