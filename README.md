# 🚬 Thuốc Lá Tracker

PWA đếm số điếu thuốc đã hút trong ngày — nhẹ, nhanh, không cần cài app.

## Tính năng

- ➕ **Đếm +1 / +2 / +3 / +5** — bấm nhanh, haptic feedback
- ½ **Nửa điếu** — hút dở cũng note được
- ↩️ **Undo** — xoá điếu vừa bấm nhầm
- ⏪ **Thêm điếu quên note** — quên hồi sáng? chọn giờ cũ + thêm
- ✏️ **Sửa / xoá từng điếu** — chạm vào timeline
- 🎯 **Mục tiêu ngày** — thanh progress báo % hoàn thành
- ⏱️ **Timer từ điếu cuối** — real-time, biết đã được bao lâu
- ⏱️ **Mục tiêu khoảng cách** — cảnh báo nếu hút gần nhau quá
- 💰 **Tính tiền tự động** — nhập giá bao, tính hôm nay/tuần/tháng
- 📊 **Biểu đồ 7 ngày** + timeline chi tiết
- 📤 **Xuất CSV** — tải về làm báo cáo
- 📱 **PWA** — Add to Home Screen, xài offline

## Cài đặt

1. Mở https://thuocla.dinhtrung.dev (hoặc dùng localtunnel)
2. Trên iOS: Safari → Share → Add to Home Screen
3. Trên Android: Chrome → ⋮ → Install app / Add to Home Screen

## Dev

```bash
git clone https://github.com/dinhtrung/thuocla-tracker.git
cd thuocla-tracker
python3 -m http.server 8080
# Mở http://localhost:8080
```
