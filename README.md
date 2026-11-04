# 🚬 Thuốc Lá Tracker

PWA đếm số điếu thuốc đã hút trong ngày — nhẹ, nhanh, miễn phí, không cần cài app. *[Changelog](CHANGELOG.md) • v1.5.2*

## 📸 Giao diện

| Trang chính | Thống kê | Cài đặt |
|:---:|:---:|:---:|
| ![Main](screenshots/main.png) | ![Stats](screenshots/stats.png) | ![Settings](screenshots/settings.png) |

## ✨ Tính năng

- **➕ Đếm +1 🚬** — bấm nhanh, haptic feedback
- **💡 Chọn lý do hút** — popup sau +1 (🍜 Sau ăn, ☕ Cà phê, 😤 Stress, 🍻 Nhậu…), icon hiện ngay trong timeline
- **↩️ Undo** — xoá điếu vừa bấm nhầm
- **⏪ Thêm điếu quên note** — quên hồi sáng? chọn giờ cũ + thêm
- **✏️ Sửa / xoá từng điếu** — chạm vào dòng so sánh
- **🎯 Mục tiêu ngày** — thanh progress báo % hoàn thành
- **⏱️ Timer từ điếu cuối** — real-time, biết đã được bao lâu
- **⏱️ Mục tiêu khoảng cách** — cảnh báo nếu hút gần nhau quá
- **🚨 Alert thông minh** — chain-smoke (≤30ph), khung giờ đỉnh, hút nhanh liên tiếp
- **📋 So sánh song song** — giờ hút hôm nay vs ngày ít nhất
- **💰 Tính tiền tự động** — nhập giá bao, tính hôm nay/tuần/tháng
- **📊 Biểu đồ SVG 7 ngày** — chuẩn pixel, có 🎯 đường mục tiêu
- **📅 Lật tuần** — ‹ › xem các tuần trước
- **📋 Xem chi tiết ngày** — bấm vào cột biểu đồ, hiện từng điếu
- **📊 Tab Insights** — so sánh tuần, phân tích giấc ngủ, thử thách, biểu đồ "✂️ Cửa sổ cắt"
- **📤 Xuất CSV** — tải về làm báo cáo
- **📱 PWA** — Add to Home Screen, xài offline, **tự cập nhật** khi có bản mới (không cần refresh tay)

## 🚀 Dùng thử

Mở [thuocla-tracker.vercel.app](https://thuocla-tracker.vercel.app)
- iOS: Safari → Share → **Add to Home Screen**
- Android: Chrome → **⋮ → Install app / Add to Home Screen**

## 📱 Bản Android (APK)

Cài trực tiếp, không cần Google Play, **chạy 100% offline** — APK không xin quyền `INTERNET`.

1. Tải APK: [**Releases → `thuocla-v1.2.0.apk`**](https://github.com/dinhtrung/thuocla-tracker/releases/latest)
2. Mở file vừa tải trên máy, cho phép *Cài từ nguồn không xác định* khi Android hỏi
3. Cài đè lên bản cũ được luôn — **dữ liệu còn nguyên** (cùng `applicationId`, cùng khoá ký)

| | Bản web (PWA) | Bản Android (APK) |
|---|---|---|
| Cài đặt | Add to Home Screen | Sideload file APK |
| Offline | Service worker | Toàn bộ UI nhúng sẵn trong APK |
| Dữ liệu | `localStorage` của trình duyệt | Room/SQLite của app + auto-backup của Android |
| Xuất CSV / xuất-nhập bản sao JSON | ✅ | ✅ (từ 1.2.0) |
| Chia sẻ bản sao JSON | ❌ | Chỉ bản 1.1.0; **1.2.0 bỏ** — xem [android/README-android.md](android/README-android.md) |
| Quyền xin cấp | — | Không có quyền mạng nào |

Yêu cầu: Android 8.0+ (API 26). APK release do CI build và **ký bằng khoá của bản đang cài** khi
push tag `android-v*`; đổi UI app thì phải sửa bản web rồi sync asset (xem
[android/README-android.md](android/README-android.md)).

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
- Service Worker — offline + auto-update
- Vercel — deploy tự động từ GitHub
