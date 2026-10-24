// Đóng menu chọn ngôn ngữ khi bấm ra ngoài hoặc nhấn Esc. Menu vẫn dùng được khi không có JavaScript (thẻ <details>).
document.addEventListener("click", (event) => {
  for (const menu of document.querySelectorAll("details.lang[open]")) {
    if (!menu.contains(event.target)) menu.open = false;
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape") return;
  for (const menu of document.querySelectorAll("details.lang[open]")) {
    menu.open = false;
    menu.querySelector("summary")?.focus();
  }
});
