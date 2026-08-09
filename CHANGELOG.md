# Changelog

Tất cả thay đổi đáng chú ý của **Thuốc Lá Tracker** sẽ được ghi ở đây.

## v1.3.4 — 2026-08-09
### ✨ Mới
- Tab 🌆 Cuối tuần: so sánh TB điếu cuối tuần vs ngày thường, gap, chuỗi hút theo (30 ngày qua)
- Biểu đồ giờ nóng cuối tuần (top 3 giờ tô đỏ)
- Thuật toán tự đề xuất lịch cuối tuần: mục tiêu điếu, gap mục tiêu, giờ điếu đầu/cuối, khung giờ lấp bằng hoạt động
- Trạng thái hôm nay: nhận biết cuối tuần và hiện mục tiêu riêng

## v1.3.3 — 2026-08-06

### 🎨 UI
- **Nút "Thêm điếu với giờ cụ thể" (trang Thống kê → chi tiết ngày)**: sửa format cho khớp nút "Thêm điếu quên note" trên trang chủ — bỏ nền gradient, dùng style secondary (nền mờ, chữ nhạt), icon đồng hồ 20×20.

## v1.3.2 — 2026-08-05

### 🎨 UI
- **Đồng hồ bấm giờ**: bên phải mirror layout bên trái — icon to hơn (28px) + label "Dự kiến" trên, giờ:phút màu xanh to (24px) giữa, "còn xx phút" dưới (bỏ ngoặc). Bỏ vòng tròn số phút. Khi đạt mục tiêu: ✅ "Đạt mục tiêu" + "+X phút" vượt mục tiêu. Không đặt mục tiêu thì ẩn bên phải.

## v1.3.1 — 2026-08-05

### 🐛 Fixes
- **Auto-update hoạt động thật sự** — service worker trước đây **chưa bao giờ được đăng ký** (các lần bump cache không tới được máy). Giờ app tự đăng ký SW và **tự reload khi có bản mới** — không cần refresh tay.
- **Icon lý do hiện ngay** — chọn lý do trong popup sau khi +1 → icon (🍜☕😤…) hiện tức thì trong timeline, không phải refresh lại.

### 🔧 Khác
- Bump service worker cache → v12.

## v1.3.0 — 2026-08-04

### ✨ Mới
- **Insights: biểu đồ "✂️ Cửa sổ cắt"** — thống kê điếu hút cách điếu trước ≤40 phút theo từng khung giờ (SVG 24h, highlight top 3 khung giờ).
- **4 thẻ thống kê**: trung bình điếu cắt được/ngày, điếu đôi (≤20ph), khung giờ nóng nhất, tiền tiết kiệm/tháng.

### 🧹 Dọn dẹp / UI
- Bỏ hoàn toàn nút ½ (điếu nửa) — không ai hút nửa điếu.
- Nút "Thêm điếu quên note" → icon clock+ (home + thống kê), bỏ chữ.

## v1.2.0 — 2026-07-31

### ✨ Mới
- Icon lý do (trigger) hiện trong timeline so sánh.
- Chọn / sửa lý do hút trong modal sửa giờ.

### 🎨 UI
- Nút +1 làm tròn phút theo bội số 5 (0, 5, 10…) — khớp với time picker.
- Gộp hàng nút +1 / ⟲ / thêm giờ thành một hàng gọn.

## v1.1.0 — 2026-07-30

### ✨ Mới
- Tách code thành 3 file (index.html / styles.css / app.js).
- **Alert chain-smoke** — cảnh báo đỏ khi hút ≤30 phút sau điếu trước.
- **Cảnh báo khung giờ đỉnh** — vàng 6h–12h.
- **Cảnh báo chậm lại** — 3 điếu trong 2h.
- **Chọn lý do hút** (8 trigger: 🍜 Sau ăn, ☕ Cà phê, 😤 Stress, 🍻 Nhậu, 😞 Buồn, 🌀 Thói quen, 🚬 Thèm, 🤷 Khác) sau khi +1.
- **Tab Insights**: so sánh tuần, phân tích giấc ngủ, thử thách.

## v1.0.2 — 2026-07-30

### 🎨 UI
- Biểu đồ thống kê: số to hơn, hiện khoảng cách trung bình (phút) dưới mỗi cột.

## v1.0.1 — 2026-07-16

### 📝 Khác
- Cập nhật ảnh chụp màn hình + README.

## v1.0.0

- Phiên bản đầu tiên 🎉
