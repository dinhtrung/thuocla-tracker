## 1. Toolchain và skeleton (làm 1 lần trên homebox, trước khi giao việc cho seat)

- [ ] 1.1 Cài JDK 17 và ép Gradle dùng nó: `sudo apt-get install -y openjdk-17-jdk` → `java -version` phải in `17.` khi chạy với `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`. Java 25 đang có KHÔNG dùng được cho AGP 8.x.
- [ ] 1.2 Cài Android SDK: `cmdline-tools/latest`, `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`; `yes | sdkmanager --licenses`; export `ANDROID_HOME=$HOME/Android/Sdk` vào `~/.profile`. Bằng chứng: `sdkmanager --list_installed | grep -E 'platforms;android-35|build-tools;35'`.
- [x] 1.3 Tạo `android/` + Gradle wrapper (Kotlin DSL, AGP 8.x, Compose BOM, Room, KSP, minSdk 26, targetSdk 35): `./gradlew :app:assembleDebug` xanh, in đường dẫn APK. Bằng chứng: dòng `BUILD SUCCESSFUL` + `ls -l app/build/outputs/apk/debug/app-debug.apk`.
- [x] 1.4 Manifest KHÔNG có `android.permission.INTERNET`. Bằng chứng: `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` không liệt kê INTERNET.
- [x] 1.5 Thêm `android/` vào `.vercelignore`; commit nhánh `feature/android-skeleton`; mở PR draft. Không đụng `index.html`/`app.js`/`service-worker.js`.

## 2. offline-store (chặn lane B cho tới khi xong 2.2)

- [x] 2.1 Room entity `RecordEntity(dateKey TEXT, minuteOfDay INT, triggerIdx INT NULL, note TEXT NULL)` với `UNIQUE INDEX (dateKey, minuteOfDay)`; `ConfigEntity(id=1, goal, intervalGoal, pricePerPack, cigsPerPack)`. Test: insert hai bản ghi cùng (dateKey, minuteOfDay) → chỉ 1 hàng tồn tại.
- [x] 2.2 DAO + repository: `addRecord` (OnConflict IGNORE trả về cờ bị chặn), `deleteLast(dateKey)`, `updateTime`, `setTrigger`, `day(dateKey)`, `range(from,to)`, `monthCount`, `distinctDayCount`, `getConfig/saveConfig` với default 10/60/25000/20. Chạy trong `withTransaction` cho thao tác nhiều bước.
- [x] 2.3 Test JVM (Robolectric + Room in-memory): đọc/ghi/trùng phút/default config. Bằng chứng: `./gradlew :app:testDebugUnitTest` xanh, in số test.
- [x] 2.4 Kiểm tra sống sót: `adb install -r app-debug.apk`, ghi 3 điếu, `adb shell am force-stop vn.dinhtrung.thuocla`, mở lại → vẫn 3 điếu. Ghi lại output `adb shell run-as vn.dinhtrung.thuocla ls databases/`.

## 3. cigarette-log

- [x] 3.1 Hàm thuần: `roundTo5(minuteOfDay)`, `timeKey(record)`, `addNow`, `addAt(dateKey, hh:mm)`, `undoLast`. Test: 14:07→14:05, 14:08→14:10; thêm 10:15 khi đã có 10:15 → bị chặn.
- [x] 3.2 UI Hôm nay: nút +1 (lớn, gradient), nút hoàn tác (icon), bộ đếm + tiến độ mục tiêu. Bằng chứng: test Compose tìm được `testTag("addCig")`, bấm 2 lần → bộ đếm = 2.
- [x] 3.3 Bottom sheet 8 lý do đúng thứ tự (Sau ăn, Cà phê, Stress, Nhậu, Buồn, Thói quen, Thèm, Khác), mở ~2s sau +1, ghi vào đúng điếu vừa tạo; bấm lại lý do đang gắn = bỏ. Test: +1 rồi chọn Cà phê, +1 rồi chọn Stress → điếu 1 = Cà phê, điếu 2 = Stress.
- [x] 3.4 Sửa giờ một điếu và thêm điếu vào ngày quá khứ, cả hai đi qua luật làm tròn 5 phút + chống trùng phút.

## 4. daily-summary

- [ ] 4.1 Bốn thẻ nhanh: ít nhất (31 ngày, trừ hôm nay), hôm qua, TB 7 ngày, tháng này — kèm fallback ngày-đầu-tiên: chỉ có hôm nay 5 điếu ⇒ TB 7 ngày = **5.0** (không phải 0.7) và thẻ ít nhất = **5 • Hôm nay**. Test đúng 2 con số này.
- [ ] 4.2 Thẻ đồng hồ: đã trôi từ điếu cuối, mục tiêu khoảng cách, giờ dự kiến điếu kế tiếp + số phút còn lại / số phút đã vượt. Test: cuối 12:35, goal 60, now 12:50 → "13:35, còn 45 phút"; now 13:50 → "vượt 15".
- [ ] 4.3 Bảng so sánh 3 cột (Hôm nay | Ngày ít nhất | TB 30 ngày), mỗi ô 2 dòng; cột Hôm nay luôn vẽ đủ kể cả khi chưa có ngày tham chiếu (2 cột kia hiện "—").

## 5. cut-reminder (thuật toán port nguyên hằng số)

- [ ] 5.1 Port `computeSttStats` + `getDailyTarget` + hai chuẩn (nhịp quen thuộc theo giờ hiện tại, nhịp mục tiêu = span/mục tiêu ngày) thành Kotlin thuần; hằng số: lookback 30, gap 40, kép 20, chain 0.25, kép 0.15, min ngày 7, presence 0.3, cap STT 20.
- [ ] 5.2 Note 5 trạng thái đúng thứ tự ưu tiên: đạt mục tiêu (phân biệt sớm/muộn theo giờ điếu cuối TB) > khen (dưới cả hai chuẩn) > cảnh báo nhịp (trên cả hai) > cảnh báo điếu kế tiếp > gợi ý điếu mục tiêu > fallback "còn X điếu trong hạn mức". Tính lại mỗi phút và sau mỗi lần ghi/hoàn tác.
- [ ] 5.3 Test vàng bằng dữ liệu thật của Trung (goal 12, seed có cả ngày cuối tuần): 08:00 với 2 điếu → khen; 08:40 với 5 điếu → cảnh báo nhịp; 17:40 với 12/12 → hết hạn mức; 22:10 với 12/12 → đạt mục tiêu. Ghi 4 verdict thực tế vào PR.

## 6. history-stats

- [x] 6.1 Biểu đồ cột số điếu 7 ngày + nút lùi/tiến tuần; trục có nhãn ngày và giá trị. Test: seed 14 ngày → bấm lùi 1 tuần, dữ liệu đổi đúng khoảng.
- [x] 6.2 Chi tiết ngày: danh sách điếu theo giờ + khoảng cách + lý do; sửa giờ và gắn/bỏ lý do ngay trong chi tiết.
- [x] 6.3 Khối cuối tuần vs ngày thường (TB điếu/ngày, gap TB, số điếu "hút theo" ≤40 phút, giờ điếu đầu/cuối); thiếu ngày cuối tuần ⇒ hiện "—" không hiện 0.

## 7. settings

- [ ] 7.1 Form 4 trường (mục tiêu ngày, khoảng cách mục tiêu, giá bao, điếu/bao) + lưu; ô trống/quá nhỏ ⇒ giữ default 10 / 60 / 25000 / 20, không tạo config rỗng. Test: đổi mục tiêu 10→8, quay về Hôm nay thấy 8 và tiến độ tính lại.
- [ ] 7.2 Hiện phiên bản app + số ngày có dữ liệu (vd `v1.0.0 · 128 ngày`).

## 8. data-portability

- [x] 8.1 Xuất CSV đúng 6 cột `Ngày,Giờ,Lý do,Ghi chú,Khoảng cách (phút),STT trong ngày`: tên lý do không emoji, khoảng cách rỗng ở điếu đầu, STT từ 1. Test với ngày 02/11 5 điếu → 1 header + 5 dòng, khoảng cách `,85,115,50,90`.
- [x] 8.2 Xuất JSON `thuocla_backup_YYYY-MM-DD.json` với `{app, format, exportedAt, config, data}`; `data` là map ngày → records `{time, trigger?, note?}`. Test: đọc lại file, `data["2026-11-02"]` có đúng 5 phần tử.
- [x] 8.3 Nhập JSON: hỏi THAY THẾ / GỘP; chuẩn hoá bỏ key ngày không hợp lệ, bỏ record `time` hỏng, bỏ `trigger` ngoài 0–7; GỘP bỏ trùng phút, config trong file thắng. Test: file 40 ngày + đang có 3 ngày ⇒ 43 ngày (hoặc ít hơn nếu trùng ngày); file JSON không có `data` ⇒ báo lỗi, dữ liệu không đổi.
- [x] 8.4 Nhập CSV: parse 6 cột, suy lại gap/STT (không tin file), map tên lý do → chỉ số, dòng hỏng (vd `25:99`) ⇒ abort toàn bộ + báo số dòng lỗi.
- [ ] 8.5 Round-trip: export JSON → xoá dữ liệu (xoá app data) → import → cùng số ngày/điếu/lý do. Bằng chứng: 2 lần `adb shell run-as ... ls databases/` + số đếm trong app trước/sau.

## 9. Build, đóng gói, verify với người dùng

- [ ] 9.1 Keystore release tạo local (`~/keystores/thuocla-release.jks`, KHÔNG commit), `./gradlew :app:assembleRelease`, copy APK sang `~/thuocla-apk/thuocla-vX.Y.Z.apk`. Bằng chứng: `ls -l` + `apksigner verify --print-certs`.
- [ ] 9.2 Cài trên điện thoại của Trung, Phục hồi từ `thuocla_backup_*.json` (THAY THẾ), rồi đối chiếu 5 con số với PWA cùng lúc: số điếu hôm nay, tổng tháng, TB 7 ngày, số điếu ngày ít nhất, giờ điếu đầu trong chi tiết ngày. Ghi 5 cặp số vào PR — bước này KHÔNG tự đánh dấu xong, cần Trung xác nhận.
- [ ] 9.3 `README-android.md` trong `android/`: cách build, cách cài, cách nhập bản sao, và ghi chú PWA đóng băng tính năng ở v1.5.2.
