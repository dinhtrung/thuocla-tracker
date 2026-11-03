# Spec Delta
## Purpose
Đảm bảo unit test view-model của Android cài `Dispatchers.Main` qua một rule dùng chung thay vì cài
tay trong `@Before`, để loại bỏ path gây lỗi "Dispatchers.Main is used concurrently with setting it"
khi chạy trong `runTest`.

## ADDED Requirements
### Requirement: Unit test VM cài Main qua rule dùng chung
Mọi test class view-model SHALL cài `Dispatchers.Main` qua `testutil/MainDispatcherRule` (`@get:Rule`), và SHALL NOT gọi `Dispatchers.setMain`/`resetMain` trực tiếp trong `@Before`/`@After`.
#### Scenario: StatsViewModelTest không còn cài tay
- **WHEN** đọc `StatsViewModelTest.kt`
- **THEN** không còn lời gọi `Dispatchers.setMain(` hay `Dispatchers.resetMain(` trong class
- **AND** class có `@get:Rule val mainDispatcherRule = MainDispatcherRule()`
#### Scenario: Chạy lặp dưới tải không còn đỏ
- **WHEN** chạy lặp `StatsViewModelTest` nhiều lần dưới tải host
- **THEN** không xuất hiện lỗi "Dispatchers.Main is used concurrently with setting it"
