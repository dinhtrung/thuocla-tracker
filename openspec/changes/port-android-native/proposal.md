## Why

Toàn bộ log điếu thuốc đang nằm trong `localStorage` của trình duyệt: xoá cache / đổi máy / dùng chế độ ẩn danh là **mất sạch nhiều tháng dữ liệu** — chính vì vậy v1.5.0 phải thêm "Sao lưu / Phục hồi" bằng tay. App Android native giữ dữ liệu trong SQLite của chính app (không browser nào xoá được), mở tức thì không cần chờ tải trang, và mở đường cho widget / nhắc giờ ở phase sau.

Lưu ý thẳng: PWA **đã** dùng được offline (service worker cache-first từ v1.3.1, hiện v38). Nên lý do port không phải "offline" mà là **độ bền dữ liệu** + hiệu năng mở app + nền tảng native. Bản web vẫn giữ nguyên, không bị thay thế.

## What Changes

- Thêm app Android native viết bằng **Kotlin + Jetpack Compose**, package `vn.dinhtrung.thuocla`, `minSdk 26`, chạy **hoàn toàn offline**, manifest **không xin quyền INTERNET**.
- Lưu trữ bằng **Room/SQLite** trong bộ nhớ riêng của app: mỗi điếu = (ngày, giờ, lý do?, ghi chú?) + cấu hình (goal / intervalGoal / pricePerPack / cigsPerPack).
- 3 màn hình MVP + modal: **Hôm nay** (đếm, +1, hoàn tác, thêm giờ cụ thể, timer, quick stats, bảng so sánh, note "điếu nên cắt"), **Thống kê** (chart 7 ngày + chi tiết ngày + cuối tuần), **Cài đặt** (mục tiêu, giá, sao lưu/phục hồi).
- **Nhập dữ liệu cũ** từ PWA bằng cả hai đường: file **JSON backup** (`thuocla_backup_YYYY-MM-DD.json`, hỏi THAY THẾ hay GỘP) và file **CSV export** (6 cột như bản web).
- **Xuất** đúng định dạng cũ (CSV 6 cột + JSON backup cùng schema) để mang ngược về PWA được.
- Giữ nguyên các hành vi đã verify trên bản web: làm tròn phút về bội số 5, chặn log trùng phút, `trigger` là **chỉ số** trong danh sách 8 lý do, cảnh báo là banner trong thẻ đếm (không phải toast nổi).
- Phát hành **APK sideload** (ký bằng keystore local, không commit); **không** lên Google Play, **không** server, **không** tài khoản, **không** analytics.
- Phase 2 (ngoài change này): tab Insights (cửa sổ cắt 24h, thẻ debug 2 chuẩn), widget màn hình chính, nhắc giờ bằng notification.

## Capabilities

### New Capabilities
- `offline-store`: Lưu trữ cục bộ bền vững (SQLite/Room) làm nguồn sự thật duy nhất, không phụ thuộc mạng cho bất kỳ màn hình nào.
- `cigarette-log`: Ghi nhận / sửa / xoá một điếu (giờ, lý do, ghi chú) kèm luật chống trùng phút và làm tròn 5 phút.
- `daily-summary`: Màn "Hôm nay" — đếm so với mục tiêu, timer tới điếu kế tiếp, quick stats, bảng so sánh 3 cột và note "điếu nên cắt".
- `history-stats`: Màn "Thống kê" — chart số điếu theo ngày, chi tiết một ngày (sửa giờ / gắn lý do) và so sánh cuối tuần vs ngày thường.
- `settings`: Cấu hình mục tiêu ngày, khoảng cách mục tiêu, giá bao, số điếu mỗi bao, và hiển thị phiên bản app.
- `data-portability`: Xuất CSV/JSON và nhập lại từ file backup JSON của PWA hoặc từ CSV export, có chế độ THAY THẾ và GỘP.

### Modified Capabilities
<!-- Không có: đây là codebase mới, openspec/specs hiện đang trống. -->

## Impact

- Thư mục mới `android/` trong repo này (Gradle Kotlin DSL, AGP 8.x, Compose BOM, Room, Hilt không dùng — giữ đơn giản), keystore + `local.properties` nằm ngoài git.
- `.vercelignore` thêm `android/` để Vercel chỉ deploy PWA như hiện nay.
- Không sửa `index.html` / `app.js` / `styles.css` / `service-worker.js` của PWA (đang ở v1.5.2, SW v38).
- Nguồn tham chiếu hành vi: `app.js` bản web (hằng số `CUT_*`, `AVG_MIN_PRESENCE`, `TRIGGERS`, `normalizeBackup`, `exportCSV`) và change `openspec/changes/cut-cigarette-reminder/` (thuật toán note "điếu nên cắt").
- Dữ liệu người dùng: 1 lần nhập file backup từ PWA; sau đó app là nguồn chính, PWA chỉ để xem lại.
