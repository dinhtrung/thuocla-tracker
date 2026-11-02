# Spec Delta

## Purpose

Ghi nhận trung thực một điếu vừa hút trong vòng một chạm, kèm lý do và mốc giờ đúng, và cho phép sửa lại khi bấm nhầm — đây là dữ liệu gốc cho mọi thống kê và lời khuyên của app.

## ADDED Requirements

### Requirement: Ghi nhanh một điếu

Bấm nút +1 SHALL tạo một bản ghi cho ngày hôm nay với giờ hiện tại làm tròn về bội số 5 phút gần nhất, và bản ghi SHALL hiện ngay trên màn Hôm nay cùng chi tiết ngày mà không cần thao tác tải lại.

#### Scenario: Làm tròn giờ về bội số 5 phút

- **WHEN** người dùng bấm +1 lúc 14:07
- **THEN** bản ghi mới có giờ 14:05 (14:08 sẽ thành 14:10)

#### Scenario: Bấm +1 nhiều lần liên tiếp

- **WHEN** người dùng bấm +1 ba lần trong cùng một phút ở các mốc 09:02, 09:03, 09:04
- **THEN** chỉ có một bản ghi 09:05 được tạo, hai lần sau bị chặn bởi luật trùng phút

### Requirement: Chặn bản ghi trùng phút

App SHALL NOT tạo bản ghi thứ hai trùng cả ngày, giờ và phút với một bản ghi đã có; khi bị chặn, app SHALL hiện cảnh báo ngay trong thẻ đếm (không phải popup nổi) và SHALL NOT mở bước chọn lý do.

#### Scenario: Log trùng phút trong ngày

- **WHEN** ngày 02/11 đã có điếu 10:15 và người dùng thêm điếu 10:15 từ màn Thống kê
- **THEN** app từ chối thêm, hiện cảnh báo "Đã có điếu lúc 10:15" và số điếu của ngày không đổi

### Requirement: Lý do của điếu

Mỗi điếu SHALL nhận được tối đa một lý do thuộc đúng tám lựa chọn cố định theo thứ tự: Sau ăn, Cà phê, Stress, Nhậu, Buồn, Thói quen, Thèm, Khác. Lý do SHALL được lưu dưới dạng chỉ số 0–7 (emoji chỉ là phần hiển thị), và điếu chưa có lý do SHALL hợp lệ.

#### Scenario: Chọn lý do ngay sau khi +1

- **WHEN** người dùng bấm +1 rồi chọn "Cà phê" trong bảng lý do hiện ra sau khoảng 2 giây
- **THEN** đúng điếu vừa tạo mang lý do Cà phê; nếu sau đó bấm +1 tiếp và chọn lý do khác, điếu đầu vẫn giữ Cà phê

#### Scenario: Bỏ lý do đã gắn

- **WHEN** một điếu đang mang lý do Stress và người dùng bấm lại đúng lựa chọn Stress
- **THEN** điếu trở về trạng thái chưa có lý do

### Requirement: Sửa giờ, thêm vào ngày cũ và hoàn tác

Người dùng SHALL sửa được giờ của một điếu bất kỳ trong chi tiết ngày (giờ mới cũng làm tròn 5 phút và cũng bị luật trùng phút chặn), SHALL thêm được điếu vào một ngày trong quá khứ, và SHALL hoàn tác được điếu cuối cùng của hôm nay bằng một nút riêng.

#### Scenario: Hoàn tác điếu vừa ghi

- **WHEN** người dùng bấm +1 rồi bấm nút hoàn tác
- **THEN** điếu vừa thêm biến mất khỏi ngày hôm nay và số điếu giảm đúng một

#### Scenario: Đổi giờ một điếu đã có

- **WHEN** người dùng mở chi tiết ngày và đổi điếu 08:20 thành 08:27
- **THEN** điếu được lưu ở 08:25 và mọi khoảng cách tới điếu trước/sau được tính lại theo giờ mới
