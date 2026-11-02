## Why

APK hiện **không khai `android:icon`**: manifest chỉ có `android:label="Thuốc lá"`, và `aapt2 dump badging` trên bản build hôm nay in `application: label='Thuốc lá' icon=''`. Hệ quả là trên màn hình chính điện thoại app hiện icon robot mặc định của Android — người dùng không nhận ra app, và nó lạc hẳn khỏi bản sắc của PWA (PWA đã có icon riêng `icon-512.png` từ lâu).

Đây không phải việc thiết kế mới: app Android phải dùng **đúng** mark của PWA, không tự vẽ mark khác, để icon trên launcher và icon trên PWA không bao giờ lệch nhau.

## What Changes

- Thêm bộ asset đã sinh sẵn vào `design/app-icon/` (commit sẵn trong change này): 5 mật độ `mipmap-*/ic_launcher.png` + `ic_launcher_round.png`, 3 lớp adaptive (`ic_launcher_foreground/background/monochrome.png` ở 432×432), `play-store-512.png`, `preview.png` và `README.md` ghi provenance + cách wire.
- Nguồn: `smoking-tracker/icon-512.png` (mark đang ship của PWA), nền navy `#1a1a2e` — trùng đúng token `surface` của dark theme — được tách ra thành lớp background; mark còn lại thành lớp foreground.
- Wire vào app: copy mipmap vào `res/`, ba lớp vào `res/drawable-nodpi/`, thêm `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` có `<monochrome>` cho Android 13+.
- Khai `android:icon="@mipmap/ic_launcher"` và `android:roundIcon="@mipmap/ic_launcher_round"` trên `<application>`.
- **Không** đổi `android:label`, không thêm chữ vào icon, không đổi màu mark.

## Capabilities

### New Capabilities
- `app-icon`: icon launcher (adaptive + legacy + monochrome) khớp bản sắc PWA và được khai báo đúng trong manifest.

### Modified Capabilities
_(không sửa capability nào đang có)_
