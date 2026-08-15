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

### Requirement: Behind-pace praise
App SHALL so sánh số thứ tự (STT) của điếu hiện tại với "trường hợp lý tưởng" — số điếu mà một ngày bình thường đáng lẽ đã hút tới thời điểm hiện tại (theo lịch sử 30 ngày, tách cuối tuần/ngày thường). Khi số điếu hôm nay nhỏ hơn vị trí lý tưởng, note SHALL hiển thị trạng thái khen ngợi.

#### Scenario: Praises when smoking fewer than usual pace
- **WHEN** lúc 09:00 thường đã hút tới điếu #3 mà hôm nay mới hút 2 điếu (đã cắt điếu #3)
- **THEN** note hiển thị khen ngợi dạng "Hút ít hơn nhịp thường ngày X điếu — giỏi lắm!"

#### Scenario: No praise when on or ahead of usual pace
- **WHEN** số điếu hôm nay bằng hoặc lớn hơn vị trí STT lý tưởng theo nhịp thường ngày
- **THEN** note không hiển thị trạng thái khen, chuyển sang cảnh báo/mục tiêu cắt tương ứng

#### Scenario: Ideal position uses day-type history
- **WHEN** hôm nay là cuối tuần
- **THEN** vị trí lý tưởng tính từ thống kê riêng của các ngày cuối tuần (không trộn với ngày thường)

### Requirement: Goal-aware reward and warning
App SHALL hiển thị khen thưởng ngay khi số điếu hôm nay đạt đúng mục tiêu ngày, và cảnh báo khi vượt mục tiêu. Mọi mục tiêu cắt SHALL được giới hạn trong hạn mức mục tiêu ngày (không đề xuất cắt điếu có STT lớn hơn goal).

#### Scenario: Praises when daily goal is reached
- **WHEN** số điếu hôm nay đạt đúng mục tiêu ngày (ví dụ 10/10)
- **THEN** note hiển thị trạng thái khen thưởng "Đạt mục tiêu X điếu" thay vì bất kỳ mục tiêu cắt nào

#### Scenario: Warns when goal is exceeded
- **WHEN** số điếu hôm nay vượt mục tiêu ngày (ví dụ 12/10)
- **THEN** note hiển thị cảnh báo vượt mục tiêu, không hiển thị mục tiêu cắt điếu

#### Scenario: Cut target never exceeds the daily goal
- **WHEN** mục tiêu ngày là 10 và điếu "hút theo" phổ biến nhất nằm ở STT 17 (xuất hiện ít ngày)
- **THEN** app không đề xuất cắt điếu #17 mà chỉ chọn mục tiêu trong phạm vi STT 1..10

### Requirement: Persistent note visibility
Khi đã đủ dữ liệu (≥ 7 ngày trong cửa sổ 30 ngày), note "Điếu nên cắt" SHALL luôn hiển thị trên tab Hôm nay — không được biến mất. Khi người dùng đang dưới mục tiêu ngày và không có điếu nào đáng cắt sắp tới, note SHALL hiển thị trạng thái động viên nhắc còn bao nhiêu điếu trong hạn mức.

#### Scenario: Encourages when below goal with nothing cuttable
- **WHEN** số điếu hôm nay nhỏ hơn mục tiêu (ví dụ 9/10) và mọi STT trong hạn mức đã qua giờ dự kiến
- **THEN** note vẫn hiển thị, dạng "Còn X điếu trong hạn mức Y hôm nay — giữ nhịp nhé!"

#### Scenario: Never hides with sufficient data
- **WHEN** app đã có từ 7 ngày dữ liệu trong 30 ngày qua
- **THEN** note không bao giờ ở trạng thái ẩn hoàn toàn trên tab Hôm nay

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
