# Build trên Windows

Dùng Gradle wrapper của repository, không cần cài Gradle toàn máy.

## Chuẩn bị
- JDK launcher hoạt động (đã dùng JBR17.0.14). File gradle/gradle-daemon-jvm.properties có sẵn pin daemon Java21; Gradle dùng toolchain resolver khi cần. `gradlew --version` xác nhận Launcher JVM17 và Daemon JVM21. JAVA_HOME không ghi đè daemon criteria này.
- Android SDK có platform 37 và build tools mà AGP yêu cầu. local.properties trỏ sdk.dir của máy, không commit file này.
- compileSdk 37 để đáp ứng metadata của Core 1.19.0 và Lifecycle 2.11.0. targetSdk vẫn 36, minSdk vẫn 24.

## Lệnh tái lập
Từ thư mục repository, trong PowerShell:

```powershell
./scripts/build.ps1 -JdkPath 'C:/Users/dungk/.jdks/jbr-17.0.14'
```

Trên máy khác truyền đường dẫn JDK của máy đó hoặc thiết lập JAVA_HOME rồi chạy `./scripts/build.ps1`. Script chỉ thay JAVA_HOME trong tiến trình gọi và khôi phục khi xong, không chỉnh cấu hình Java toàn máy.

Để chạy thêm kiểm tra:

```powershell
./scripts/build.ps1 -JdkPath 'C:/Users/dungk/.jdks/jbr-17.0.14' -Tasks ':app:lintDebug'
./scripts/build.ps1 -JdkPath 'C:/Users/dungk/.jdks/jbr-17.0.14' -Tasks ':app:connectedDebugAndroidTest'
```

Lệnh connected test yêu cầu thiết bị đã cho phép USB debugging và sẽ cài APK debug/test. APK debug nằm tại app/build/outputs/apk/debug/app-debug.apk. Unit test report tại app/build/reports/tests/testDebugUnitTest/index.html.

Android Studio: chọn cùng JDK hoạt động ở Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK. Script không thay cài đặt IDE.

## Nguồn tương thích
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://docs.gradle.org/current/userguide/compatibility.html

Không nâng/hạ thư viện để né lỗi runtime. Kiểm tra lỗi metadata để chọn compileSdk phù hợp, tách biệt với targetSdk/minSdk.
