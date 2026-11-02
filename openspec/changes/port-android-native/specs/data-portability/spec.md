# Spec Delta

## Purpose

Đảm bảo nhiều tháng dữ liệu đang nằm trong PWA đi sang app native mà không mất một điếu nào, và chiều ngược lại vẫn dùng được nếu người dùng muốn quay về bản web.

## ADDED Requirements

### Requirement: Xuất CSV tương thích bản web

App SHALL xuất được một file CSV với header đúng thứ tự `Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày`; mỗi dòng SHALL có ngày dạng `YYYY-MM-DD`, giờ `HH:MM`, tên lý do không kèm emoji, khoảng cách tới điếu trước tính bằng phút (rỗng với điếu đầu ngày) và số thứ tự trong ngày bắt đầu từ 1.

#### Scenario: Xuất file CSV

- **WHEN** ngày 02/11 có 5 điếu và người dùng chọn xuất CSV
- **THEN** file có 1 dòng header và 5 dòng dữ liệu, dòng đầu là 06:55 với ô khoảng cách rỗng, các dòng sau có khoảng cách 85, 115, 50, 90

### Requirement: Xuất bản sao JSON

App SHALL xuất được file JSON có tên `thuocla_backup_YYYY-MM-DD.json` chứa các khoá `app`, `format`, `exportedAt`, `config`, `data`, trong đó `data` là ánh xạ từ ngày sang danh sách điếu có `time`, `trigger` (0–7) và `note` khi có.

#### Scenario: File sao lưu mở lại được bằng bản web

- **WHEN** người dùng xuất bản sao JSON từ app Android rồi phục hồi file đó trong PWA
- **THEN** PWA nhận đủ số ngày và số điếu, lý do hiển thị đúng

### Requirement: Nhập bản sao JSON của PWA

App SHALL nhập được file backup JSON do PWA xuất ra và SHALL hỏi rõ hai chế độ: THAY THẾ toàn bộ dữ liệu hiện có, hoặc GỘP vào dữ liệu đang có. Khi nhập, app SHALL chỉ nhận khoá ngày hợp lệ, bỏ bản ghi có `time` không đọc được và bỏ `trigger` nằm ngoài 0–7.

#### Scenario: Gộp một file sao lưu

- **WHEN** app đang có 3 ngày dữ liệu và người dùng nhập một file backup có 40 ngày, chọn GỘP
- **THEN** app có 43 ngày (hoặc ít hơn nếu trùng ngày), điếu trùng đúng phút trong cùng ngày bị bỏ, và cấu hình trong file thắng cấu hình đang có

#### Scenario: File không phải bản sao hợp lệ

- **WHEN** người dùng chọn một file JSON không có khoá `data` đúng dạng
- **THEN** app báo lỗi rõ ràng và không thay đổi bất kỳ dữ liệu nào

### Requirement: Nhập CSV export của PWA

App SHALL nhập được file CSV 6 cột do PWA xuất ra, tự suy lại khoảng cách và số thứ tự thay vì tin hai cột đó, ánh xạ tên lý do sang chỉ số 0–7 và để trống lý do nếu tên không khớp; nếu file hỏng thì SHALL không ghi gì và báo số dòng lỗi đầu tiên.

#### Scenario: Nhập CSV 5 điếu

- **WHEN** người dùng nhập file CSV có 5 dòng của ngày 02/11 với lý do Cà phê, Sau ăn, Cà phê, Stress, Sau ăn
- **THEN** app tạo đúng ngày 02/11 với 5 điếu và lý do lần lượt là chỉ số 1, 0, 1, 2, 0

#### Scenario: CSV có dòng hỏng

- **WHEN** file CSV có một dòng với ô giờ là "25:99"
- **THEN** app báo lỗi ở dòng đó và không nhập bất kỳ dòng nào từ file
