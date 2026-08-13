## Why

App hiện chỉ có theme tối (`#1a1a2e` background). Dùng ngoài trời ban ngày khó đọc, gây mỏi mắt. Thêm light mode để thoải mái hơn khi dùng ban ngày, vẫn giữ dark làm mặc định.

## What Changes

- Thêm bộ biến CSS light theme (override toàn bộ biến `:root` hiện tại qua `[data-theme="light"]`)
- Thêm nút chuyển đổi theme trong tab **Cài đặt** (icon mặt trời/trăng, theo phong cách icon-only button của app)
- Lưu lựa chọn theme vào localStorage (key `theme`), áp dụng ngay khi tải trang (tránh flash sai theme)
- Mặc định giữ nguyên **dark** cho người dùng cũ — không đổi hành vi hiện tại
- Bump service-worker cache version để bản mới tới được máy người dùng

## Capabilities

### New Capabilities
- `theme`: Khả năng chọn và lưu theme sáng/tối của app, áp dụng nhất quán trên mọi màn hình (home, thống kê, insights, cài đặt)

### Modified Capabilities
<!-- Không có spec cũ — change đầu tiên của repo -->

## Impact

- `styles.css`: thêm khối `[data-theme="light"]` override biến CSS; đảm bảo mọi component dùng biến (không hardcode màu) nên tự chuyển theme
- `index.html`: thêm nút toggle theme trong tab Cài đặt
- `app.js`: đọc/ghi theme trong localStorage, áp dụng trước khi render, bind sự kiện nút toggle
- `service-worker.js`: bump `CACHE` version (cơ chế release của PWA — không bump thì máy người dùng không bao giờ thấy bản mới)
