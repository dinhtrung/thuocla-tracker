# Changelog

Tất cả thay đổi đáng chú ý của **Thuốc Lá Tracker** sẽ được ghi ở đây.

## v1.4.3 — 2026-08-15
### ✨ Mới
- **Khen khi hút ít hơn nhịp thường ngày** — note so sánh số điếu hôm nay với "trường hợp lý tưởng" (số điếu thường đã hút tới giờ này theo lịch sử): hút ít hơn → ✅ "Hút ít hơn nhịp thường ngày X điếu — giỏi lắm!". Ví dụ: cắt điếu #3 lúc 08:07 xong, đến 09:00 thấy ngay lời khen.

### 🐛 Fixes
- Presence chuẩn hoá theo loại ngày (cuối tuần/ngày thường) — trước đây candidate cuối tuần bị loại nhầm vì chia cho tổng số ngày, làm mục tiêu cắt nhảy sai điếu

## v1.4.2 — 2026-08-14
### 🐛 Fixes
- **Note "Điếu nên cắt" không biến mất nữa** — trước đây khi hút dưới mục tiêu (ví dụ 9/10) và muộn giờ, panel ẩn hẳn. Giờ luôn hiển thị trạng thái động viên: "Còn X điếu trong hạn mức Y hôm nay — giữ nhịp nhé!"
- Chuẩn hoá mục tiêu ngày (parseInt) — tránh nhầm "Vượt mục tiêu" khi đạt đúng mục tiêu nếu config lưu dạng chuỗi

## v1.4.1 — 2026-08-14
### 🐛 Fixes
- **Note "Điếu nên cắt" giờ goal-aware** — trước đây note chỉ đúng khi hút nhiều hơn kế hoạch:
  - 🎉 Khen thưởng ngay khi đạt đúng mục tiêu ngày: "Đạt mục tiêu X điếu — thắng hôm nay rồi!"
  - ⚠️ Cảnh báo khi vượt mục tiêu: "Vượt mục tiêu X điếu (đang N) — dừng lại!"
  - Mục tiêu cắt bị giới hạn trong hạn mức ngày (không còn đề xuất "cắt điếu #17" khi mục tiêu 10)
  - Điếu mục tiêu chốt 1 lần/ngày và tự chuyển sang điếu kế tiếp khi đã hút; loại STT xuất hiện quá ít (1-off)

## v1.4.0 — 2026-08-14
### ✨ Mới
- **Note "Điếu nên cắt" trên trang chủ** — tự tính từ 30 ngày gần nhất (tách riêng cuối tuần/ngày thường) và nhắc nhở theo 3 trạng thái:
  - 🎯 **Mục tiêu sáng**: "Hôm nay cắt điếu #N ≈ HH:MM" (ưu tiên điếu hay hút theo/kép, né điếu neo cứng)
  - ⚠️ **Cảnh báo trước giờ dự đoán**: điếu kế tiếp là "điếu hút theo" (gap ≤ 40ph) → nhắc thử cắt
  - ✅ **Khen khi cắt thành công**: qua giờ dự đoán chưa hút → động viên giữ đà
- Note cập nhật live sau mỗi thao tác +1 / xoá điếu và mỗi phút; ẩn nếu chưa đủ 7 ngày dữ liệu

## v1.3.6 — 2026-08-13
### ✨ Mới
- **Light mode** — nút 🌓 trong Cài đặt chuyển đổi giữa theme sáng/tối (icon mặt trời/trăng, lưu lựa chọn, mặc định vẫn là theme tối)
- Thanh trạng thái điện thoại (theme-color) đổi theo theme đang dùng

### 🎨 UI
- Màu chữ trong biểu đồ (Thống kê, Cửa sổ cắt, Cuối tuần) chuyển sang biến theme — đọc được trên cả 2 nền

## v1.3.5 — 2026-08-09
### 🎨 UI
- Ghép nội dung 🌆 Cuối tuần vào cuối tab Thống kê (bỏ tab riêng — 5 tab bị tràn màn hình)
- Tab Cuối tuần render khi mở Thống kê

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
