## Why
Bộ test Android có flake: hai test trong `StatsViewModelTest` thỉnh thoảng đỏ với lỗi
`Dispatchers.Main is used concurrently with setting it`. Nguyên nhân đã xác định: `StatsViewModelTest`
là class **duy nhất** cài `Dispatchers.Main` bằng tay (`@Before Dispatchers.setMain(...)` /
`@After Dispatchers.resetMain()`), trong khi ba class view-model khác (`DataScreenTest`,
`SettingsViewModelTest`, `TodayViewModelTest`) dùng chung `testutil/MainDispatcherRule`. Đây là flake
**có sẵn** (vào từ #14), KHÔNG phải regression của #19. Vì intermittent, ai review tiếp theo sẽ phải
phát hiện lại "bằng đường đau" nếu không có change này.

## What Changes
- Chuyển `StatsViewModelTest` sang dùng `MainDispatcherRule` như các class khác: bỏ
  `Dispatchers.setMain`/`resetMain` inline, thêm `@get:Rule val mainDispatcherRule = MainDispatcherRule()`.
- Không đổi logic test nào; chỉ đổi cách cài main dispatcher.
- Bằng chứng: chạy lặp class dưới tải, ghi số lần chạy + điều kiện; không tuyên bố "fixed" từ một lần xanh.

## Non-goals
- Không sửa code sản phẩm (`StatsViewModel` và các file nguồn khác giữ nguyên).
- Không refactor các test class khác (chúng đã đúng).

## Capabilities
### New Capabilities
- `android-test-dispatchers`: quy ước cài `Dispatchers.Main` trong unit test Android qua một rule dùng chung, không cài tay.

### Modified Capabilities
_(không sửa capability nào đang có)_
