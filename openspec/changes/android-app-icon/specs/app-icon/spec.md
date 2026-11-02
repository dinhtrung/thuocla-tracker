# Spec Delta

## Purpose

Cho app một icon launcher đúng bản sắc: mark của PWA, nền navy `#1a1a2e`, tự thích ứng mọi hình dạng mask của Android 8+ và có lớp một màu cho Themed icons của Android 13+. Không có icon thì người dùng chỉ thấy robot mặc định và không nhận ra app trên màn hình chính.

## ADDED Requirements

### Requirement: Icon khai báo và hiển thị trên launcher

`<application>` SHALL khai `android:icon="@mipmap/ic_launcher"` và `android:roundIcon="@mipmap/ic_launcher_round"`, và bản build SHALL nhúng icon thật thay vì để trống.

#### Scenario: Cài APK và nhìn màn hình chính

- **WHEN** cài `app-debug.apk` lên máy/AVD rồi mở launcher
- **THEN** app hiện mark điếu thuốc có khói trên nền navy, không phải icon robot mặc định của Android
- **AND** `aapt2 dump badging app-debug.apk` in `application: label='Thuốc lá' icon='res/...'` với đường dẫn **khác rỗng**, và có dòng `application-icon-*` cho từng mật độ

#### Scenario: Mask tròn và squircle không cắt mất mark

- **WHEN** launcher dùng mask tròn (hoặc squircle của OEM) trên icon adaptive
- **THEN** toàn bộ điếu thuốc và khói nằm trong vùng an toàn 66dp, không bị cắt góc, không lệch tâm

### Requirement: Icon một màu cho Themed icons

Bộ asset SHALL có lớp `monochrome` để Android 13+ tô màu icon theo hình nền hệ thống.

#### Scenario: Bật Themed icons

- **WHEN** người dùng bật "Themed icons" trong cài đặt hệ thống (Android 13+)
- **THEN** icon app hiện dạng silhouette một màu, vẫn nhận ra được là điếu thuốc có khói, không còn mảng navy riêng

### Requirement: Không lệch bản sắc với PWA

Mark SHALL là đúng artwork của PWA (`smoking-tracker/icon-512.png`), cùng nền `#1a1a2e`.

#### Scenario: So icon app với icon PWA

- **WHEN** đặt icon app cạnh icon PWA trên cùng một màn hình
- **THEN** hai mark trùng hình, trùng tông; chỉ khác cách mask của từng nền tảng
- **AND** không có chữ, số hay nhãn nào được thêm vào icon
