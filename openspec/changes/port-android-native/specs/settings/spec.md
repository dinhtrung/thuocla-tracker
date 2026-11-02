# Spec Delta

## Purpose

Cho người dùng tự đặt mục tiêu cai và giá thuốc của mình, vì mọi lời khuyên và con số tiền trong app đều suy ra từ bốn giá trị này.

## ADDED Requirements

### Requirement: Cấu hình mục tiêu và giá

Màn Cài đặt SHALL cho phép sửa bốn giá trị: mục tiêu số điếu mỗi ngày, khoảng cách mục tiêu giữa hai điếu (phút), giá một bao và số điếu mỗi bao. Giá trị mặc định khi chưa cấu hình SHALL là 10 điếu/ngày, 60 phút, 25.000 đồng/bao, 20 điếu/bao.

#### Scenario: Lưu mục tiêu mới

- **WHEN** người dùng đổi mục tiêu ngày từ 10 sang 8 và bấm lưu
- **THEN** màn Hôm nay hiện mục tiêu 8 và tiến độ tính lại theo 8 ngay khi quay về

#### Scenario: Nhập giá trị không hợp lệ

- **WHEN** người dùng xoá trắng ô giá bao rồi bấm lưu
- **THEN** app giữ giá trị mặc định cho ô đó và không tạo cấu hình rỗng

### Requirement: Hiển thị phiên bản và tiến độ cai

Màn Cài đặt SHALL hiển thị số phiên bản app và số ngày đã có dữ liệu, để người dùng biết mình đang chạy bản nào và đã theo dõi được bao lâu khi cần báo lỗi.

#### Scenario: Kiểm tra phiên bản khi báo lỗi

- **WHEN** người dùng mở tab Cài đặt
- **THEN** app hiện phiên bản hiện tại kèm số ngày có dữ liệu (ví dụ "v1.0.0 · 128 ngày")
