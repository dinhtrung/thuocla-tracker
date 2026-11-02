## 1. Asset (đã xong, commit kèm change này)

- [x] 1.1 Sinh bộ asset trong `design/app-icon/` từ `smoking-tracker/icon-512.png`: tách nền navy `#1a1a2e` khỏi mark, xuất 5 mật độ `ic_launcher.png` + `ic_launcher_round.png`, 3 lớp adaptive 432×432, `play-store-512.png`, `preview.png` (proof sheet 48/96/192 px dưới mask tròn, squircle, vuông).
- [x] 1.2 `design/app-icon/README.md` ghi provenance (file nguồn, hex nền) + đúng đoạn XML/`AndroidManifest` cần thêm, để seat không phải đoán.

## 2. Wire vào app

- [x] 2.1 Copy `design/app-icon/mipmap-{m,h,xh,xxh,xxx}dpi/` → `android/app/src/main/res/mipmap-*/` (giữ nguyên tên file `ic_launcher.png`, `ic_launcher_round.png`). Không resize lại, không nén lại.
- [x] 2.2 Copy `ic_launcher_foreground.png`, `ic_launcher_background.png`, `ic_launcher_monochrome.png` → `android/app/src/main/res/drawable-nodpi/`.
- [x] 2.3 Thêm `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` và `ic_launcher_round.xml` với `<background>`, `<foreground>`, `<monochrome>` trỏ đúng 3 drawable ở 2.2.
- [x] 2.4 `AndroidManifest.xml`: thêm `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"` trên `<application>`; **giữ nguyên** `android:label="Thuốc lá"` và mọi theme đang có.

## 3. Bằng chứng

- [x] 3.1 `./gradlew :app:assembleDebug :app:testDebugUnitTest` xanh; `aapt2 dump permissions` vẫn KHÔNG có `android.permission.INTERNET`.
- [x] 3.2 `aapt2 dump badging app-debug.apk | grep -E "^application:|application-icon-"`: `icon=` phải khác rỗng, có `application-icon-640` trở lên (adaptive) và `application-icon-160`.
- [ ] 3.3 Cài lên AVD (`thuocla-api35`, đã có sẵn trên homebox): `adb install -r`, mở launcher, chụp ảnh icon dưới mask tròn. Bật Themed icons (`adb shell cmd uimode ...` nếu bật được) và chụp trạng thái monochrome.
- [ ] 3.4 Dán output lệnh + ảnh vào PR body. Không sửa file nào ngoài `android/app/src/main/res/` và `AndroidManifest.xml`.

## 4. Không làm

- Không đổi label, không thêm chữ/số vào icon, không đổi màu mark, không thay ảnh PWA.
