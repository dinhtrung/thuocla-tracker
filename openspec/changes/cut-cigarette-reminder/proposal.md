## Why

Người dùng đang cai thuốc (~14 điếu/ngày, giảm dần) nhưng phải tự nhớ "điếu nào nên cắt" — phân tích hiện chỉ nằm ở tab Insights dạng tổng hợp 30 ngày (✂️ Cửa sổ cắt), không nhắc đúng thời điểm quyết định hút. Một note trên tab Hôm nay sẽ biến insight có sẵn thành hành động ngay lúc người dùng chuẩn bị hút, tăng khả năng cắt thành công.

## What Changes

- Thêm **note card "Điếu nên cắt"** trên tab Hôm nay (giữa hàng nút +1 và quick stats), tự tính toán từ dữ liệu 30 ngày + dữ liệu hôm nay.
- Note có **3 trạng thái luân phiên trong ngày**:
  1. **Mục tiêu sáng**: chốt 1 điếu nên cắt hôm nay (dựa trên STT có tần suất "hút theo" cao nhất, ưu tiên giờ chưa qua).
  2. **Nhắc trước giờ dự đoán**: khi STT kế tiếp (todayCount+1) là "điếu hút theo" → nhắc trước giờ dự đoán ± độ lệch chuẩn: "điếu #N ≈ HH:MM, 8/30 ngày là điếu theo, thử bỏ!".
  3. **Khen khi cắt thành công**: khi giờ dự đoán của STT mục tiêu đã qua mà người dùng chưa hút → chuyển note thành động viên "cắt thành công".
- Thuật toán per-STT (kế thừa luật đã verify trong `references/data-analysis.md`): tần suất "hút theo" (gap ≤40ph) và "kép" (≤20ph), giờ trung bình ± stdev, tách riêng weekend/weekday, luôn loại `gap=0`.
- Cập nhật live: chạy lại sau `addCig`/`undoLast` và định kỳ mỗi phút (không mỗi giây).
- **Không** đổi model dữ liệu, **không** thêm tab mới (tuân thủ quy tắc tối đa 4 tab), **không** đụng timer card và `renderCutWindows()` hiện tại.

## Capabilities

### New Capabilities
- `cut-reminder`: Capability tự động tính toán "điếu nên cắt" từ lịch sử hút và hiển thị note nhắc nhở động trên tab Hôm nay (mục tiêu sáng → nhắc trước → khen sau).

### Modified Capabilities
<!-- Không có: không thay đổi requirement của capability hiện có, chỉ thêm hành vi mới. -->

## Impact

- `index.html` — thêm block note card trong `#tab-main` (sau `.btn-row`, trước `.stats-grid`).
- `app.js` — thêm `renderCutReminder()` + helper tính per-STT; hook vào `updateDisplay()`, `addCig` patch và interval 60s; không sửa `renderCutWindows()`.
- `styles.css` — thêm style note card (3 trạng thái màu: mục tiêu/đang nhắc/khen).
- `service-worker.js` — bump `CACHE` version khi release (bắt buộc, cache-first).
- Không phụ thuộc backend/dependency mới — thuần localStorage + JS hiện có.
