## 1. Sửa flake
- [ ] 1.1 `StatsViewModelTest`: thêm `@get:Rule val mainDispatcherRule = MainDispatcherRule()`, import `vn.dinhtrung.thuocla.testutil.MainDispatcherRule` và `org.junit.Rule`.
- [ ] 1.2 `StatsViewModelTest`: bỏ `Dispatchers.setMain(...)` trong `@Before` và `Dispatchers.resetMain()` trong `@After`; giữ `viewModel.viewModelScope.cancel()` + `db.close()`. Bỏ các import không còn dùng (`setMain`, `resetMain`, `UnconfinedTestDispatcher`, `Dispatchers` nếu không còn dùng).
- [ ] 1.3 `git grep -n 'Dispatchers.setMain\|Dispatchers.resetMain' -- android/app/src/test` chỉ còn trong `MainDispatcherRule.kt`.

## 2. Bằng chứng (flake không repro tất định — ghi số lần + điều kiện tải)
- [ ] 2.1 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:testDebugUnitTest` xanh (toàn bộ suite).
- [ ] 2.2 Chạy lặp `StatsViewModelTest` tối thiểu 20 lần (`--tests '*StatsViewModelTest' --rerun-tasks`) **dưới tải host** (chạy song song gradle/emulator để tái hiện điều kiện tải) — ghi số lần chạy + điều kiện tải, 0 lần fail "Dispatchers.Main is used concurrently".
- [ ] 2.3 Dán lệnh + output (số test, số lần chạy, điều kiện tải) vào PR body.

## 3. Không làm
- Không đổi `StatsViewModel` hay bất kỳ file nguồn sản phẩm nào; chỉ sửa `android/app/src/test/java/vn/dinhtrung/thuocla/ui/stats/StatsViewModelTest.kt`.
- Không refactor `DataScreenTest`/`SettingsViewModelTest`/`TodayViewModelTest`/`MainDispatcherRule`.
