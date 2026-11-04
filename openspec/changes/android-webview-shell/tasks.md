## 0. Luật chung

- Chỉ thêm/sửa trong `android/` và đúng một script trong `scripts/`. **Không sửa** `index.html`, `app.js`, `styles.css`, `service-worker.js` ở repo root — bản web là nguồn duy nhất của UI.
- Không thêm quyền: `INTERNET`, `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`. Gate INTERNET trong CI phải xanh.
- Không sửa tay file trong `android/app/src/main/assets/web/` — chạy `scripts/verify-web-assets.sh --copy` để đồng bộ từ root.
- Build gate: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest` (dán output thật vào PR).
- Ràng buộc không có trong spec (phải thêm dependency, phải đổi schema, WebView không chạy trên AVD): **dừng và báo trong PR body**, không xấp xỉ.

## 1. Asset bundle + chống lệch (task S1)

- [x] 1.1 `scripts/verify-web-assets.sh`: chế độ mặc định so `sha256sum` từng cặp `index.html`, `app.js`, `styles.css`, `manifest.json`, `icon-192.png`, `icon-512.png` giữa repo root và `android/app/src/main/assets/web/`; fail (exit 1) khi thiếu file hoặc lệch, in tên cặp lệch. Chế độ `--copy` copy từ root sang `assets/web/`.
- [x] 1.2 Chạy `--copy` để tạo `android/app/src/main/assets/web/` đủ 6 file; chạy lại chế độ verify → xanh.
- [x] 1.3 `.github/workflows/android-apk.yml`: thêm bước chạy `bash scripts/verify-web-assets.sh` **trước** bước build (cả nhánh debug và release), để asset lệch làm đỏ CI.
- [x] 1.4 Bằng chứng: dán output `sha256sum` 6 cặp + output script xanh; thử cố ý sửa 1 file trong `assets/web/` rồi chạy verify → phải fail (dán output fail đó), sau đó `--copy` lại cho sạch.

## 2. Web shell (task S2)

- [x] 2.1 Thêm dependency `androidx.webkit:webkit` vào `gradle/libs.versions.toml` + `app/build.gradle.kts` (cần cho `WebViewAssetLoader` và `addDocumentStartJavaScript`).
- [x] 2.2 `web/WebShellActivity.kt`: `WebView` + `WebViewAssetLoader` với `AssetsPathHandler("/assets/")`; load `https://appassets.androidplatform.net/assets/web/index.html`; bật `javaScriptEnabled`, `domStorageEnabled`; **không** bật `allowFileAccess`/`allowUniversalAccessFromFileURLs`; chặn điều hướng ra ngoài origin `appassets.androidplatform.net` (`shouldOverrideUrlLoading` → nuốt hoặc mở bằng intent ngoài, không load trong WebView).
- [x] 2.3 `AndroidManifest.xml`: activity `WebShellActivity` với `MAIN`/`LAUNCHER`, `android:configChanges` hợp lý, `android:exported="true"`. `MainActivity` (Compose cũ) **giữ nguyên code**, chỉ bỏ `MAIN`/`LAUNCHER` → mở được bằng intent tường minh trong lúc chuyển tiếp.
- [x] 2.4 Back button: `OnBackPressedCallback` — web có `history.length > 1` thì `goBack()`, ngược lại thoát app; không để back trắng màn hình.
- [x] 2.5 Bằng chứng tĩnh: `aapt2 dump badging` → launcher là `vn.dinhtrung.thuocla/.web.WebShellActivity`; `aapt2 dump permissions` không có INTERNET; `unzip -l app-debug.apk | grep assets/web` liệt kê đủ 6 file.

## 3. Cầu lưu trữ — Room là nguồn sự thật (task S3)

- [x] 3.1 `web/WebStoreMirror.kt`: đọc toàn bộ Room (records theo ngày + config) và serialize đúng shape mà `app.js` đang dùng (`smoking_data` map ngày → mảng record, `smoking_config` object) — **đọc `app.js` để lấy đúng key và shape, không đoán**; ghi lại vào Room theo từng lần web thay đổi.
- [x] 3.2 Shim JS tiêm bằng `WebViewCompat.addDocumentStartJavaScript`: đổ dữ liệu mirror vào `localStorage` **trước** khi `app.js` chạy; bọc `setItem`/`removeItem`/`clear` để chuyển tiếp sang `WebStoreMirror` (hàng đợi tuần tự, lỗi ghi Room thì log + không làm hỏng UI).
- [x] 3.3 `web/WebAppBridge.kt` với `@JavascriptInterface`, danh sách trắng method: `readAll`, `writeDay`, `writeConfig`, `deleteLast`, và các method file ở task S4. Không nhận SQL, không nhận đường dẫn tuỳ ý.
- [x] 3.4 Test JVM: (a) Room có 5 điếu → mirror sinh JSON đúng shape, parse lại ra 5 điếu; (b) ghi qua bridge 1 điếu → Room tăng đúng 1 và không tạo bản ghi trùng phút; (c) config đổi → `smoking_config` phản ánh đúng 4 khoá.
- [x] 3.5 Bằng chứng sống trên AVD: dữ liệu đang có trong Room (cài bản 1.1.0 trước, ghi vài điếu) → cài bản shell → **mở app thấy đúng số điếu cũ**; sau đó `adb shell pm clear vn.dinhtrung.thuocla` là ca âm (mất cả Room — dùng để phân biệt), còn ca "xoá WebView storage" thì dùng `adb shell run-as vn.dinhtrung.thuocla rm -rf app_webview` rồi mở lại → dữ liệu vẫn còn.

## 4. Cầu file — xuất/nhập/chia sẻ từ shell (task S4)

- [x] 4.1 Shim override đúng các hàm toàn cục mà UI web gọi: `exportCSV()` (`app.js:1398`), `exportBackup()` (`:1492`), `pickRestoreFile()` (`:1546`) → gọi bridge thay vì Blob/download. **Lưu ý:** bản web **không có** nhập CSV (chỉ có `restoreFromFile` cho JSON, `:1551`) — không tự thêm nút mới, phạm vi shell là ba hành động trên + Chia sẻ.
- [x] 4.2 Native: mở SAF `CreateDocument`/`OpenDocument` và chooser `ACTION_SEND` từ `WebShellActivity` (dùng lại `DataPortability`, `FileProvider` và `file_paths.xml` đã có ở task #34), trả kết quả về JS để UI hiện thông báo như bản web.
- [x] 4.3 Giữ nguyên định dạng và luật nhập: không đổi `CSV_HEADER`, không đổi schema JSON, không đổi luật THAY THẾ/GỘP.
- [x] 4.4 Bằng chứng sống: xuất CSV từ shell → mở file, đúng 6 cột; nhập backup JSON → số điếu đổi đúng; bấm Chia sẻ → chooser mở, chọn được app nhận. Dán ảnh/log từng bước.

## 5. Nghiệm thu offline + dọn Compose (task S5, có cổng)

- [x] 5.1 Bằng chứng offline (bắt buộc): bật máy bay **trước** khi cài → cài APK → mở app (đủ tab, 0 điếu) → +1 → `am force-stop` → mở lại (còn 1 điếu); dán output `adb shell settings get global airplane_mode_on`, `logcat -d | grep -c "appassets"`, và xác nhận không có request mạng nào. **Đạt trên emulator-5554 (API 35), APK head `feature/40-s5-acceptance`: 0→+1→force-stop→mở lại = 1 điếu (Room + UI). `appassets` = 0 dòng log.** ⚠️ **`airplane_mode_on=1` một mình KHÔNG cắt mạng trên emulator này:** reviewer đo được settings=1 mà `dumpsys connectivity` vẫn `Active default network: 106` và ping 8.8.8.8 vẫn thông (ổn định 18s). Phải cắt mạng thật bằng `adb shell svc wifi disable; adb shell svc data disable`, rồi mới xác nhận `Active default network: none` + ping `Network is unreachable`. Người sau chép đúng thứ tự này, nếu không sẽ ghi một lần "offline" không hề offline. `logcat | grep -c appassets` luôn ra 0 (không có gì log tag đó) nên nó không tự chứng minh gì — xem bằng chứng thay thế trong PR #49.
- [x] 5.2 Đo cold start (3 lần) và RAM: ghi số thật vào PR, so với bản Compose nếu còn cài được. **Shell 597/619/622 ms, ~100 MB PSS; Compose `MainActivity` 887/907/913 ms, ~72 MB PSS — cùng APK, cùng máy.**
- [x] 5.3 `android/README-android.md`: giải thích kiến trúc shell (asset trong APK, Room là nguồn sự thật, bridge file), cách chạy `verify-web-assets.sh` sau khi sửa web, và ràng buộc "không sửa tay file trong assets/". **Thêm mục "Kiến trúc shell (từ 1.2.0)".**
- [ ] 5.4 **Chỉ sau khi Trung xác nhận trên máy thật**: xoá Compose UI (`TodayScreen`, `StatsScreen`, `SettingsScreen`, `DataScreen`) và các ViewModel UI tương ứng; giữ `data/`, `domain/` và `Theme`/`Color` nếu còn dùng. Xoá xong build vẫn xanh và test không mất coverage của logic đã có.
- [ ] 5.5 [orchestrator] Bump `versionName "1.2.0"`, `versionCode 3` sau khi mọi PR merge; tag `android-v1.2.0` → CI ký + đính APK; cài đè lên bản 1.1.0, dữ liệu phải còn nguyên.
