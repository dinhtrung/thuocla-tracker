# Spec Delta

## Purpose

App phải dùng được khi **không có mạng** (đi leo núi nhiều ngày) mà giao diện **không lệch** bản web. Hai yêu cầu này chỉ gặp nhau ở một điểm: render chính bộ file của bản web, đọc từ trong APK.

## ADDED Requirements

### Requirement: Asset của app là bản sao của bản web

APK SHALL chứa bản sao byte-identical của `index.html`, `app.js`, `styles.css`, `manifest.json`, `icon-192.png`, `icon-512.png` trong `assets/web/`. Repo SHALL có script kiểm tra từng cặp file theo SHA-256 và SHALL fail khi lệch; CI SHALL chạy script đó trước khi build APK.

#### Scenario: Không ai sửa tay bản trong APK

- **WHEN** một file trong `android/app/src/main/assets/web/` bị sửa mà file gốc ở repo root không đổi
- **THEN** script verify fail với tên file lệch, và job APK không được coi là xanh

#### Scenario: Sửa bản web là shell nhận ngay

- **WHEN** `styles.css` ở repo root được sửa rồi copy sang `assets/web/` và build lại
- **THEN** app hiện đúng thay đổi đó mà không phải sửa bất kỳ file Kotlin/Compose nào

### Requirement: App chạy được ngay lần mở đầu tiên khi không có mạng

App SHALL render đầy đủ giao diện và SHALL ghi được điếu khi thiết bị không có kết nối mạng, kể cả ở lần mở app đầu tiên sau khi cài (chưa từng chạy khi có mạng). App SHALL NOT phát sinh request ra ngoài origin `appassets.androidplatform.net` để tải giao diện.

#### Scenario: Cài máy bay rồi mở app

- **WHEN** thiết bị đang ở chế độ máy bay, người dùng cài APK và mở app lần đầu
- **THEN** app hiện đủ các tab và số điếu (0), bấm +1 ghi được một điếu, và màn Thống kê mở được — không có màn hình trắng, không có thông báo lỗi mạng

#### Scenario: Dữ liệu sống qua force-stop

- **WHEN** người dùng ghi 1 điếu rồi app bị `force-stop` và mở lại, vẫn không có mạng
- **THEN** điếu đó vẫn còn

### Requirement: Manifest không có quyền mạng

App SHALL NOT khai báo `android.permission.INTERNET` hay bất kỳ quyền bộ nhớ nào. Web shell SHALL chỉ phục vụ asset từ trong APK.

#### Scenario: Gate CI vẫn xanh

- **WHEN** build debug và release sau khi thêm web shell
- **THEN** `aapt2 dump permissions` không có `INTERNET`, và bước gate INTERNET trong CI xanh

### Requirement: Room là nguồn sự thật của dữ liệu

Trước khi mã web khởi động, app SHALL nạp dữ liệu từ Room vào vùng lưu trữ web — tối thiểu ba khoá mà bản web đang dùng: `smoking_data`, `smoking_config`, `theme` — để màn hình hiển thị đúng dữ liệu hiện có. Mọi thay đổi dữ liệu phát sinh từ web SHALL được ghi vào Room. Xoá dữ liệu web (WebView storage) SHALL NOT làm mất dữ liệu của app.

#### Scenario: Xoá storage của WebView không mất dữ liệu

- **WHEN** app đang có 5 điếu, người dùng xoá dữ liệu WebView của app (hoặc app bị dọn storage) rồi mở lại
- **THEN** app vẫn hiện đúng 5 điếu và cấu hình cũ, vì dữ liệu được nạp lại từ Room

#### Scenario: Dữ liệu của bản Compose cũ vẫn thấy trong shell

- **WHEN** người dùng đã dùng bản native trước đó (dữ liệu nằm trong Room) rồi cập nhật lên bản shell
- **THEN** shell hiện đúng số điếu, lý do và bốn giá trị cấu hình đang có, không cần nhập lại gì

### Requirement: Các hành động dữ liệu của bản web chạy được trong shell

Ba hành động mà giao diện web đang có — Xuất CSV, Xuất bản sao JSON, Nhập bản sao JSON — và hành động Chia sẻ bản sao SHALL chạy được trong shell, đi qua SAF/`FileProvider` của native thay vì tải file bằng Blob. Định dạng file, luật THAY THẾ/GỘP và thông báo lỗi SHALL giữ nguyên như capability `data-portability` đã đặc tả.

#### Scenario: Xuất CSV từ shell

- **WHEN** người dùng bấm Xuất CSV trong shell và chọn đích trong hộp thoại của hệ thống
- **THEN** file nhận được đúng 6 cột như bản web xuất ra

#### Scenario: Nhập bản sao JSON từ shell

- **WHEN** người dùng chọn một file backup JSON và chọn THAY THẾ hoặc GỘP
- **THEN** dữ liệu trong app đổi đúng theo lựa chọn, và màn hình cập nhật ngay mà không cần khởi động lại

#### Scenario: Nhập CSV nằm ngoài phạm vi shell

- **WHEN** người dùng cần nhập file CSV
- **THEN** app nêu rõ đường nhập CSV chỉ có ở bản native cũ (giao diện web không có nút này), và shell không được tự thêm nút mới — thêm UI là một change khác

### Requirement: Không fork giao diện

App SHALL NOT chứa bản sao thứ hai của giao diện (Compose viết lại) sau khi shell được nghiệm thu. Trong thời gian chuyển tiếp, màn Compose cũ SHALL chỉ mở được bằng intent tường minh, không còn là launcher.

#### Scenario: Chỉ còn một nguồn giao diện

- **WHEN** change này hoàn tất
- **THEN** không còn file Compose nào dựng màn hình (Today/Stats/Settings/Data) trong `android/app/src/main/java/.../ui`, và launcher chỉ mở web shell
