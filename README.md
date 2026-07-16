# 🚬 Thuốc Lá Tracker

PWA đếm số điếu thuốc đã hút trong ngày — nhẹ, nhanh, miễn phí, không cần cài app.

## 📸 Giao diện

| Trang chính | Thống kê | Cài đặt |
|:---:|:---:|:---:|
| ![Main](screenshots/main.png) | ![Stats](screenshots/stats.png) | ![Settings](screenshots/settings.png) |

## ✨ Tính năng

- **➕ Đếm +1 🚬** và **½** — bấm nhanh, haptic feedback
- **↩️ Undo** — xoá điếu vừa bấm nhầm
- **⏪ Thêm điếu quên note** — quên hồi sáng? chọn giờ cũ + thêm
- **✏️ Sửa / xoá từng điếu** — chạm vào dòng so sánh
- **🎯 Mục tiêu ngày** — thanh progress báo % hoàn thành
- **⏱️ Timer từ điếu cuối** — real-time, biết đã được bao lâu
- **⏱️ Mục tiêu khoảng cách** — cảnh báo nếu hút gần nhau quá
- **📋 So sánh song song** — giờ hút hôm nay vs ngày ít nhất
- **💰 Tính tiền tự động** — nhập giá bao, tính hôm nay/tuần/tháng
- **📊 Biểu đồ SVG 7 ngày** — chuẩn pixel, có 🎯 đường mục tiêu
- **📅 Lật tuần** — ‹ › xem các tuần trước
- **📋 Xem chi tiết ngày** — bấm vào cột biểu đồ, hiện từng điếu
- **📤 Xuất CSV** — tải về làm báo cáo
- **📱 PWA** — Add to Home Screen, xài offline

## 🚀 Dùng thử

Mở [thuocla-tracker.vercel.app](https://thuocla-tracker.vercel.app)
- iOS: Safari → Share → **Add to Home Screen**
- Android: Chrome → **⋮ → Install app / Add to Home Screen**

## 🛠 Dev

```bash
git clone https://github.com/dinhtrung/thuocla-tracker.git
cd thuocla-tracker
python3 -m http.server 8080
# Mở http://localhost:8080
```

## 📦 Tech stack

- HTML + CSS + Vanilla JS (PWA)
- localStorage — không cần server, không đăng nhập
- SVG — biểu đồ chính xác, responsive
- Vercel — deploy tự động từ GitHub
