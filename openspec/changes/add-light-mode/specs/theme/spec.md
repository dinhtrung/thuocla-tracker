## Purpose

Cho phép người dùng chọn giữa theme sáng và tối cho app Hành Trình Cai Thuốc Lá, lựa chọn được lưu lại và áp dụng nhất quán trên mọi màn hình.

## ADDED Requirements

### Requirement: Theme selection
App SHALL cung cấp cho người dùng khả năng chuyển đổi giữa theme tối (dark) và theme sáng (light) từ tab Cài đặt. Theme mặc định SHALL là dark.

#### Scenario: User switches to light mode
- **WHEN** người dùng bật nút chuyển theme trong tab Cài đặt
- **THEN** toàn bộ giao diện app chuyển sang theme sáng ngay lập tức

#### Scenario: User switches back to dark mode
- **WHEN** người dùng tắt nút chuyển theme trong tab Cài đặt
- **THEN** toàn bộ giao diện app chuyển về theme tối

### Requirement: Theme persistence
Lựa chọn theme của người dùng SHALL được lưu trong localStorage và được khôi phục ở các lần mở app sau.

#### Scenario: Theme survives app restart
- **WHEN** người dùng chọn theme sáng rồi đóng và mở lại app
- **THEN** app mở ra với theme sáng ngay từ lần render đầu tiên (không hiện flash theme tối)

#### Scenario: New user gets default theme
- **WHEN** người dùng chưa từng chọn theme (không có lựa chọn lưu trong localStorage)
- **THEN** app hiển thị theme tối mặc định

### Requirement: Consistent theme across all screens
Theme đang chọn SHALL được áp dụng đồng nhất trên tất cả các tab (Hôm nay, Thống kê, Insights, Cài đặt) và mọi thành phần giao diện (card, biểu đồ, nút bấm, bottom sheet).

#### Scenario: Theme applies to statistics charts
- **WHEN** người dùng đang ở theme sáng và mở tab Thống kê
- **THEN** các biểu đồ và thẻ thống kê hiển thị với màu theme sáng, không có vùng tối còn sót
