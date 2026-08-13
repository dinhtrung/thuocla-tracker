## Context

App là PWA tĩnh (index.html + app.js + styles.css), toàn bộ màu dark theme nằm trong biến CSS `:root` (styles.css dòng 2-12). Phần lớn UI dùng biến nên việc đổi theme chỉ cần override biến. **Ngoại lệ đã audit (app.js):** biểu đồ SVG (stats chart, cutWindowSvg, weekendHotSvg) hardcode màu chữ `#fff`, `#ccc`, `#aaa`, `#8899aa` và màu accent `#ff6b81`/`#e94560`/`#f1c40f` trực tiếp trong thuộc tính `fill`/`stroke` — các màu chữ này sẽ gần như vô hình trên nền sáng. Xem proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Light theme hoạt động qua override biến CSS, không nhân đôi CSS cho từng component
- Áp dụng theme trước lần render đầu tiên (không flash)
- Mọi biểu đồ/chart đọc được trên cả 2 theme
- Toggle trong tab Cài đặt, lưu localStorage

**Non-Goals:**
- Không tự động theo hệ thống (`prefers-color-scheme`) ở bản này — chọn tay để không gây bất ngờ đổi giao diện
- Không đổi layout, không thêm tab mới
- Không thêm dependency

## Decisions

**D1: `[data-theme="light"]` trên `<html>`, override biến `:root`**
Override toàn bộ biến hiện có (--bg, --surface, --card, --text, --text-dim...) thành palette sáng. Chọn `data-theme` attribute thay vì class `.light` vì dễ scale khi sau này thêm theme khác.
*Alternative:* file CSS riêng `light.css` — bị loại vì nhân đôi maintenance, biến vẫn là cơ chế đúng.

**D2: Áp dụng theme bằng inline script trong `<head>` index.html**
Đọc `localStorage.theme` và set `data-theme` trước khi app.js render, tránh flash sai theme. Mặc định không có giá trị → dark.
*Alternative:* set trong app.js lúc khởi động — bị loại vì gây flash theme tối trên nền trắng.

**D3: SVG chart dùng `var(--text)` / `var(--text-dim)` thay màu hardcode**
Thuộc tính `fill="var(--text)"` hợp lệ trong SVG (CSS variables áp dụng được trong presentation attributes). Accent (`#ff6b81`/`#e94560`/`#f1c40f`) giữ nguyên — đủ tương phản trên cả 2 nền.
*Alternative:* vẽ lại chart bằng hàm lấy màu từ getComputedStyle — phức tạp hơn, không cần.

**D4: Toggle theme dạng icon-only button (SVG `currentColor`, mặt trời/trăng) trong tab Cài đặt**
Đúng phong cách UI đã chốt (icon-only, SVG stroke currentColor, secondary button style). Không thêm tab mới — cài đặt nằm trong tab Cài đặt sẵn có.

**D5: Lưu key `theme` riêng trong localStorage**
Không nhét vào `smoking_config` để tránh đụng shape config hiện có (đang được parse ở nhiều nơi). Giá trị: `'dark'` | `'light'`.

**D6: `manifest.json` + `<meta name="theme-color">`**
Giữ dark ở manifest (tĩnh). Thêm JS cập nhật `meta[name=theme-color]` khi đổi theme để thanh trạng thái mobile theo theme. Manifest static là chấp nhận được (PWA install snapshot).

## Risks / Trade-offs

- [Màu hardcode còn sót trong app.js không nằm trong audit] → Audit lại bằng grep `#[0-9a-fA-F]{3,6}` sau khi implement, test cả 2 theme trên từng tab
- [Flash sai theme nếu inline script chậm] → Script đặt ngay đầu `<head>`, chỉ đọc localStorage (đồng bộ, không await)
- [Bản cũ trên máy người dùng không cập nhật] → Bump SW cache version (cơ chế release bắt buộc của PWA này — cache-first)
- [Text trắng trong toast (#fff on gradient) đổi theme thành tối] → Toast nền gradient vàng giữ nguyên, chỉ đổi màu chữ qua biến nếu cần

## Migration Plan

1. Thêm inline script `<head>` + khối `[data-theme="light"]` trong styles.css
2. Sửa màu hardcode trong app.js (chart text → var())
3. Thêm nút toggle trong Cài đặt + logic localStorage
4. Bump SW cache `smoking-tracker-v<N>` → v19
5. Deploy: git commit + push `main` (Vercel auto-deploy ~5s), verify live bằng curl
6. Rollback: revert commit, push lại (SW cache bump tiếp đảm bảo máy người dùng nhận bản cũ)

## Open Questions

Không có — các quyết định đều đã chốt trong D1-D6.
