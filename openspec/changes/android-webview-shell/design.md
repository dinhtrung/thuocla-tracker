# Design — web shell cho Android (UI khớp bản web, offline thật)

## Bối cảnh đã đo (trên `main`)

- Bản web self-contained: 0 URL ngoài, 0 `fetch`/`XHR`/`geolocation`; tổng 192 KB; SW precache 4 file.
- Bản Compose: 2512 dòng UI trên 10 file; tổng `android/` 5080 dòng. UI được viết lại từ spec, không port từ CSS → lệch pixel.
- `MainActivity.kt` hiện là `ComponentActivity` + Compose; `DataPortability` đã có sẵn toàn bộ logic xuất/nhập; #34 thêm `FileProvider`, #33 thêm luật backup.

## Quyết định

### D1. Nhúng asset + `WebViewAssetLoader`, tuyệt đối không `loadUrl` ra internet

- Asset ở `assets/web/`, phục vụ qua `WebViewAssetLoader` dưới `https://appassets.androidplatform.net/assets/web/index.html`.
- Origin `https` hợp lệ ⇒ `localStorage`, `IndexedDB`, service worker đều hoạt động như trên web thật.
- Loại: `loadUrl("https://thuocla-tracker.vercel.app")` — offline là trắng trang trừ khi SW đã warm; đúng ca "lần đầu mở app trên núi".
- Loại: `file://` — origin opaque, `localStorage` hành xử khác giữa các bản WebView, và phải bật `allowFileAccessFromFileURLs` (bề mặt tấn công).
- Loại: local HTTP server trong app — thêm vòng đời, thêm cổng, thêm chỗ hỏng, không đổi lại được gì.

### D2. Không fork bản web; chống lệch bằng script

`assets/web/*` là **bản sao byte-identical** của file ở repo root. `scripts/verify-web-assets.sh` so `sha256sum` từng cặp và fail nếu lệch; CI chạy script này trong job APK. Không ai được sửa tay file trong `assets/` — sửa ở root rồi chạy script copy. Nhờ vậy "khớp giao diện" là tính chất cấu trúc, không phải lời hứa.

### D3. Lưu trữ: Room là nguồn sự thật, web chỉ là view

- Trước khi `app.js` chạy: native đọc Room (toàn bộ bản ghi + config) và bơm vào `localStorage` qua shim.
- Mọi thao tác ghi của web (`setItem`/`removeItem`/`clear`) được shim chuyển tiếp sang Room (write-through, hàng đợi tuần tự trên background thread).
- Cơ chế: shim là JS native tiêm bằng `WebViewCompat.addDocumentStartJavaScript` (androidx.webkit) — chạy **trước** `app.js` nên web không cần biết gì. Bơm qua `evaluateJavascript` sau `pageFinished` là quá muộn (app.js đã đọc `localStorage` lúc `init`).
- Loại: chỉ dùng `localStorage` — bền vừa đủ nhưng mất khi WebView data bị xoá (một số ROM dọn "giải phóng dung lượng"), và phá luôn giá trị của `allowBackup` vì DB sẽ trống.
- Loại: patch `app.js` gọi thẳng bridge — fork file web, lệch ngay từ commit đầu.

### D4. File exchange đi qua native, không qua Blob

WebView không tự tải `a[download]` + `Blob`, và không mở được SAF. Shim override đúng các hàm toàn cục mà UI web đang gọi (`exportCSV`, `exportBackup`, `pickRestoreFile`, …) sang lời gọi bridge; native mở SAF/`FileProvider` từ Activity. Không sửa `app.js`, không thêm quyền.

### D5. Bằng chứng offline là bắt buộc, không suy luận

Trên AVD: airplane mode bật **trước** khi cài, cài APK, mở app (phải render đủ UI), thêm 1 điếu, `am force-stop`, mở lại (phải còn điếu đó), và `logcat` không có request mạng ra ngoài origin `appassets`. Đây là ca thật của người dùng; build xanh và grep asset không thay được.

### D6. Xoá Compose UI là task cuối, có cổng

Chỉ xoá sau khi: shell xanh trên CI, bằng chứng offline ở D5, và **Trung cài trên máy thật thấy dữ liệu cũ còn nguyên**. Trước đó `MainActivity` Compose vẫn giữ trong APK, chỉ mất `LAUNCHER` (vẫn mở được bằng intent tường minh để so sánh). Xoá cả ViewModel UI, giữ `data/`, `domain/`, `Theme/Color` nếu còn dùng.

### D7. Phiên bản

`versionName 1.2.0`, `versionCode 3`. Khoá ký không đổi (cert SHA-256 `1f4a8c98…584c69`) nên cài đè không mất dữ liệu.

## Rủi ro đã cân nhắc

- **ROM tắt Android System WebView** ⇒ app không chạy. Chấp nhận; đo lại trên máy thật ở task nghiệm thu.
- **Cold start chậm hơn ~200–400 ms**, RAM +30–50 MB. Chấp nhận với app ghi 1 điếu/lần.
- **Widget / notification nhắc giờ** không làm được trong WebView — để phase sau, làm native.
- **Bridge là bề mặt tin cậy**: chỉ expose method trong danh sách trắng, không nhận chuỗi SQL, không nhận đường dẫn tuỳ ý; dữ liệu vào Room phải qua `DataPortability.normalizeBackup`/DAO hiện có.
