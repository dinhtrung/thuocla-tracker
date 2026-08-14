## Purpose

Tự động tính toán "điếu nên cắt" từ lịch sử hút của người dùng và hiển thị note nhắc nhở động trên tab Hôm nay, giúp biến phân tích thành hành động đúng thời điểm.

## ADDED Requirements

### Requirement: Cut-target computation
App SHALL tự động tính toán "điếu nên cắt" dựa trên dữ liệu hút của 30 ngày gần nhất và dữ liệu hôm nay, theo vị trí điếu trong ngày (STT), tách riêng thống kê cuối tuần và ngày thường. Điếu "hút theo" là điếu có khoảng cách ≤ 40 phút so với điếu trước; điếu "kép" là khoảng cách ≤ 20 phút; các bản ghi có khoảng cách = 0 SHALL bị loại khỏi tính toán.

#### Scenario: Computes from 30-day history
- **WHEN** có từ 7 ngày dữ liệu trở lên trong cửa sổ 30 ngày
- **THEN** app tính được cho từng STT: số ngày xuất hiện, giờ trung bình ± độ lệch, tần suất "hút theo" và "kép"

#### Scenario: Separates weekend and weekday statistics
- **WHEN** người dùng xem vào cuối tuần (Thứ 7/CN)
- **THEN** mọi tính toán dùng thống kê của riêng cuối tuần, không trộn với ngày thường

### Requirement: Morning cut target
App SHALL hiển thị trên tab Hôm nay mục tiêu cắt 1 điếu cụ thể (STT + giờ dự kiến + số lần là "điếu hút theo" trong 30 ngày) khi chưa có cảnh báo hoặc trạng thái khen nào đang hiển thị.

#### Scenario: Shows daily cut target
- **WHEN** người dùng mở tab Hôm nay và điếu mục tiêu của ngày chưa bị hút
- **THEN** note hiển thị dạng "Hôm nay cắt điếu #N ≈ HH:MM — hút theo X/30 ngày"

#### Scenario: Excludes rigid anchor cigarettes
- **WHEN** một STT có giờ rất ổn định (xuất hiện đều đặn, độ lệch giờ thấp) và ít khi là "điếu hút theo"
- **THEN** STT đó không được chọn làm mục tiêu cắt

### Requirement: Pre-time warning
App SHALL hiển thị cảnh báo khi điếu kế tiếp người dùng sắp hút (STT = số điếu hôm nay + 1) là "điếu hút theo" với tần suất từ 25% số ngày xuất hiện trở lên, và giờ hiện tại đang ở trong khoảng trước giờ dự kiến của điếu đó.

#### Scenario: Warns before the predicted time
- **WHEN** STT kế tiếp có tần suất "hút theo" ≥ 25% và giờ hiện tại chưa vượt quá giờ dự kiến của điếu đó
- **THEN** note hiển thị dạng "Điếu #N ≈ HH:MM — đây là điếu hút theo, thử cắt!"

#### Scenario: No warning for non-chain cigarettes
- **WHEN** STT kế tiếp không phải là "điếu hút theo" theo ngưỡng trên
- **THEN** note không hiện cảnh báo, giữ nguyên trạng thái mục tiêu (nếu có)

### Requirement: Success acknowledgment
App SHALL chuyển note sang trạng thái khen ngợi khi giờ dự kiến của điếu mục tiêu đã qua (cộng thêm độ lệch chuẩn, tối thiểu 30 phút) mà người dùng chưa hút điếu đó.

#### Scenario: Praises after skipping the target
- **WHEN** giờ hiện tại > giờ dự kiến của điếu mục tiêu + độ lệch cho phép, và số điếu hôm nay chưa đạt tới STT mục tiêu
- **THEN** note hiển thị dạng "✅ Đã qua HH:MM chưa hút điếu #N — cắt thành công!"

#### Scenario: Returns to normal state after smoking
- **WHEN** người dùng hút thêm điếu (số điếu hôm nay tăng)
- **THEN** note tính toán lại và hiển thị trạng thái phù hợp với STT kế tiếp mới

### Requirement: Live update cadence
App SHALL tính lại và cập nhật note ngay sau mỗi thao tác thêm hoặc xoá điếu, và định kỳ mỗi phút khi không có thao tác.

#### Scenario: Updates after add or undo
- **WHEN** người dùng bấm nút +1, nút xoá điếu cuối, hoặc thêm điếu với giờ cụ thể
- **THEN** note được tính lại ngay lập tức

#### Scenario: Periodic refresh without interaction
- **WHEN** không có thao tác nào nhưng thời gian trôi qua (chuyển trạng thái nhắc → khen)
- **THEN** note tự cập nhật trong vòng 60 giây

### Requirement: Insufficient-data fallback
App SHALL hiển thị trạng thái chờ dữ liệu khi cửa sổ 30 ngày có ít hơn 7 ngày dữ liệu, và không được phép bịa ra mục tiêu cắt khi thiếu dữ liệu.

#### Scenario: Shows data-needed message
- **WHEN** có ít hơn 7 ngày dữ liệu trong cửa sổ 30 ngày
- **THEN** note hiển thị thông báo cần thêm dữ liệu thay vì mục tiêu hoặc cảnh báo
