# Design — bản sao lưu tự động + chia sẻ bản sao

## Bối cảnh code (đã đọc trên `main`, không suy đoán)

- `android/app/src/main/AndroidManifest.xml:10` — `android:allowBackup="false"`; manifest không có `uses-permission` nào.
- `android/app/src/main/java/vn/dinhtrung/thuocla/MainActivity.kt:77` — xuất file qua `ActivityResultContracts.CreateDocument("text/plain")`; `:89` — nhập qua `OpenDocument()`; `:245` — MIME lọc `text/*`, `application/json`, `application/octet-stream`.
- `android/app/src/main/java/vn/dinhtrung/thuocla/data/DataPortability.kt:74` — `jsonFileName() = "thuocla_backup_${LocalDate.now(clock)}.json"`; `exportJson()` trả `null` khi chưa có bản ghi; payload `{app, format, exportedAt, config, data}`.
- `android/app/src/main/java/vn/dinhtrung/thuocla/ui/data/DataScreen.kt:73-111` — bốn nút Xuất CSV / Xuất bản sao JSON / Nhập bản sao JSON / Nhập CSV.
- Room DB: `app/src/main/java/vn/dinhtrung/thuocla/data/db/AppDatabase.kt` (một file SQLite duy nhất trong `databases/`).

## Quyết định

### D1. Bật `allowBackup="true"` thay vì tự viết cơ chế backup định kỳ

Android đã có sẵn transport cloud (tài khoản Google) và device-transfer; bật cờ là xong, không code chạy nền, không pin, không wake-lock. Ba phương án khác đã cân nhắc:

- **Giữ `false` + nhắc người dùng xuất file** — chính là hiện trạng, và nó đã thất bại đúng vào lúc cần: người dùng quên.
- **WorkManager tự export ra `Android/data/<pkg>/files/`** — không cần quyền, nhưng **gỡ app là mất**, nên không phải bản sao lưu, chỉ là bản nháp.
- **Đồng bộ lên cloud tự viết (Firebase/Drive API)** — cần `INTERNET`, phá ràng buộc offline và làm đỏ gate CI. Loại.

Hệ quả phải chấp nhận: bản sao nằm trong hạ tầng của Google, không phải file người dùng cầm tay. Vì thế đường SAF + chia sẻ vẫn giữ nguyên — ba đường bù nhau, không đường nào thay đường nào.

### D2. Cần CẢ `fullBackupContent` và `dataExtractionRules`

`minSdk 26` mà `dataExtractionRules` chỉ được đọc từ API 31. Thiếu `fullBackupContent` là API 26–30 rơi về luật mặc định (sa lưu mọi thứ, kể cả cache). Thiếu `dataExtractionRules` là API 31+ cũng rơi về mặc định. Nên có cả hai, nội dung đồng bộ: include `database` + `file`, và `device-transfer` cũng include `database` (chuyển máy bằng cáp không được mất dữ liệu).

**Không có `<exclude>` nào, và đó là quyết định, không phải thiếu sót.** Bản đầu của D2 yêu cầu exclude `cache` + `no_backup`; hai tên đó **không phải domain hợp lệ** của `<full-backup-content>`/`<data-extraction-rules>` (tập hợp lệ: `file`, `database`, `sharedpref`, `external`, `root`, `device_file`, `device_database`, `device_sharedpref`, `device_root`). Viết đúng nguyên văn thì `:app:lintVitalRelease` đỏ 12 lỗi và `:app:assembleRelease` exit 1 — đo được trên PR #41 head `8a8b3255`. Platform vốn đã tự loại `getCacheDir()`, `getCodeCacheDir()` và `getNoBackupFilesDir()` khỏi bản sao lưu, nên một `<exclude>` tường minh vừa bất hợp lệ vừa thừa. Hệ quả: phạm vi bảo vệ không đổi, chỉ bỏ hai phần tử không thể tồn tại.

### D3. Chia sẻ qua `FileProvider` + cache riêng, không ghi ra thư mục công khai

Ghi thẳng vào `Downloads` cần quyền trên API 26–28 và `MediaStore` trên 29+ — hai đường phức tạp cho cùng một việc. `FileProvider` (`androidx.core`, đã có trong dependencies) phục vụ file qua `content://` và cấp quyền đọc tạm bằng `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData`. File đặt ở `cacheDir/backups/` nên hệ điều hành dọn được khi máy thiếu chỗ, và app tự dọn trước mỗi lần chia sẻ (yêu cầu "không để lại file rác").

- Authority: `${applicationId}.fileprovider`, `android:exported="false"`, `android:grantUriPermissions="true"`.
- `res/xml/file_paths.xml`: `<cache-path name="backups" path="backups/" />`.
- Tên file giữ nguyên vì app nhận (Drive/Zalo) dùng `DISPLAY_NAME` từ `ClipData`; đây là thứ quyết định người dùng thấy file tên gì.

### D4. Chỉ chia sẻ JSON, không chia sẻ CSV

CSV **không chứa cấu hình** và không phục hồi được nguyên trạng (gap/STT là số suy ra). Bản sao lưu thật là JSON — cũng là định dạng PWA đọc lại được. Chia sẻ CSV là mời người dùng cất nhầm artefact. CSV vẫn xuất được qua SAF như cũ khi cần đọc bằng Excel.

### D5. Bằng chứng là `bmgr`, không phải build xanh

Khai báo manifest đúng mà transport không chạy thì dữ liệu vẫn mất. Tiêu chí đạt: trên AVD, chọn transport local, `bmgr backupnow`, `pm clear`, `bmgr restore`, mở app thấy đúng số điếu + cấu hình. Trình tự này chính là thứ một người dùng thật trải qua khi cài lại.

Nếu AVD của box không chọn được transport local (`com.android.localtransport/.LocalTransport` không có), ghế **dừng và báo** trong PR body kèm output `bmgr list transports`, không được thay bằng suy luận từ manifest.

### D6. `versionName` 1.1.0, `versionCode` 2

Bản `1.0.0` đang cài trên máy; bump để phân biệt bản có hai tính năng này và để `thuocla-v1.1.0.apk` xuất hiện đúng tên trong release. Khoá ký không đổi (cert SHA-256 `1f4a8c98…584c69`), nên cài đè không mất dữ liệu.

## Không làm trong change này

- Widget màn hình chính, nhắc giờ bằng notification (phase 2 của `port-android-native`).
- Đồng bộ nhiều máy / tài khoản.
- Đổi schema Room, đổi định dạng JSON/CSV, đổi UI ngoài đúng một nút Chia sẻ.
- Đụng vào PWA (`index.html`, `app.js`, `styles.css`, `service-worker.js`) — đóng băng ở v1.5.2.
