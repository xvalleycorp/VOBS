// Giao diện sáng/tối. Mặc định theo hệ điều hành; khi người xem bấm nút ở header, lựa chọn được lưu lại
// và đặt vào thuộc tính data-theme của <html>. File này nạp trong <head>, không defer, để áp màu trước khi vẽ trang.
(() => {
  const KEY = "vobs-theme";
  const root = document.documentElement;
  const systemDark = window.matchMedia("(prefers-color-scheme: dark)");

  try {
    const saved = localStorage.getItem(KEY);
    if (saved === "light" || saved === "dark") root.dataset.theme = saved;
  } catch {
    // Trình duyệt chặn localStorage: vẫn theo hệ điều hành
  }

  const isDark = () => (root.dataset.theme ? root.dataset.theme === "dark" : systemDark.matches);

  document.addEventListener("DOMContentLoaded", () => {
    const button = document.querySelector(".theme-toggle");
    if (!button) return;
    const sync = () => button.setAttribute("aria-pressed", String(isDark()));
    sync();
    systemDark.addEventListener("change", sync);
    button.addEventListener("click", () => {
      const next = isDark() ? "light" : "dark";
      root.dataset.theme = next;
      try {
        localStorage.setItem(KEY, next);
      } catch {
        // Không lưu được thì lựa chọn chỉ giữ trong trang hiện tại
      }
      sync();
    });
  });
})();
