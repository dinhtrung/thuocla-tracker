## 1. HTML + CSS

- [x] 1.1 Thêm block note card `#cutNote` vào `#tab-main` (sau `.btn-row`, trước `.stats-grid`): icon + text chính + text phụ (id: `cutNote`, `cutNoteIcon`, `cutNoteText`, `cutNoteSub`), ẩn mặc định (`display:none`)
- [x] 1.2 Thêm CSS `.cut-note` nhất quán hệ thống card (bg rgba(255,255,255,0.05), radius 16px, border `var(--line)`, padding 12-14px, flex icon+text) + 3 biến thể màu theo trạng thái: `.cut-note.warn` / `.cut-note.praise` / `.cut-note.target`

## 2. Core logic (app.js)

- [x] 2.1 Viết `computeSttStats()`: quét `loadData()` cửa sổ 30 ngày, per-STT (1..20) theo loại ngày (weekend/weekday) → `present`, `avgTime`, `stdevTime`, `chainFreq` (gap ≤40), `kepFreq` (gap ≤20); áp dụng luật `gap>0` (loại duplicate gap=0)
- [x] 2.2 Viết `selectDailyTarget()`: chọn mục tiêu sáng theo design D1 — `chainFreq ≥ 0.25` hoặc `kepFreq ≥ 0.15`, giờ dự kiến còn trong tương lai, ưu tiên giờ sớm nhất, loại neo cứng (present ≥ 60% số ngày + stdev ≤ 45ph + chainFreq < 0.2), fallback STT muộn nhất có lịch sử; trả null nếu không tìm được
- [x] 2.3 Viết `renderCutReminder()`: state machine D2 (CẢNH BÁO → KHEN → MỤC TIÊU → ẩn), `slack = max(30, stdev)`; `MIN_DAYS=7` → fallback text "cần thêm dữ liệu"; cập nhật text + icon (🎯/⚠️/✅) + class theo trạng thái
- [x] 2.4 Hook cập nhật: gọi `renderCutReminder()` ở đầu `updateDisplay()` + `setInterval(renderCutReminder, 60000)` (khởi động cùng `startTimer`); verify đường `addCig`/`undoLast`/`openTimePicker` đều chạy `updateDisplay()` (patch nếu thiếu)
- [x] 2.5 Xoá dead code nếu có (không để lại hàm/class thừa từ quá trình phát triển)

## 3. Verify + Deploy

- [x] 3.1 Seed dữ liệu test (synthetic 30 ngày gồm weekend/weekday, chuỗi gap ≤40/≤20 quanh giờ nhất định) → verify bằng browser: 3 trạng thái hiển thị đúng text/class/icon, fallback khi < 7 ngày, layout không tràn container 328px, không đè timer card
- [x] 3.2 Bump `CACHE` version trong `service-worker.js` (bắt buộc — cache-first)
- [x] 3.3 Cập nhật CHANGELOG.md (mục `✨ Mới`) + `git add` targeted (chỉ index.html, app.js, styles.css, service-worker.js, CHANGELOG.md — tránh `.serena/`) + commit + push
- [x] 3.4 Verify live: `curl -s https://thuocla-tracker.vercel.app/app.js | grep` code mới + xác nhận `CACHE` mới được serve
