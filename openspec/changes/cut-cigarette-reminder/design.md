## Context

App là static PWA (index.html + app.js + styles.css + service-worker.js, không build step, deploy qua Vercel). Dữ liệu nằm trong localStorage `{YYYY-MM-DD: [{time: ISO, ...}]}`. Hiện tại:

- `renderCutWindows()` (tab Insights) đã có phân tích 30 ngày: đếm "điếu hút theo" (gap ≤40ph) và "kép" (≤20ph) theo giờ — nhưng là số tổng hợp, không chỉ ra điếu cụ thể sắp tới.
- `updateTimer()` (tab Hôm nay) chạy mỗi giây, là hook live duy nhất trên trang chủ.
- `references/data-analysis.md` đã verify luật cắt: cắt điếu thứ 2 trong chuỗi; kép 13h/21h là mục tiêu #1; STT#3 (≈08:10) & #4 (≈09:20) là neo cứng (stdev ±44-49ph, xuất hiện đều) — **không** khuyến nghị cắt; đuôi ngày STT#13+ vốn lỏng.
- UI prefs bắt buộc: tối đa 4 tab, icon-only buttons, secondary style rgba(255,255,255,0.05)/text-dim/radius 16px, round minutes 5, bỏ dead code, mirror layout.

Động lực: xem proposal.md — Why.

## Goals / Non-Goals

**Goals:**
- Note card động 1 vị trí trên tab Hôm nay với 3 trạng thái: mục tiêu → cảnh báo → khen.
- Thuật toán per-STT tách weekend/weekday, loại `gap=0`, cùng hằng số với `renderCutWindows` (40/20 phút).
- Chi phí tính toán thấp (30 ngày × ~14 records — vài trăm phép so sánh, chạy được mỗi phút).

**Non-Goals:**
- Không đổi `renderCutWindows()` hay tab Insights.
- Không đổi model dữ liệu / không thêm tab.
- Không push notification, không âm thanh, không dismiss — note hiển thị thụ động trên trang chủ.
- Không dự đoán theo chuỗi Markov/ML — chỉ thống kê tần suất.

## Decisions

### D1: Chọn mục tiêu cắt theo per-STT score
Với mỗi STT N (1..20) trong 30 ngày qua, theo loại ngày (weekend/weekday):
- `present` = số ngày có ≥ N điếu
- `avgTime`, `stdevTime` = giờ trung bình ± độ lệch chuẩn
- `chainFreq` = ngày điếu #N có gap≤40 với điếu #N-1 / present
- `kepFreq` = ngày điếu #N có gap≤20 / present

Mục tiêu sáng = STT có `chainFreq ≥ 0.25` (hoặc `kepFreq ≥ 0.15`), giờ dự kiến còn ở phía trước trong ngày, **ưu tiên giờ sớm nhất** (actionable sớm), loại trừ neo cứng: `present ≥ 0.6 × số ngày` AND `stdevTime ≤ 45ph` AND `chainFreq < 0.2`. Fallback: STT muộn nhất còn trong lịch sử. Không tìm được → không hiện mục tiêu (giữ note trạng thái nhẹ).

Lý do: luật này phản ánh trực tiếp insight đã verify — cắt điếu "hút theo" (autopilot), né neo cứng. So với phương án đếm theo giờ (như renderCutWindows): per-STT cho biết *điếu thứ mấy* chứ không chỉ *giờ nào*, khớp yêu cầu "điếu nên cắt".

### D2: State machine 4 trạng thái, goal-aware (sửa theo phản hồi 2026-08-14)
```
goal = cfg.goal (>0)
if goal > 0 && todayCount >= goal:
    → KHEN/CHẶN GOAL  todayCount == goal: 🎉 "Đạt mục tiêu X điếu — thắng hôm nay rồi!"
                       todayCount  > goal: ⚠️ "Vượt mục tiêu X điếu (đang N) — dừng lại!"
else:
    STT kế tiếp = todayCount + 1  (≤ goal, tự giới hạn)
    if s.chainFreq ≥ 0.25 và now < s.avgTime + slack:
        → CẢNH BÁO  "Điếu #N ≈ HH:MM — hút theo, thử cắt!"
    else:
        target = getDailyTarget(...)  (cache 1 lần/ngày)
        if target && now > target.avgTime + slack && todayCount < target.stt:
            → KHEN      "✅ Đã qua HH:MM chưa hút điếu #N — cắt thành công!"
        elif target:
            → MỤC TIÊU "Hôm nay cắt điếu #N ≈ HH:MM" (chain: kèm tần suất; tail: "điếu cuối trong hạn mức")
        else:
            → ĐỘNG VIÊN  goal > 0: 🎯 "Còn X điếu trong hạn mức Y hôm nay — giữ nhịp nhé!"
                         goal = 0: 💪 "Không có điếu nào đáng cắt — khoảng cách hút đang ổn!"
```
`slack = max(30, stdevTime)`. Trạng thái GOAL có độ ưu tiên cao nhất — đạt/vượt mục tiêu ngày là thông điệp quan trọng hơn mọi đề xuất cắt. Lý do sửa: bản đầu không goal-aware → lúc 21:31 đã đủ 10/10 mục tiêu mà note vẫn đề xuất "cắt điếu #17 ≈ 22:16" (STT chỉ xuất hiện 4/30 ngày) — giả định người dùng hút tới 17 điếu, vô nghĩa với người đang cai; đồng thời không có khen thưởng khi đạt mục tiêu.

**Sửa tiếp (v1.4.2)**: nhánh `else` từng ẩn note (`display:none`) khi không còn điếu đáng cắt — đúng lúc người dùng hút ÍT hơn kế hoạch (9/10 lúc 22:30) panel biến mất. → Panel không bao giờ ẩn khi đủ dữ liệu; thay bằng trạng thái động viên nhắc hạn mức còn lại. Ngoài ra `goal` được chuẩn hoá bằng `parseInt` (config có thể lưu chuỗi "10" → `10 === "10"` sai → nhầm "Vượt mục tiêu" khi đạt đúng goal).

### D2b: Target chốt 1 lần/ngày (cache theo ngày)
`getDailyTarget()` chọn target khi render đầu tiên trong ngày và cache (`cutDailyTarget = {date, stt, avgTime, stdevTime, chain, kep, present, reason}`); reset khi đổi ngày. Khi điếu mục tiêu đã bị hút (`todayCount ≥ stt`) → clear cache + chọn lại target kế tiếp (`stt > todayCount`). Nhờ đó PRAISE đánh giá đúng "điếu mục tiêu đã qua giờ mà chưa hút" bất kể app được mở lúc nào trong ngày.

### D3: Vị trí + style note card
HTML: block mới sau `.btn-row`, trước `.stats-grid` trong `#tab-main` — đúng nơi mắt người dùng rơi ngay sau thao tác hút. Style: class riêng `.cut-note` nhưng nhất quán hệ thống card hiện tại (bg rgba(255,255,255,0.05), radius 16px, border `var(--line)`, padding 12-14px, text 13-14px); icon theo trạng thái: 🎯 (mục tiêu), ⏳/⚠️ (cảnh báo), ✅ (khen). Không tạo nút bấm trong note (tránh bloat, đúng Non-Goals).

### D4: Trigger cập nhật
- Gọi `renderCutReminder()` ở đầu `updateDisplay()` — `updateDisplay()` đã chạy sau mọi thao tác thêm/xoá (addCig patch, undoLast, thêm giờ cụ thể) và khi switch tab.
- Thêm `setInterval(renderCutReminder, 60000)` riêng (không nhét vào `updateTimer` 1s — tốn không cần thiết; ngưỡng thời gian đều theo phút).
- Tính toán mỗi lần render từ raw data (không cache) — đủ rẻ.

### D5: Hằng số dùng chung
`LOOKBACK=30 ngày`, `MAX_GAP=40`, `MAX_KEP=20`, `CHAIN_THRESHOLD=0.25`, `KEP_THRESHOLD=0.15`, `SLACK=max(30, stdev)`, `MIN_DAYS=7` (fallback), `STT_CAP=20`. Giữ `gap>0` (loại duplicate gap=0) — đúng luật `renderCutWindows` (xem data-analysis.md, mục NOTE).

## Risks / Trade-offs

- **[Dữ liệu ít]** Người dùng mới < 7 ngày dữ liệu → fallback "cần thêm dữ liệu", không bịa mục tiêu (đã spec). → Mitigation: `MIN_DAYS=7`, note dạng hướng dẫn.
- **[Dự đoán lệch]** stdev lớn (điếu sáng sớm ±50ph) → cảnh báo sai giờ, hoặc KHEN sớm. → Mitigation: `slack = max(30, stdev)`; chỉ CẢNH BÁO khi `chainFreq ≥ 0.25` — điếu theo thường có gap ngắn nên giờ khá ổn định; nếu cả 30 ngày đều không có đủ mẫu → rơi về MỤC TIÊU.
- **[Chật home tab]** Home tab đã có timer + counter + stats-grid + comparison → thêm card nữa có thể dài. → Mitigation: note gọn 2-3 dòng text, không thêm nút; ẩn hẳn khi không có trạng thái nào (đủ dữ liệu nhưng không có mục tiêu → ẩn, không chiếm chỗ).
- **[SW cache-first]** Nếu quên bump `CACHE` version, điện thoại phục vụ file cũ → feature không bao giờ hiện. → Mitigation: bump version trong task release (bắt buộc, đã có trong skill deploy pipeline).
- **[Trùng gap=0 duplicate]** Đếm nhầm điếu kép khi cùng giờ bấm nhiều lần. → Mitigation: áp dụng đúng luật `gap>0` như `renderCutWindows`.

## Migration Plan

- Deploy tiêu chuẩn của repo: sửa index.html/app.js/styles.css → verify local (puppeteer, script verify-layout.js) → bump `CACHE` trong service-worker.js → `git add` targeted → commit + push → verify live bằng curl. Không cần migration dữ liệu (không đổi model).

## Open Questions

- Có nên thêm chế độ "ẩn note hôm nay" không? (Mặc định: không — giữ đơn giản, note tự biến mất cuối ngày. Quyết định sau được, không ảnh hưởng spec.)
