## 1. CSS Light Theme

- [x] 1.1 Thêm khối `[data-theme="light"]` trong styles.css override toàn bộ biến `:root` (--bg, --surface, --card, --text, --text-dim, --accent, --accent-light, --green, --yellow, --orange) sang palette sáng (nền trắng/xám nhạt, text tối, accent giữ tông đỏ hồng dễ đọc trên nền sáng)
- [x] 1.2 Rà soát styles.css: chuyển mọi màu hardcode (nếu có) thành biến CSS để tự theo theme

## 2. Theme Boot & Persistence

- [x] 2.1 Thêm inline script đầu `<head>` index.html: đọc `localStorage.theme`, set `document.documentElement.dataset.theme` trước khi app.js render (mặc định 'dark' khi chưa có giá trị)
- [x] 2.2 Trong app.js: hàm `setTheme(theme)` — set `data-theme`, lưu `localStorage.theme`, cập nhật `<meta name="theme-color">` tương ứng

## 3. Toggle trong Cài đặt

- [x] 3.1 Thêm icon-only button (SVG mặt trời/trăng, `stroke="currentColor"`, style secondary button) trong tab Cài đặt, label mô tả trạng thái hiện tại
- [x] 3.2 Bind sự kiện toggle: chuyển đổi dark ⇄ light, cập nhật icon + gọi `updateDisplay()` (nếu cần) để mọi thành phần render lại đúng theme

## 4. SVG Charts theme-aware

- [x] 4.1 Thay màu hardcode trong chart text (app.js: `#fff`, `#ccc`, `#aaa`, `#8899aa` ở stats chart, cutWindowSvg, weekendHotSvg) bằng `var(--text)` / `var(--text-dim)`
- [x] 4.2 Giữ nguyên màu accent `#ff6b81`/`#e94560`/`#f1c40f` (đủ tương phản cả 2 theme); audit lại grep hex toàn app.js sau khi sửa

## 5. Release

- [x] 5.1 Bump service-worker cache version `smoking-tracker-v<N>` (SW hiện tại đang v18 → v19)
- [x] 5.2 Update CHANGELOG.md (mục ✨ Mới: light mode)
- [x] 5.3 Verify local: mở file:// trong browser, test toggle cả 2 theme trên 4 tab, kiểm tra chart text đọc được trên nền sáng
- [x] 5.4 Commit (chỉ add các file thay đổi) + push `main` + verify live trên Vercel (curl grep mã mới + CACHE version mới)
