# Spec Delta

## Purpose

Đảm bảo mọi dữ liệu hút thuốc và cấu hình sống trong bộ nhớ riêng của app trên máy, không phụ thuộc mạng, trình duyệt hay tài khoản — mất điện, kill app hay đổi mạng đều không làm mất log.

## ADDED Requirements

### Requirement: Kho cục bộ là nguồn sự thật duy nhất

App SHALL lưu toàn bộ điếu và cấu hình trong bộ nhớ riêng của ứng dụng trên thiết bị, và mọi màn hình SHALL đọc từ kho này; app SHALL NOT cần mạng cho bất kỳ chức năng nào trong phạm vi v1.

#### Scenario: Mở app khi không có mạng

- **WHEN** thiết bị ở chế độ máy bay hoặc không có SIM/wifi
- **THEN** app mở bình thường và hiển thị đầy đủ số điếu hôm nay, lịch sử, thống kê

#### Scenario: Dữ liệu sống qua việc kill app và khởi động lại máy

- **WHEN** người dùng ghi 3 điếu rồi kill app (vuốt khỏi recent apps) hoặc khởi động lại máy
- **THEN** mở lại app vẫn thấy đúng 3 điếu đó với giờ và lý do đã lưu

### Requirement: Không yêu cầu quyền mạng

Manifest của app SHALL NOT khai báo quyền `android.permission.INTERNET`; app SHALL chạy đầy đủ với mọi kết nối mạng bị tắt.

#### Scenario: Kiểm tra quyền đã khai báo

- **WHEN** giải nén APK và đọc manifest đã biên dịch
- **THEN** danh sách quyền không chứa `INTERNET` (có thể có `VIBRATE` cho phản hồi rung)

### Requirement: Ghi dữ liệu có tính nguyên tử

Mỗi lần ghi một điếu hoặc lưu cấu hình SHALL là một giao dịch trọn vẹn; nếu tiến trình bị kill giữa lúc ghi thì kho SHALL NOT còn bản ghi dở dang hay cấu hình nửa vời.

#### Scenario: Kill app ngay sau khi bấm +1

- **WHEN** người dùng bấm +1 và tiến trình bị kill trong vòng vài trăm mili giây
- **THEN** lần mở sau, số điếu hôm nay là N hoặc N+1 (không có bản ghi thiếu giờ, không có hai bản ghi cùng phút)
