# Spec Delta

## Purpose

Cho người dùng nhìn lại lịch sử theo ngày để thấy tiến bộ thật, sửa được dữ liệu đã ghi sai, và nhận ra ngày cuối tuần có phá nhịp hay không.

## ADDED Requirements

### Requirement: Biểu đồ số điếu theo ngày

Màn Thống kê SHALL hiển thị biểu đồ số điếu theo từng ngày cho khoảng 7 ngày gần nhất, và SHALL cho phép dịch khoảng thời gian sang trước/lùi theo tuần.

#### Scenario: Xem tuần trước

- **WHEN** người dùng mở màn Thống kê rồi bấm nút lùi một tuần
- **THEN** biểu đồ vẽ 7 ngày trước đó và tiêu đề khoảng ngày cập nhật tương ứng

### Requirement: Chi tiết một ngày

Màn Thống kê SHALL mở được chi tiết một ngày bất kỳ, liệt kê từng điếu theo thứ tự giờ kèm khoảng cách tới điếu trước, lý do nếu có, và cho phép sửa giờ hoặc gắn/bỏ lý do ngay tại đó.

#### Scenario: Gắn lý do cho điếu cũ

- **WHEN** người dùng mở chi tiết ngày và bấm vào biểu tượng "chưa có lý do" của điếu 06:55
- **THEN** app mở bảng chọn lý do và lưu lý do cho đúng điếu 06:55 đó

### Requirement: So sánh cuối tuần và ngày thường

Màn Thống kê SHALL hiển thị so sánh 30 ngày gần nhất giữa ngày cuối tuần và ngày thường, gồm trung bình số điếu mỗi ngày, khoảng cách trung bình, số điếu "hút theo" (cách điếu trước không quá 40 phút) và giờ điếu đầu/điếu cuối.

#### Scenario: Chưa có ngày cuối tuần nào

- **WHEN** 30 ngày gần nhất chỉ có ngày thường
- **THEN** phần cuối tuần hiện dấu "—" thay vì số 0 gây hiểu nhầm
