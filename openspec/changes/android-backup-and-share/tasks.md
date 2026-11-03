## 0. Luật chung cho cả change

- Chỉ sửa trong `android/`. Không đụng `index.html` / `app.js` / `styles.css` / `service-worker.js` (PWA đóng băng v1.5.2), không đổi schema Room, không đổi định dạng JSON/CSV.
- **Cấm thêm quyền**: `android.permission.INTERNET`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`. Bước gate trong CI sẽ đỏ nếu có INTERNET, và spec `data-portability` cấm phần còn lại.
- Build gate trước khi push: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest` — dán nguyên output vào PR body.
- Gặp ràng buộc không có trong spec (phải đổi schema, phải xin quyền, transport backup không chạy được): **dừng và báo trong PR body**, không tự xấp xỉ.

## 1. Manifest + luật sao lưu (deliverable: bản sao lưu tự động của OS)

- [x] 1.1 Thêm `android/app/src/main/res/xml/backup_rules.xml` (dùng cho `android:fullBackupContent`, API 26–30): `<full-backup-content>` với `<include domain="database" path="."/>`, `<include domain="file" path="."/>`, `<exclude domain="cache" path="."/>`, `<exclude domain="no_backup" path="."/>`.
- [x] 1.2 Thêm `android/app/src/main/res/xml/data_extraction_rules.xml` (API 31+) với **hai** nhánh `cloud-backup` và `device-transfer`, mỗi nhánh include `database` + `file` và exclude `cache` + `no_backup`.
- [x] 1.3 Sửa `android/app/src/main/AndroidManifest.xml`: `android:allowBackup="true"`, `android:fullBackupContent="@xml/backup_rules"`, `android:dataExtractionRules="@xml/data_extraction_rules"`. Không thêm `uses-permission` nào.
- [x] 1.4 Bằng chứng tĩnh: `aapt2 dump xmltree app/build/outputs/apk/debug/app-debug.apk --file AndroidManifest.xml | grep -iE "allowBackup|fullBackupContent|dataExtractionRules"` in ra `allowBackup=true` (và hai thuộc tính trỏ đúng file xml); `aapt2 dump permissions` không có INTERNET. Dán cả hai output vào PR.

## 2. Chứng minh backup/restore thật trên AVD (deliverable: dữ liệu sống sót qua cài lại)

- [x] 2.1 Trên AVD đang chạy (`~/Android/Sdk/emulator`, ảnh `android-35;google_apis;x86_64` đã dùng ở task 3.3): `adb shell bmgr list transports` — phải thấy `com.android.localtransport/.LocalTransport`; chọn nó bằng `adb shell bmgr transport com.android.localtransport/.LocalTransport`, rồi `adb shell bmgr enable true`.
- [x] 2.2 Seed dữ liệu có kiểm soát: cài `app-debug.apk`, ghi 3 điếu vào hôm nay, đổi mục tiêu ngày 10 → 8 trong Cài đặt. Ghi lại số điếu + mục tiêu (đây là "trước").
- [x] 2.3 `adb shell bmgr backupnow vn.dinhtrung.thuocla` → output phải chứa `Package vn.dinhtrung.thuocla with result: Success`. Dán nguyên dòng output.
- [x] 2.4 `adb shell pm clear vn.dinhtrung.thuocla`, mở app → phải trống (0 điếu, mục tiêu về default). Đây là bước chứng minh dữ liệu đã thực sự rời app.
- [x] 2.5 `adb shell bmgr restore <token-của-dataset>` (token lấy từ `adb shell dumpsys backup | grep -i "Current:"` hoặc `bmgr list sets`), mở app → đúng 3 điếu và mục tiêu 8. Ghi cặp số "trước/sau" vào PR.
- [ ] 2.6 Nếu bước 2.1 hoặc 2.5 không chạy được trên AVD này: **dừng, không tự đánh dấu xong**, dán output lỗi + ảnh `bmgr list transports` vào PR body và ghi rõ phần nào chưa chứng minh được.

## 3. Chia sẻ bản sao JSON (deliverable: gửi bản sao sang Drive/Zalo/Telegram)

- [ ] 3.1 `res/xml/file_paths.xml`: `<paths><cache-path name="backups" path="backups/" /></paths>`.
- [ ] 3.2 Khai báo `<provider>` trong `AndroidManifest.xml`: `android:name="androidx.core.content.FileProvider"`, `android:authorities="${applicationId}.fileprovider"`, `android:exported="false"`, `android:grantUriPermissions="true"`, meta-data `android.support.FILE_PROVIDER_PATHS` → `@xml/file_paths`.
- [ ] 3.3 Trong `DataPortability` (hoặc helper mới cùng package `data`): hàm ghi bản sao chia sẻ vào `cacheDir/backups/` — xoá các file cũ trong thư mục trước khi ghi, tên lấy từ `jsonFileName()`, nội dung lấy từ `exportJson()`, trả `null` khi `exportJson()` trả `null`.
- [ ] 3.4 `MainActivity`: thêm launcher/khối tạo intent `Intent.ACTION_SEND` — `type = "application/json"`, `putExtra(Intent.EXTRA_STREAM, uri)`, `putExtra(Intent.EXTRA_TITLE, name)`, `clipData = ClipData.newUri(contentResolver, name, uri)`, `addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`, mở bằng `Intent.createChooser`. Trước khi mở: nếu `intent.resolveActivity(packageManager) == null` → báo lỗi qua slot message hiện có, **không crash**.
- [ ] 3.5 `DataViewModel` + `DataScreen`: thêm hành động **Chia sẻ bản sao JSON** ngay dưới **Xuất bản sao JSON** (`DataScreen.kt:80`), dùng cùng kiểu nút và cùng slot thông báo; chưa có dữ liệu → cùng cách xử lý với nút xuất (thông báo, không tạo file rỗng).
- [ ] 3.6 Test (Robolectric, JVM): bấm hành động chia sẻ → `ShadowActivity.getNextStartedActivity()` trả intent có `action == ACTION_SEND`, `type == "application/json"`, `EXTRA_STREAM` có authority `<applicationId>.fileprovider`, và file tương ứng tồn tại trong `cacheDir/backups/` với tên đúng `thuocla_backup_YYYY-MM-DD.json`, nội dung parse được và có khoá `data`. Thêm test: gọi chia sẻ hai lần → thư mục cache chỉ còn 1 file.
- [ ] 3.7 Bằng chứng sống trên AVD: bấm Chia sẻ, chọn một app nhận có trên AVD (hoặc `adb shell dumpsys activity intents` để chứng minh intent bay ra), chụp màn hình; ghi rõ app nhận đã chọn.

## 4. Docs (ghế A) + phát hành (orchestrator)

- [x] 4.1 [A] `android/README-android.md`: thêm mục **Sao lưu** nêu đủ ba đường (tự động của OS, Chia sẻ file, Xuất file qua SAF) + giới hạn thật: bản sao tự động chỉ về khi **cài mới/đổi máy** và máy có bật sao lưu; "Xoá dữ liệu" trên cùng máy thì chỉ `bmgr restore` mới cứu được; chia sẻ dùng chung cache nên không dùng làm kho lưu. Chỉ **một** ghế sửa file này (ghế A); ghế B không đụng README.
- [ ] 4.2 [mỗi ghế] Tick đúng các mục mình làm trong `openspec/changes/android-backup-and-share/tasks.md`, không tick mục của ghế kia, và ghi `openspec validate --changes android-backup-and-share --strict` xanh vào PR body.
- [ ] 4.3 [orchestrator, SAU khi cả hai PR merge] `android/app/build.gradle.kts`: `versionName = "1.1.0"`, `versionCode = 2` — cố ý để sau cùng, vì hai nhánh cùng sửa một dòng này là conflict chắc chắn.
- [ ] 4.4 [orchestrator, sau 4.3] tag `android-v1.1.0` → CI ký bằng keystore hiện có và đính `thuocla-v1.1.0.apk` vào GitHub Release; cài đè lên bản 1.0.0 trên máy Trung, dữ liệu phải còn nguyên.
