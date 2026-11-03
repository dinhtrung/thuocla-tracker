# Ứng dụng Android — Thuốc Lá Tracker

App native (Kotlin + Jetpack Compose) thay cho PWA: chạy 100% offline, không xin quyền
`android.permission.INTERNET`, dữ liệu nằm trong Room/SQLite của app. APK cài trực tiếp
(sideload), không lên Google Play.

## Build

Yêu cầu: **JDK 17** (Java 25 có sẵn trên máy này nhưng sai cho AGP 8.7.3) và **Android SDK 35**.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/dinhtrung/Android/Sdk
cd android
./gradlew :app:assembleDebug
```

APK debug ra ở `android/app/build/outputs/apk/debug/app-debug.apk`. Build lần đầu trên worktree
mới sẽ lâu hơn (Gradle daemon + tải dependency) — cứ để chạy.

Kiểm thử: `./gradlew :app:testDebugUnitTest`.

## Release (ký bằng keystore local, không commit)

Keystore nằm **ngoài repo**, không bao giờ được commit:

```bash
mkdir -p ~/keystores
keytool -genkeypair -v -keystore ~/keystores/thuocla-release.jks \
  -alias thuocla -keyalg RSA -keysize 4096 -validity 10000
```

Đường dẫn và mật khẩu đọc từ **gradle properties** (file `~/.gradle/gradle.properties`, ngoài
repo), đúng bốn khoá sau:

```properties
thuoclaStoreFile=/home/dinhtrung/keystores/thuocla-release.jks
thuoclaStorePassword=<mật khẩu store>
thuoclaKeyAlias=thuocla
thuoclaKeyPassword=<mật khẩu key>
```

`android/app/build.gradle.kts` đọc chúng bằng `providers.gradleProperty(...)`. Thiếu bất kỳ khoá
nào thì build release vẫn cấu hình được và chỉ tạo APK **chưa ký** — nhờ vậy clone mới và CI build
được mà không cần bí mật.

Build và copy APK ra ngoài repo:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/dinhtrung/Android/Sdk
cd android
./gradlew :app:assembleRelease
mkdir -p ~/thuocla-apk
cp app/build/outputs/apk/release/app-release.apk ~/thuocla-apk/thuocla-v1.0.0.apk
```

`versionName` lấy từ `android/app/build.gradle.kts` (hiện là `1.0.0`), nên tên file đổi theo phiên
bản. Kiểm tra chữ ký:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs ~/thuocla-apk/thuocla-v1.0.0.apk
```

## Cài & nhập bản sao

```bash
adb install -r ~/thuocla-apk/thuocla-v1.0.0.apk
```

Khôi phục dữ liệu từ PWA:

1. Trên PWA (v1.5.2) xuất bản sao JSON — file tên `thuocla_backup_YYYY-MM-DD.json`.
2. Chuyển file sang điện thoại (USB, Drive, chat…).
3. Mở app → tab **Dữ liệu** → **Nhập bản sao JSON** → chọn file.
4. Chọn **THAY THẾ** (xoá toàn bộ dữ liệu đang có rồi nhập) hoặc **GỘP** (chỉ thêm bản ghi chưa có).
   Trùng phút trong cùng ngày sẽ bị bỏ qua, không ghi đè.

Xuất ngược lại cũng ở tab **Dữ liệu**: **Xuất bản sao JSON** (đúng định dạng PWA) hoặc
**Xuất CSV (6 cột)**. Cả hai dùng Storage Access Framework, app không xin quyền bộ nhớ.

## PWA đóng băng

PWA đã **đóng băng tính năng ở v1.5.2**; app Android này là hướng phát triển tiếp theo.
