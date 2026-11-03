## Why

Người dùng cần app **dùng được khi không có mạng** (đi leo núi nhiều ngày, không sóng) — đó là lý do bản port native tồn tại. Nhưng yêu cầu thứ hai, giao diện **khớp bản web**, thì bản Compose hiện tại không đáp được: UI đã được **viết lại** từ spec chứ không port từ CSS, nên khớp hành vi mà lệch pixel (gradient, bo góc, font, chart SVG, animation). Viết lại Compose để đuổi kịp `styles.css` là đường không có điểm kết thúc, và mỗi lần sửa web phải sửa hai nơi.

Bản web lại **không có phụ thuộc mạng nào** — đã kiểm tra trên `main`:

- quét `index.html`, `app.js`, `styles.css`: **0 URL ngoài** (không CDN, không Google Fonts, không thư viện chart online);
- **0** `fetch(`, `XMLHttpRequest`, `navigator.geolocation` trong toàn bộ `app.js`;
- tổng toàn bộ app: **192 KB** (`index.html` 24K, `app.js` 100K, `styles.css` 20K, 2 icon), service worker precache đủ `index.html`/`styles.css`/`app.js`/`manifest.json`.

Chính vì vậy, nhúng **nguyên** các file đó vào APK và render bằng `WebView` + `WebViewAssetLoader` cho ra cả hai thứ cùng lúc: giao diện khớp 100% theo cấu trúc (cùng file), và offline thật ngay từ lần mở đầu tiên (file đọc từ APK, không request nào). Đây là phương án **A+**: web shell cho UI, native cho vòng đời và dữ liệu — Room vẫn là nguồn sự thật, backup tự động của OS (change `android-backup-and-share`) vẫn hoạt động vì DB là của app, và đường xuất/nhập/chia sẻ vẫn đi qua SAF/`FileProvider` như đã đặc tả.

Điểm phải nói thẳng: sau change này, bản Compose UI (10 file, 2512 dòng) **trở thành code chết** và sẽ bị xoá ở task cuối — nhưng chỉ sau khi shell đã được chứng minh trên AVD ở chế độ máy bay **và** trên máy thật. Không xoá trước khi có bằng chứng.

## What Changes

- **Asset bundle trong APK:** `android/app/src/main/assets/web/` chứa bản sao của `index.html`, `app.js`, `styles.css`, `manifest.json`, `icon-192.png`, `icon-512.png`. Có script kiểm tra **byte-identical** với file gốc ở repo root để không bao giờ lệch (không sửa tay bản trong `assets/`).
- **Web shell:** một `WebView` mới (`WebShellActivity`) trở thành launcher; asset phục vụ qua `WebViewAssetLoader` dưới origin `https://appassets.androidplatform.net/assets/` để `localStorage` hoạt động bình thường; cấm `loadUrl` ra internet, cấm `setAllowFileAccess` bừa; back button điều hướng trong web trước khi thoát.
- **Cầu lưu trữ (native là nguồn sự thật):** trước khi web khởi động, native đẩy dữ liệu từ Room vào `localStorage` (mirror); mọi lần web ghi thì ghi tiếp vào Room qua `addJavascriptInterface`. Nhờ vậy web không phải sửa, mà dữ liệu vẫn sống trong SQLite của app — clear WebView data hay đổi WebView provider cũng không mất.
- **Cầu file:** xuất/nhập CSV, xuất/nhập JSON và chia sẻ bản sao trong shell đi qua native (`DataPortability` + SAF + `FileProvider` đã có), không dựa vào `a[download]`/Blob (WebView không tự tải file).
- **Xoá dần Compose UI:** sau khi shell được nghiệm thu trên máy thật mới xoá `TodayScreen`/`StatsScreen`/`SettingsScreen`/`DataScreen` và các ViewModel tương ứng; giữ `Theme`/`Color` nếu còn dùng cho splash/thanh trạng thái.
- **Phát hành:** `versionName 1.2.0`, `versionCode 3`; tag `android-v1.2.0` sinh APK qua CI như hiện nay.

## Capabilities

### New Capabilities

- `web-shell`: app render đúng bộ file của bản web, chạy được offline ngay lần đầu, không request mạng, giữ dữ liệu bền qua Room và trao đổi file qua SAF/`FileProvider`.

### Modified Capabilities

Không có. Yêu cầu "xuất/nhập/chia sẻ vẫn dùng được" được đặc tả ngay trong capability `web-shell`; định dạng file, luật nhập và toàn bộ hành vi của `data-portability` **không đổi**.

## Impact

- File mới: `android/app/src/main/assets/web/*` (bản sao 6 file web), `android/app/src/main/java/vn/dinhtrung/thuocla/web/WebShellActivity.kt`, `.../web/WebAppBridge.kt`, `.../web/WebStoreMirror.kt`, `android/app/src/main/res/xml/network_security_config.xml` (nếu cần chặn cleartext), `scripts/verify-web-assets.sh`.
- File sửa: `AndroidManifest.xml` (thêm activity shell, chuyển `MAIN`/`LAUNCHER`), `android/app/build.gradle.kts` (version), `android/README-android.md`, `.github/workflows/android-apk.yml` (bước verify asset khớp).
- File **không** sửa: `index.html`, `app.js`, `styles.css`, `service-worker.js` ở repo root — bản web vẫn là nguồn duy nhất của UI, PWA trên Vercel không đổi.
- Bị xoá (task cuối, sau nghiệm thu): Compose UI + ViewModel của 4 màn.
- Ràng buộc giữ nguyên: manifest không có `android.permission.INTERNET`; gate INTERNET trong CI vẫn phải xanh.
