# Spec Delta

## Purpose

Dữ liệu nhiều tháng của người dùng phải sống sót qua các sự cố thật: hỏng máy, đổi máy, cài lại app, hoặc đơn giản là quên bấm xuất file. Hiện app chỉ có một đường duy nhất — người dùng tự xuất file rồi tự cất — nên chỉ cần quên một lần là mất sạch.

## ADDED Requirements

### Requirement: Bản sao lưu tự động của hệ điều hành

App SHALL tham gia cơ chế sao lưu của hệ điều hành: manifest SHALL khai báo `android:allowBackup="true"`, `android:fullBackupContent` (API 26–30) và `android:dataExtractionRules` (API 31 trở lên). Hai file luật SHALL **include** domain `database` (toàn bộ Room DB: bản ghi điếu và cấu hình) và domain `file`. Hai file SHALL NOT khai báo `<exclude>` cho `cache`/`no_backup`: đó không phải domain hợp lệ, và nền tảng đã tự loại hai thư mục đó khỏi bản sao lưu.

#### Scenario: Cài lại app và dữ liệu về lại

- **WHEN** máy đã có bản sao lưu của app, người dùng gỡ app rồi cài lại (cùng tài khoản Google, hoặc chuyển máy trực tiếp bằng cáp)
- **THEN** app mở ra với đúng số ngày và số điếu đã có trong bản sao lưu, và bốn giá trị cấu hình (mục tiêu ngày, khoảng cách, giá bao, điếu/bao) cũng về đúng

#### Scenario: Domain không hợp lệ làm đỏ release build

- **WHEN** file luật sao lưu khai báo `<exclude domain="cache">` hoặc `<exclude domain="no_backup">`
- **THEN** `:app:lintVitalRelease` fail và `:app:assembleRelease` đỏ, nên bản phát hành không ra được — đây là lý do hai file luật chỉ include, không exclude

#### Scenario: Bản sao lưu chứa dữ liệu mới nhất

- **WHEN** người dùng ghi thêm 2 điếu rồi bản sao lưu của hệ điều hành chạy
- **THEN** lần phục hồi kế tiếp trả về số điếu đã tính cả 2 điếu mới, không phải con số trước đó

#### Scenario: Manifest vẫn không có quyền mạng

- **WHEN** build debug và build release
- **THEN** `aapt2 dump permissions` không liệt kê `android.permission.INTERNET`, và bước gate INTERNET trong CI vẫn xanh

### Requirement: Bản sao lưu tự động không xin quyền và không gọi mạng

App SHALL NOT xin bất kỳ quyền nào (`INTERNET`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`) để phục vụ yêu cầu sao lưu tự động. Việc truyền bản sao lưu SHALL do chính hệ điều hành thực hiện, app chỉ khai báo phạm vi dữ liệu được sao lưu.

#### Scenario: Không quyền mới nào xuất hiện

- **WHEN** build APK sau khi bật `allowBackup` và thêm hai file luật
- **THEN** danh sách permission của APK y hệt trước khi thay đổi (chỉ còn quyền nội bộ do AndroidX sinh ra), không có quyền bộ nhớ hay mạng

### Requirement: File bản sao để chia sẻ

Tab Dữ liệu SHALL có hành động **Chia sẻ bản sao JSON** đặt cạnh hành động xuất file hiện có. Khi người dùng chọn, app SHALL tạo file `thuocla_backup_YYYY-MM-DD.json` có nội dung y hệt bản xuất JSON hiện hành (`{app, format, exportedAt, config, data}`) trong thư mục cache riêng của app, trước khi mở hộp chọn ứng dụng.

#### Scenario: Không có dữ liệu để chia sẻ

- **WHEN** app chưa có bản ghi nào
- **THEN** hành động Chia sẻ không tạo file rỗng và app báo chưa có dữ liệu, giống cách hành động xuất JSON đang xử lý (`DataPortability.exportJson()` trả `null`)

### Requirement: Phạm vi của Chia sẻ theo từng bản build

Yêu cầu "File bản sao để chia sẻ" áp cho **bản Compose 1.1.0**. Từ bản shell 1.2.0 trở đi, giao diện là bản web đóng băng v1.5.2 (không có nút Chia sẻ), nên shell SHALL NOT cung cấp hành động này.

Đây là quyết định có chủ ý của chủ sản phẩm (phương án b, 2026-11-04) và là regression đã biết so với 1.1.0. Phần native vẫn còn trong APK nhưng không có lối vào từ giao diện.

#### Scenario: Shell không có hành động Chia sẻ

- **WHEN** người dùng mở tab **Cài đặt** trong bản shell 1.2.0
- **THEN** không có nút Chia sẻ nào; đường chia sẻ duy nhất còn lại là **Xuất bản sao JSON** qua SAF

### Requirement: Mở hộp chọn ứng dụng để gửi bản sao

App SHALL mở hộp chọn ứng dụng bằng `Intent.ACTION_SEND` với MIME `application/json`, đính file qua `content://` của `FileProvider`, giữ nguyên tên file hiển thị, và cấp quyền đọc tạm thời cho ứng dụng nhận. App SHALL NOT xin quyền bộ nhớ và SHALL NOT dùng mạng cho việc này.

#### Scenario: Gửi bản sao sang ứng dụng khác

- **WHEN** người dùng bấm Chia sẻ bản sao JSON rồi chọn một ứng dụng nhận (Drive, Zalo, Telegram…)
- **THEN** ứng dụng nhận được **một** file tên `thuocla_backup_YYYY-MM-DD.json` đúng nội dung JSON, và phục hồi file đó trong app (hoặc trong PWA) cho đủ số ngày, số điếu và cấu hình

#### Scenario: Không có ứng dụng nào nhận

- **WHEN** không ứng dụng nào trên máy nhận MIME `application/json`
- **THEN** app hiện thông báo không chia sẻ được và **không** bị crash

### Requirement: Dọn file tạm của lần chia sẻ trước

Trước khi ghi file cho một lần chia sẻ mới, app SHALL xoá các file bản sao còn lại trong thư mục cache dành riêng cho việc chia sẻ, để thư mục đó không tích luỹ theo ngày.

#### Scenario: Không để lại file rác

- **WHEN** một lần chia sẻ đã xong (gửi thành công hoặc người dùng huỷ hộp chọn) và sau đó người dùng chia sẻ lần nữa
- **THEN** thư mục cache của bản sao chỉ còn file của lần chia sẻ mới nhất, không tích luỹ theo ngày

### Requirement: Đường sao lưu cũ không bị thay đổi

Ba hành động xuất/nhập qua SAF hiện có (Xuất CSV, Xuất bản sao JSON, Nhập bản sao JSON, Nhập CSV) SHALL giữ nguyên hành vi và định dạng đã đặc tả ở capability `data-portability` của change `port-android-native`; change này chỉ **thêm** đường mới, không thay đổi hay thay thế đường cũ.

#### Scenario: File xuất vẫn tương thích bản web

- **WHEN** người dùng xuất bản sao JSON bằng đường SAF sau khi change này được merge
- **THEN** file vẫn có đúng tên `thuocla_backup_YYYY-MM-DD.json`, đúng sáu trường CSV như cũ, và PWA v1.5.2 vẫn phục hồi được
