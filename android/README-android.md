# Ứng dụng Android — Thuốc Lá Tracker

App Android bọc bản web đóng băng (v1.5.2) trong `WebView`: chạy 100% offline, không xin quyền
`android.permission.INTERNET`, dữ liệu nằm trong Room/SQLite của app. APK cài trực tiếp
(sideload), không lên Google Play.

## Build

Yêu cầu: **JDK 17** (Java 25 có sẵn trên máy này nhưng sai cho AGP 8.7.3) và **Android SDK 35**.

```bash
# Thay hai đường dẫn dưới bằng vị trí thật trên máy bạn.
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
cd android
./gradlew :app:assembleDebug
```

APK debug ra ở `android/app/build/outputs/apk/debug/app-debug.apk`. Build lần đầu trên worktree
mới sẽ lâu hơn (Gradle daemon + tải dependency) — cứ để chạy.

Lần build đầu cần mạng để Gradle tải AGP/dependency vào Gradle home. Sau đó `--offline` chạy được
với cache sẵn có. Nếu **không có mạng** và Gradle home rỗng thì build hỏng ngay ở bước plugin
(`com.android.application` 8.7.3 không phân giải được) — lỗi này không liên quan đến chữ ký.

Kiểm thử: `./gradlew :app:testDebugUnitTest`.

## Release (ký bằng keystore local, không commit)

Keystore nằm **ngoài repo**, không bao giờ được commit:

```bash
mkdir -p ~/keystores
keytool -genkeypair -v -keystore "$HOME/keystores/thuocla-release.jks" \
  -alias thuocla -keyalg RSA -keysize 4096 -validity 10000
```

Đường dẫn và mật khẩu đọc từ **gradle properties** (file `~/.gradle/gradle.properties`, ngoài
repo), đúng bốn khoá sau:

```properties
# Thay /home/<bạn> bằng thư mục home thật (cùng chỗ với lệnh keytool ở trên);
# gradle properties không tự mở rộng dấu ~.
thuoclaStoreFile=/home/<bạn>/keystores/thuocla-release.jks
thuoclaStorePassword=<mật khẩu store>
thuoclaKeyAlias=thuocla
thuoclaKeyPassword=<mật khẩu key>
```

`android/app/build.gradle.kts` đọc chúng bằng `providers.gradleProperty(...)`. Thiếu bất kỳ khoá
nào thì build release vẫn cấu hình được và chỉ tạo APK **chưa ký** — nhờ vậy clone mới và CI build
được mà không cần bí mật (vẫn cần mạng cho lần tải dependency đầu tiên).

Build và copy APK ra ngoài repo:

```bash
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
cd android
./gradlew :app:assembleRelease
mkdir -p ~/thuocla-apk
# Đủ 4 khoá ký -> app-release.apk (đã ký); thiếu khoá -> app-release-unsigned.apk (chưa ký).
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || APK=app/build/outputs/apk/release/app-release-unsigned.apk
cp "$APK" ~/thuocla-apk/thuocla-v1.2.0.apk
```

`versionName` lấy từ `android/app/build.gradle.kts` (hiện là `1.2.0`, `versionCode` 3), nên tên file đổi theo phiên
bản. Kiểm tra chữ ký:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs ~/thuocla-apk/thuocla-v1.2.0.apk
```

## Cài & nhập bản sao

```bash
adb install -r ~/thuocla-apk/thuocla-v1.0.0.apk
```

Khôi phục dữ liệu từ PWA:

1. Trên PWA (v1.5.2) xuất bản sao JSON — file tên `thuocla_backup_YYYY-MM-DD.json`.
2. Chuyển file sang điện thoại (USB, Drive, chat…).
3. Mở app → tab **Dữ liệu** → **Nhập bản sao JSON** → chọn file.
4. Chọn **THAY THẾ** (xoá toàn bộ dữ liệu đang có rồi nhập) hoặc **GỘP** (chỉ thêm bản ghi chưa có).
   Trùng phút trong cùng ngày sẽ bị bỏ qua, không ghi đè.

Xuất ngược lại cũng ở tab **Dữ liệu**: **Xuất bản sao JSON** (đúng định dạng PWA) hoặc
**Xuất CSV (6 cột)**. Cả hai dùng Storage Access Framework, app không xin quyền bộ nhớ.

## Kiến trúc shell (từ 1.2.0)

Ứng dụng này **không vẽ lại giao diện**. Nó chạy chính bộ file của bản web bên trong một `WebView`,
nên giao diện khớp bản web theo cấu trúc chứ không phải theo lời hứa. Phần native chỉ giữ vòng đời và
dữ liệu.

- **Asset nằm trong APK.** `android/app/src/main/assets/web/` chứa bản sao **byte-identical** của
  `index.html`, `app.js`, `styles.css`, `manifest.json`, `icon-192.png`, `icon-512.png` ở repo root.
  `WebViewAssetLoader` phục vụ chúng dưới origin `https://appassets.androidplatform.net/`, nên
  `localStorage` hành xử như trên web thật mà **không cần mạng**: app không khai báo
  `android.permission.INTERNET`, và mọi điều hướng ra host khác bị chặn trong `shouldOverrideUrlLoading`.
  **Service worker thì không chạy.** `app.js:2163` có gọi `navigator.serviceWorker.register('service-worker.js')`,
  nhưng `service-worker.js` **không** nằm trong `assets/web/` (script verify chỉ canh 6 cặp file, không có nó),
  nên lời gọi đó nhận 404 và bị `.catch()` nuốt im lặng. Trong APK, cache do service worker quản lý **không tồn tại**;
  điều này không sao vì asset đã nằm sẵn trong APK và Room là nguồn sự thật. Đừng viết tài liệu hay test nào
  dựa vào việc service worker có mặt.
- **Room là nguồn sự thật.** Shim JS (`WebStoreShim`) chạy **trước** `app.js` (qua
  `WebViewCompat.addDocumentStartJavaScript`) và đổ dữ liệu Room vào `localStorage`; mọi thay đổi của
  web ghi ngược lại Room qua bridge allow-list (`WebAppBridge`, `WebFileBridge`). Vì vậy xoá storage
  của WebView **không** mất dữ liệu — lần mở sau Room lại đổ vào.
- **File đi qua native.** WebView không tự tải Blob và không mở được SAF, nên `exportCSV`,
  `exportBackup`, `pickRestoreFile` (và `shareBackup`) được shim chuyển sang native: SAF
  `CreateDocument`/`OpenDocument` và chooser `ACTION_SEND` qua `FileProvider`. Định dạng file và luật
  THAY THẾ/GỘP vẫn là của `DataPortability`, không có định dạng thứ hai.

### Sửa bản web thì sửa ở đâu

Nguồn duy nhất là các file ở **repo root**. **Không sửa tay** file trong `assets/web/` — chúng được
sinh ra và CI sẽ đỏ nếu lệch:

```bash
# từ repo root, sau khi sửa index.html/app.js/styles.css/...
bash scripts/verify-web-assets.sh --copy   # đồng bộ root -> assets/web/
bash scripts/verify-web-assets.sh          # kiểm lại: phải PASS 6/6
```

Script so `sha256sum` từng cặp và exit 1 khi thiếu file hoặc lệch, in tên cặp lệch. Job APK chạy nó
**trước** bước build, cho cả nhánh debug và release.

Compose UI đã bị **xoá** ở bản 1.2.0 (task 5.4): `MainActivity` và toàn bộ `ui/` không còn trong APK,
nên không có màn thứ hai để mở. Chỉ còn một activity là `web.WebShellActivity`.

## Sao lưu

App có **hai** đường sao lưu, không đường nào thay thế đường nào:

1. **Tự động của hệ điều hành** (bật từ bản 1.1.0). Manifest khai báo
   `android:allowBackup="true"`, `android:fullBackupContent="@xml/backup_rules"` (API 26–30) và
   `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+). Hai file luật **chỉ include**
   domain `database` + `file`, cho cả `cloud-backup` lẫn `device-transfer`, và **không có `<exclude>`**:
   `cache` và `no_backup` không phải domain hợp lệ của hai file này (AGP `lintVitalRelease` sẽ đỏ build
   release), mà hệ điều hành vốn đã tự loại `getCacheDir()`/`getNoBackupFilesDir()` khỏi bản sao lưu.
   Đừng thêm lại `<exclude domain="cache">`/`<exclude domain="no_backup">`. App **không** xin quyền nào
   cho việc này: hệ điều hành tự chạy.

> **Chia sẻ bản sao JSON** (có ở 1.1.0) **đã bị xoá ở 1.2.0**, cùng Compose UI (task 5.4): bản shell
> dùng giao diện web đóng băng v1.5.2, không có nút Chia sẻ, nên code native của nó (`BackupSharer`,
> FileProvider, `<queries>`, `file_paths.xml`) cũng đã bị xoá. Đây là quyết định có chủ ý của chủ sản
> phẩm (phương án b, 2026-11-04) và là regression đã biết so với 1.1.0.

2. **Xuất file qua SAF** (Storage Access Framework) — **Xuất bản sao JSON** hoặc **Xuất CSV (6 cột)**
   ở tab **Dữ liệu**, người dùng tự chọn chỗ cất. Đường này không xin quyền bộ nhớ.

Giới hạn thật của từng đường:

- Bản sao tự động của OS **chỉ về khi cài mới hoặc đổi máy**, và chỉ khi máy có bật sao lưu
  (tài khoản Google, hoặc chuyển máy trực tiếp bằng cáp). Nó không phải nút "khôi phục" trong app.
- Trên **cùng một máy**, bấm **Xoá dữ liệu** (hoặc `adb shell pm clear`) thì bản sao tự động
  **không** tự quay lại — chỉ `adb shell bmgr restore <token>` mới cứu được. Đây là hành vi của
  hệ điều hành, không phải của app.
- Đường **chia sẻ** không còn từ 1.2.0; ai cần bản sao rời máy thì dùng **Xuất bản sao JSON** ở đường 2.

## PWA đóng băng

PWA đã **đóng băng tính năng ở v1.5.2**; app Android này là hướng phát triển tiếp theo.
