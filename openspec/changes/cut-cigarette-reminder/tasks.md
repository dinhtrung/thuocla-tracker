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

## 4. Goal-aware fix (phản hồi user 2026-08-14: "thuật toán chỉ đúng nếu hút nhiều hơn kế hoạch")

- [x] 4.1 Thêm GOAL state (ưu tiên cao nhất): `todayCount == goal` → 🎉 khen "Đạt mục tiêu X điếu"; `todayCount > goal` → ⚠️ cảnh báo vượt mục tiêu
- [x] 4.2 Giới hạn mục tiêu cắt trong hạn mức: `getDailyTarget()` filter `stt <= goal`; thêm `CUT_MIN_PRESENCE=0.3` loại STT 1-off; tail fallback chỉ chọn điếu chưa tới giờ
- [x] 4.3 Cache target 1 lần/ngày (`cutDailyTarget`), tự roll sang điếu kế tiếp khi target đã bị hút
- [x] 4.4 Cập nhật spec (Goal-aware reward requirement) + design (D2/D2b) + CHANGELOG v1.4.1 + SW v21
- [x] 4.5 Verify 18 assertions: goal-reached/exceeded/tail mới + regression warn/target/praise/neutral/live/layout (puppeteer) → deploy + curl live

## 5. Persistent visibility fix (phản hồi user 2026-08-14: "giờ ko thấy panel đâu nữa")

- [x] 5.1 Probe tái hiện data thật (không chuỗi, 10 điếu/ngày, goal 10-12): xác nhận case `9/10 @ 22:30 → display:none` (panel ẩn đúng lúc hút ít hơn kế hoạch)
- [x] 5.2 Thay nhánh ẩn bằng trạng thái động viên: "Còn X điếu trong hạn mức Y hôm nay — giữ nhịp nhé!" (goal>0) / 💪 không đáng cắt (goal=0); chuẩn hoá `goal = parseInt(cfg.goal,10)||0`
- [x] 5.3 Verify 18 assertions (live-update expectation đổi thành "fallback shown") + probe 4/4 hiển thị → deploy SW v22 + curl live

## 6. Behind-pace praise (chỉ đạo user 2026-08-15: "so sánh STT điếu hiện tại với trường hợp lý tưởng, nhỏ hơn thì khen")

- [x] 6.1 Thay cơ chế khen cũ (target qua giờ) bằng `getExpectedStt()`: đếm STT presence ≥30% (theo đúng loại ngày) đã qua `avgTime+slack`; `todayCount < expected` → ✅ "Hút ít hơn nhịp thường ngày X điếu — giỏi lắm!"
- [x] 6.2 Fix bug presence: `present / typeCount[dayType]` (8 ngày cuối tuần / 8) thay vì `/ dayCount` (8/29) — candidate cuối tuần từng bị loại nhầm → target nhảy sai (#6 thay vì #3)
- [x] 6.3 getDailyTarget chỉ nhận candidate chưa qua giờ (bỏ pool passed) — không hiện "cắt điếu đã qua giờ"
- [x] 6.4 Verify: user case 4/4 (08:00 WARN #3 → 09:00 KHEN → 07:00 target #3 → 08:40 hết khen stale) + regression 13/13 → deploy SW v23 + curl live

## 7. Ideal = TB hôm qua về trước (chỉ đạo user 2026-08-15: "Sao lại +1 làm gì?")

- [x] 7.1 Bỏ +1: `getAvgCountByNow()` — TB số điếu hút tới giờ này, cửa sổ 30 ngày, LOẠI hôm nay (ds >= today skip), tách weekend/weekday; idealStt = ceil(TB)
- [x] 7.2 Khen ưu tiên TRƯỚC WARN (user: 08:00 mới 2/12 → phải khen, không đe "cắt #3") — thứ tự GOAL > PRAISE > WARN > TARGET > fallback
- [x] 7.3 Verify bằng DỮ LIỆU THẬT (CSV 07-15→08-15, goal 12): 6/6 — 07:00(1) khen, 08:00(2) khen TB~2.4, 08:55/09:00(3) khen, 10:00(3) khen "ít hơn 2", 08:40(4 >TB 3.3) → WARN #5; regression 13/13 → deploy SW v24 + curl live
