## Why

Hai lỗ hổng còn lại của đường sao lưu, đều đã kiểm tra trên `main`:

1. **Không có backup tự động nào.** `android/app/src/main/AndroidManifest.xml:10` đang đặt `android:allowBackup="false"`, nên Android **không** đưa Room DB vào bản sao lưu của tài khoản Google và cũng không chuyển dữ liệu khi đổi máy. Người dùng chỉ có đúng một đường: bấm **Xuất bản sao JSON** (`ui/data/DataScreen.kt:80`) rồi tự cất file đi. Quên một lần là mất toàn bộ nhiều tháng dữ liệu — đúng cái rủi ro mà bản port native sinh ra để xoá (`port-android-native/proposal.md`: "App Android native giữ dữ liệu trong SQLite của chính app… mở đường cho…").
2. **File xuất ra không đẩy đi đâu được.** Đường xuất duy nhất là `ActivityResultContracts.CreateDocument("text/plain")` (`MainActivity.kt:77`) ghi qua SAF — người dùng phải tự mở app quản lý file rồi copy thủ công. Muốn gửi bản sao sang Drive/Zalo/Telegram (nơi thực tế người dùng cất file) phải có một đường chia sẻ.

Điều quan trọng: **cả hai đều không cần quyền INTERNET.** Backup của OS do hệ thống chạy (app chỉ khai báo phần nào được sao lưu), còn chia sẻ file là `ACTION_SEND` qua `FileProvider` (`content://`, cấp quyền đọc tạm cho app nhận). Ràng buộc "offline hoàn toàn, manifest không có INTERNET" của bản port được giữ nguyên, và bước gate INTERNET trong CI vẫn phải xanh.

## What Changes

- **Backup tự động của hệ điều hành:** bật `android:allowBackup="true"`, thêm `android:fullBackupContent` (API 26–30) và `android:dataExtractionRules` (API 31+) để **include** `database` (Room: bản ghi điếu + config) và `file`; **không** khai báo `<exclude>` (`cache`/`no_backup` không phải domain hợp lệ và platform đã tự loại chúng — xem design D2). Nhờ vậy cài lại app hoặc đổi máy (cùng tài khoản Google, hoặc chuyển máy trực tiếp bằng cáp) là dữ liệu về lại, không cần thao tác nào.
- **Chia sẻ bản sao:** nút **Chia sẻ bản sao JSON** trong tab Dữ liệu, dùng `Intent.ACTION_SEND` + `Intent.createChooser`, MIME `application/json`, đính file qua `FileProvider` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml`, cache dir `backups/`), tên file giữ nguyên `thuocla_backup_YYYY-MM-DD.json` (`DataPortability.jsonFileName()`). Không xin quyền bộ nhớ, không xin INTERNET.
- **Bằng chứng backup thật, không chỉ build xanh:** test trên AVD bằng `bmgr` — chọn transport local, `bmgr backupnow`, xoá dữ liệu app, `bmgr restore`, rồi chứng minh số điếu + config về đúng. Đây là thứ duy nhất chứng minh `allowBackup` đúng; đọc manifest không đủ.
- **Giữ nguyên đường cũ:** Xuất CSV / Xuất JSON qua SAF, Nhập JSON / Nhập CSV vẫn y như spec `data-portability` hiện có — không đổi hành vi, không đổi định dạng file.
- **Phát hành:** nâng `versionName` lên `1.1.0` để bản có hai tính năng này tách khỏi `1.0.0` đang cài trên máy; tag `android-v1.1.0` để CI ký và đính APK.

## Capabilities

### New Capabilities

Không có. Change này chỉ mở rộng capability `data-portability` đã có trong `port-android-native`.

### Modified Capabilities

- `data-portability`: thêm hai yêu cầu — bản sao lưu tự động của hệ điều hành (allowBackup + extraction rules) và chia sẻ bản sao JSON ra ngoài app.

## Impact

- File sửa: `android/app/src/main/AndroidManifest.xml` (allowBackup, fullBackupContent, dataExtractionRules, provider), `android/app/src/main/java/vn/dinhtrung/thuocla/MainActivity.kt` (launcher/`ACTION_SEND`), `android/app/src/main/java/vn/dinhtrung/thuocla/ui/data/DataScreen.kt` + `DataViewModel.kt` (nút và trạng thái), `android/README-android.md` (mục sao lưu + cách chuyển máy).
- File mới: `android/app/src/main/res/xml/backup_rules.xml`, `android/app/src/main/res/xml/data_extraction_rules.xml`, `android/app/src/main/res/xml/file_paths.xml`.
- Không sửa: `index.html` / `app.js` / `styles.css` / `service-worker.js` (PWA đóng băng ở v1.5.2), schema Room, định dạng JSON/CSV.
- Rủi ro đã cân nhắc: `allowBackup="true"` cho phép dữ liệu rời khỏi app bằng cơ chế của OS. Chấp nhận vì dữ liệu là của chính người dùng trên máy họ, và đây là đường duy nhất để không mất dữ liệu khi hỏng/đổi máy.
- CI: `.github/workflows/android-apk.yml` không đổi. Gate "APK không được xin quyền INTERNET" vẫn áp cho cả debug và release.
