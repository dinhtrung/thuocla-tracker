# Changelog

Tất cả thay đổi đáng chú ý của **Thuốc Lá Tracker** sẽ được ghi ở đây.

## v1.4.9 — 2026-08-21
### ✨ Mới
- **Trang chủ: bảng So sánh thành 3 cột — Hôm nay | Ít nhất | Trung bình** — cột mới "🟡 Trung bình" chứa series 30 ngày theo từng điếu (STT): ⏰ giờ TB của điếu #i (cùng loại ngày với hôm nay, loại hôm nay khỏi TB) → xuống dòng → ⏱️ phút TB từ điếu trước ± phút mục tiêu (⚠️ −X = sớm hơn mục tiêu, ✅ +X = vượt mục tiêu). Header cột hiển thị TB điếu/ngày 30 ngày + TB khoảng cách
- **Bảng So sánh: cả 3 cột cùng layout 2 dòng** — ô Hôm nay và Ít nhất đổi sang giống cột Trung bình: dòng 1 ⏰ giờ (kèm icon lý do) → dòng 2 ⏱️ phút từ điếu trước ± lệch mục tiêu (⚠️ −X / ✅ +X); căn trái – giữa – phải cho 3 cột, bỏ số thứ tự # cho gọn

## v1.4.8 — 2026-08-20
### 🐛 Fixes
- **Cảnh báo sau +1 không còn che khung chọn lý do** — trước đây toast nổi (Chain-Smoke / Khung giờ đỉnh / Chậm lại) hiện giữa màn hình đè lên bottom-sheet "Lý do hút?". Giờ cả 3 cảnh báo hiện NGAY TRONG card số điếu (banner màu dưới progress bar), picker chọn lý do luôn nhìn rõ

## v1.4.7 — 2026-08-16
### 🐛 Fixes
- **Khen sai khi hút BẰNG nhịp mục tiêu** — trước đây điều kiện `todayCount <= targetCount` khiến lúc 08:00 thường ~2.3 điếu, nhịp mục tiêu ~2 mà hút đủ 2 vẫn khen "ít hơn 1 điếu". Giờ STRICT (`<`): chỉ khen khi hút ÍT HƠN nhịp mục tiêu (bạn mới 1 < 2 → ✅ "Hút ít hơn nhịp mục tiêu 1 điếu"); bằng chuẩn → không khen, chuyển sang note cắt/cảnh báo

## v1.4.6 — 2026-08-15
### ✨ Mới
- **Tab Insights: section debug "2 chuẩn khen/cảnh báo"** — hiển thị số liệu đang dùng cho note chính: chuẩn thói quen (TB điếu giờ này, 30 ngày), chuẩn nhịp mục tiêu (giới hạn điếu giờ này + số phút cần giãn/điếu), điếu đầu/cuối TB, biên độ, và verdict hiện tại (khen / hết hạn mức sớm / trên cả 2 chuẩn…) — để kiểm tra thuật toán dễ dàng

## v1.4.5 — 2026-08-15
### 🐛 Fixes
- **Không còn khen "thắng hôm nay" khi hút hết quota lúc chiều/tối sớm** — trước đây 17:40 đã 12/12 vẫn hiện 🎉 "Đạt mục tiêu — thắng hôm nay rồi!" dù còn cả buổi tối phía trước. Giờ: đạt đủ goal TRƯỚC giờ điếu cuối thường lệ (TB 30 ngày − 30ph) → ⚠️ "Hết hạn mức X điếu rồi — tối nay đừng hút nữa!"; chỉ 🎉 khen khi đã qua mốc đó (ngày gần kết thúc)
### ✨ Mới
- **So sánh 2 chuẩn để khen/cảnh báo** — chuẩn ① thói quen: TB điếu đã hút tới giờ này (30 ngày, hôm qua về trước); chuẩn ② nhịp mục tiêu: biên độ hút TB (điếu đầu→cuối) ÷ goal = số phút cần giãn/điếu để về đúng goal cuối ngày. Hút dưới CẢ 2 chuẩn → ✅ khen; hút TRÊN cả 2 → ⚠️ "Hút nhanh hơn cả thói quen lẫn nhịp mục tiêu — giãn ra! (cần ~Xph/điếu)"

## v1.4.4 — 2026-08-15
### ✨ Mới
- **Khen theo "ideal" đúng nghĩa** — ideal = trung bình số điếu đã hút tới giờ này, tính từ dữ liệu hôm qua về trước (tách cuối tuần/ngày thường). Hôm nay hút ít hơn STT của ideal → ✅ khen "Hút ít hơn nhịp thường ngày X điếu". Lời khen ưu tiên trước cảnh báo — lúc 08:00 mới 2/12 sẽ thấy khen (thường đã hút ~2.4 điếu) thay vì bị đe "cắt điếu #3"
- Khi hút NHIỀU hơn trung bình → không khen, quay lại cảnh báo điếu "hút theo" kế tiếp

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
