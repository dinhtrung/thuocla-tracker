# Spec Delta

## Purpose

Trả lời trong một màn hình ba câu hỏi của người đang cai thuốc: hôm nay đã hút mấy điếu so với mục tiêu, còn bao lâu nữa mới tới "điếu kế tiếp", và hôm nay đang tệ hơn hay tốt hơn nhịp quen thuộc.

## ADDED Requirements

### Requirement: Đếm và tiến độ mục tiêu

Màn Hôm nay SHALL hiển thị số điếu đã hút hôm nay, mục tiêu ngày, và thanh tiến độ tính bằng số điếu trên mục tiêu (kẹp ở 100%); khi chưa cấu hình mục tiêu thì SHALL dùng giá trị mặc định 10.

#### Scenario: Đếm tăng sau mỗi lần ghi

- **WHEN** ngày mới bắt đầu và người dùng ghi lần lượt 3 điếu
- **THEN** bộ đếm lần lượt là 1, 2, 3 và thanh tiến độ tương ứng 10%, 20%, 30% với mục tiêu 10

### Requirement: Đồng hồ từ điếu cuối tới điếu kế tiếp

App SHALL hiển thị thời gian đã trôi từ điếu cuối cùng hôm nay, mục tiêu khoảng cách, và khi đã có mục tiêu khoảng cách thì SHALL hiển thị giờ dự kiến của điếu kế tiếp kèm số phút còn lại; khi đã vượt mục tiêu thì SHALL hiển thị số phút đã vượt.

#### Scenario: Chưa tới mục tiêu khoảng cách

- **WHEN** điếu cuối lúc 12:35, mục tiêu khoảng cách 60 phút, hiện tại 12:50
- **THEN** app hiện đã trôi 15 phút, dự kiến điếu kế tiếp 13:35 và còn 45 phút

#### Scenario: Đã vượt mục tiêu khoảng cách

- **WHEN** điếu cuối lúc 12:35, mục tiêu 60 phút, hiện tại 13:50
- **THEN** app hiện đã trôi 75 phút và trạng thái đạt mục tiêu với số phút vượt là 15

### Requirement: Bốn chỉ số nhanh

Màn Hôm nay SHALL hiển thị bốn thẻ: số điếu của ngày ít nhất trong 31 ngày (trừ hôm nay), số điếu hôm qua, trung bình mỗi ngày trong 7 ngày, và tổng số điếu của tháng hiện tại.

#### Scenario: Trung bình 7 ngày chỉ có một ngày dữ liệu

- **WHEN** trong 7 ngày chỉ có hôm nay với 5 điếu
- **THEN** thẻ trung bình hiện 5.0 (chia theo số ngày có dữ liệu), không phải 0.7

#### Scenario: Ngày đầu tiên dùng app

- **WHEN** chưa có ngày nào khác ngoài hôm nay và hôm nay có 5 điếu
- **THEN** thẻ "ít nhất" hiện 5 kèm nhãn "Hôm nay", bấm vào mở được chi tiết ngày hôm nay

### Requirement: Bảng so sánh ba cột

Màn Hôm nay SHALL hiển thị bảng so sánh ba cột Hôm nay / Ngày ít nhất / Trung bình 30 ngày, mỗi ô hai dòng (giờ và khoảng cách kèm chênh lệch so với mục tiêu); cột Hôm nay SHALL luôn hiển thị đủ các điếu của hôm nay kể cả khi chưa có ngày tham chiếu.

#### Scenario: Chưa có ngày tham chiếu

- **WHEN** chỉ có dữ liệu của hôm nay với 5 điếu
- **THEN** bảng vẫn vẽ 5 hàng của cột Hôm nay, hai cột còn lại hiện dấu "—"

#### Scenario: Có đủ lịch sử

- **WHEN** có 30 ngày dữ liệu và hôm nay 5 điếu
- **THEN** mỗi hàng có giờ hôm nay, giờ của ngày ít nhất, và giờ trung bình theo đúng số thứ tự điếu

### Requirement: Note "điếu nên cắt"

Màn Hôm nay SHALL hiển thị một note gợi ý điếu nên cắt, tính lại mỗi phút và sau mỗi lần ghi/hoàn tác, dựa trên hai chuẩn: nhịp quen thuộc tới giờ hiện tại và nhịp mục tiêu suy ra từ mục tiêu ngày; note SHALL có các trạng thái đạt mục tiêu, khen, cảnh báo nhịp, cảnh báo điếu kế tiếp và gợi ý điếu mục tiêu.

#### Scenario: Chưa đủ dữ liệu

- **WHEN** người dùng mới có dữ liệu của 1 ngày
- **THEN** note hiện trạng thái "cần thêm dữ liệu" kèm số ngày còn thiếu (tối thiểu 7 ngày)

#### Scenario: Hút nhanh hơn cả hai chuẩn

- **WHEN** đã hút 5 điếu lúc 08:40 trong khi nhịp quen thuộc là 3.3 điếu và nhịp mục tiêu là 3
- **THEN** note hiện cảnh báo hút nhanh hơn cả thói quen lẫn nhịp mục tiêu kèm số phút cần giãn mỗi điếu
