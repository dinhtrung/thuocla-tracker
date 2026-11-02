## Context

Bản web (`index.html` + `app.js` + `styles.css`, SW v38) là nguồn tham chiếu hành vi: mọi hằng số, luật làm tròn, luật trùng phút, thuật toán note "điếu nên cắt" đã được kiểm chứng trên dữ liệu thật nhiều tháng. App Android là **bản viết lại** cùng hành vi, không phải bản sao cơ học: HTML/DOM được thay bằng Compose, `localStorage` bằng Room.

Ràng buộc môi trường (đo trên homebox, 02/11/2026): Java 25 (`openjdk 25.0.4.1`), **chưa có** Android SDK (`ANDROID_HOME` rỗng, `which gradle` rỗng), `adb` có sẵn, đĩa còn 238G, agentgateway ở `http://127.0.0.1:4000/v1` phục vụ các seat omp. Máy soạn spec (box này) không cài Android SDK — nên việc build chạy trên homebox.

## Goals / Non-Goals

**Goals**
- APK cài trực tiếp, chạy 100% offline, không quyền INTERNET, dữ liệu sống trong SQLite của app.
- Ngang hành vi với PWA ở 3 màn MVP: Hôm nay, Thống kê, Cài đặt.
- Nhập trọn dữ liệu cũ qua JSON backup và CSV của PWA; xuất ngược lại đúng định dạng cũ.
- Vòng đời code do fleet trên homebox làm (omp seats), spec này là brief.

**Non-Goals**
- Tab Insights (cửa sổ cắt 24h, thẻ debug 2 chuẩn) — phase 2.
- Widget màn hình chính, notification nhắc giờ, Google Play, đồng bộ nhiều máy, iOS, đa ngôn ngữ.
- Không sửa PWA trong change này (chỉ thêm thư mục `android/`).
- Không có backend/analytics/tài khoản.

## Decisions

**1. Kotlin + Jetpack Compose, một Activity, không Hilt.**
Compose để màn hình khai báo theo state, khớp cách `updateDisplay()` vẽ lại toàn bộ. DI tay (`AppContainer`) vì app chỉ có 1 Room DB + 1 repository — Hilt thêm build time và KSP cho một app 3 màn. *Thay thế đã cân nhắc:* XML Views + ViewBinding (nhiều file, dễ lệch style hơn); Compose Multiplatform (thêm rủi ro toolchain, không cần iOS).

**2. Dữ liệu lưu dạng (dateKey, minuteOfDay) thay vì timestamp UTC.**
Mỗi điếu = `dateKey TEXT 'YYYY-MM-DD'` + `minuteOfDay INT 0–1439`. Ngày/giờ là thứ người dùng thấy; lưu UTC rồi quy đổi sẽ lệch khi đổi múi giờ hoặc DST, đúng loại lỗi mà bản web đã tránh bằng ISO local không có `Z`. *Thay thế:* `Instant` + `ZoneId` (đúng chuẩn nhưng sinh bug "điếu 23:50 thành hôm sau" khi đổi TZ).

**3. Chặn trùng phút bằng UNIQUE INDEX trong DB, không chỉ bằng code.**
`UNIQUE(dateKey, minuteOfDay)` khiến trạng thái sai **không biểu diễn được** (insert vi phạm → `OnConflictStrategy.IGNORE`), thay vì thêm một câu validator ở tầng UI rồi vẫn còn đường ghi lách qua import. Import CSV/JSON cũng đi qua cùng ràng buộc này.

**4. `trigger` là chỉ số 0–7, lưu `Int?`.**
Giữ đúng hợp đồng của bản web và của file backup/CSV; emoji chỉ nằm ở lớp hiển thị. Tên lý do khi xuất CSV lấy từ mảng hằng, không lưu chuỗi — tránh hai nguồn sự thật.

**5. Vào/ra file bằng Storage Access Framework.**
`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` cho nhập và xuất, nên app **không** cần quyền đọc/ghi bộ nhớ, không cần `MANAGE_EXTERNAL_STORAGE`, và vẫn thoả yêu cầu "không quyền mạng".

**6. Code đặt trong `android/` của repo này, không tách repo.**
Spec, PWA và app native ở cùng một chỗ để delta không bị lạc; `.vercelignore` thêm `android/` để Vercel vẫn chỉ deploy PWA. *Thay thế:* repo riêng `thuocla-android` — sạch hơn cho CI nhưng tách spec khỏi code và phải đồng bộ 2 nơi.

**7. Toolchain cố định trên homebox: JDK 17 + cmdline-tools + platform 35 + build-tools 35, Gradle wrapper commit vào repo.**
Java 25 đang có **không dùng được** cho AGP 8.x; cài `openjdk-17-jdk` và trỏ `org.gradle.java.home`/`JAVA_HOME` cho seat. Wrapper (`gradlew` + `gradle-wrapper.jar`) commit để mọi worktree build giống nhau, không phụ thuộc `gradle` hệ thống (hiện chưa có).

**8. Thuật toán "điếu nên cắt" port thành hàm Kotlin thuần + test vàng.**
`computeSttStats`, `getDailyTarget`, hai chuẩn (nhịp quen thuộc / nhịp mục tiêu) tách khỏi UI, test bằng chính dữ liệu người dùng với số kỳ vọng lấy từ bản web (goal 12: 08:00 → khen, 08:40 5 điếu → cảnh báo nhịp, 17:40 12/12 → hết hạn mức, 22:10 → đạt mục tiêu). Hằng số giữ nguyên: lookback 30, gap 40, kép 20, chain 0.25, kép 0.15, min ngày 7, presence 0.3.

**9. Fleet trên homebox: rig `thuocla-android`, runtime omp, 2 builder + 1 reviewer, mỗi seat một worktree tuyệt đối.**
Sao chép khuôn `~/.openrig/specs/rigs/fleet-console/rig.yaml` (cùng agentgateway litellm, `permission_policy: builtin:yolo`, mỗi member có `cwd` tuyệt đối + `startup.files` riêng). Reviewer chạy trên clone chính; builder chạy trên worktree nhánh riêng cắt từ `origin/main`.

## Risks / Trade-offs

- **AGP + JDK sai phiên bản làm cả fleet đứng** → provisioning là bước 1 của tasks, có lệnh kiểm tra (`./gradlew --version` in ra JVM 17) trước khi giao việc cho seat.
- **Tải SDK lần đầu tốn vài GB, cần `--licenses`** → làm một lần trên homebox, ghi `ANDROID_HOME` vào `~/.profile` để mọi seat kế thừa; verify bằng `sdkmanager --list_installed`.
- **Agent không nhìn được màn hình điện thoại** → không có "pixel-perfect" tự động. Giảm thiểu: (a) logic phủ bằng unit test, (b) `assembleDebug` phải xanh, (c) chấp nhận bước xác nhận cuối do Trung cài APK và đối chiếu 5 con số với PWA. Ghi rõ trong tasks để reviewer không tự nhận "xong" khi chưa có bước này.
- **Port lệch thuật toán so với bản web** → test vàng dùng đúng file CSV của người dùng; reviewer đối chiếu từng con số note/bảng so sánh với bản web ở cùng mốc giờ.
- **Hai codebase trôi khỏi nhau** → tuyên bố PWA **đóng băng tính năng** ở v1.5.2; app Android là nguồn chính từ đây. Thay đổi hành vi sau này phải sửa spec trước.
- **Mất dữ liệu khi di chuyển** → nhập luôn ở chế độ hỏi THAY THẾ/GỘP, không tự động ghi đè; và người dùng giữ file JSON backup gốc.

## Migration Plan

1. Trên PWA hiện tại: Cài đặt → Sao lưu, lấy `thuocla_backup_YYYY-MM-DD.json` (làm trên điện thoại, cất 2 bản).
2. Cài APK bản release, mở app (rỗng), chọn Phục hồi → THAY THẾ → file backup.
3. Đối chiếu 5 con số với PWA cùng thời điểm: số điếu hôm nay, tổng tháng, TB 7 ngày, điếu của ngày ít nhất, giờ điếu đầu tiên trong chi tiết ngày.
4. Dùng app native làm nguồn chính. PWA giữ nguyên làm bản xem lại 2 tuần; sau đó có thể gỡ bookmark, không xoá repo.

## Open Questions

- Icon/splash: dùng lại `icon-192.png` / `icon-512.png` của PWA hay vẽ lại? (tạm giả định: dùng lại, chuyển thành adaptive icon).
- Có cần xuất CSV/JSON tự động theo lịch (nhắc sao lưu mỗi 7 ngày) hay để người dùng tự bấm? (tạm giả định: chỉ bấm tay, nhắc tự động là phase 2).
