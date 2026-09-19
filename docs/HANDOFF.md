# Bàn giao MVP — 2026-09-19

## Kết quả triển khai
Ứng dụng đã có todo, mục tiêu, Pomodoro, DND theo quyền hệ thống, thống kê ngày/tuần/tháng, lịch sử, cài đặt và backup JSON. Tên giao diện tạm dùng: Nhịp. Chưa phải bản phát hành Google Play.

- Kotlin/Compose Material 3, Room schema v1, DataStore, Navigation Compose, DI thủ công AppContainer.
- Task: tạo/sửa/ghi chú, ưu tiên, ngày dự định/hạn chót, liên kết goal, hoàn thành/mở lại, xóa mềm/undo, tìm kiếm, nhóm Hôm nay và Sắp tới.
- Goal: tiến độ theo task, sửa/trạng thái/lưu trữ, xóa giữ task và undo liên kết.
- Timer: elapsedRealtime + BOOT_COUNT, ghi active intervals, pause/resume/abort, 4 phiên nghỉ dài, không auto-start, chống callback trùng/cũ, snapshot tên/goal lịch sử.
- Nền: AlarmManager; fallback khi thiếu exact alarm; app mở lại reconcile. UI ticker chỉ chạy khi Activity hiển thị.
- DND: rule riêng API29+, mọi cuộc gọi + báo thức được phép trong policy; API24–28 hướng dẫn thủ công.
- Thống kê: chia thời gian qua nửa đêm, loại pause/nghỉ, distinct task completion events, lọc goal và lịch sử.
- Backup: strict JSON giới hạn 20 MiB, kiểm tra quan hệ/dữ liệu, transaction thay thế, bản dữ liệu cũ private có thể xuất lại; không nhập timer hoặc DND.

## Bằng chứng đã chạy
1. `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`: PASS trên mã hiện tại. Unit tests: 11/11 (10 RulesTest có logic và 1 test mẫu).
2. Samsung SM-G990U3, Android 16/API36: RepositoryTest 7/7 PASS; UiFlowTest 1/1 PASS; DND policy test PASS. Test mẫu instrumented PASS.
3. Chạy riêng PlatformTest với quyền exact alarm được cấp từ host: 2/2 PASS, gồm rule DND cho phép mọi cuộc gọi/bật-tắt và receiver hoàn thành phiên đã lưu mà không cần Activity.
4. Đã mở APK trên Samsung, quan sát màn hình Công việc/Mục tiêu. Các screenshot đầu trong artifacts là trước đợt đồng bộ bảng màu cuối; không xem chúng là screenshot bản cuối.
5. `git diff --cached --check`: PASS. Không stage local.properties, build outputs, artifacts, keystore.
6. GitHub Actions Ubuntu/Java21: build + unit tests + lint + upload APK/reports PASS. Run: https://github.com/nvdung1607/PodomoroApp/actions/runs/35445079984 (commit 8604b9b; code ứng dụng giống APK local). Các chỉnh sửa bàn giao sau đó chỉ ở tài liệu và script test-device, không đổi code ứng dụng.

## Những lần lỗi đã xử lý
- JBR trong Android Studio thiếu jvm.cfg: dùng launcher JBR17 hợp lệ. Gradle daemon thực tế được repo pin Java21; `gradlew --version` đã xác nhận.
- AAR metadata yêu cầu compileSdk37: chỉ đổi compileSdk; giữ target36/min24 và dependency nền.
- Sửa API ZenPolicy và action mở Settings sau compile check.
- Lint Compose yêu cầu LocalResources thay Context.getString; đã sửa, không tắt detector.
- Một lượt lint chạy lúc file đang được cập nhật gây lỗi FIR; chạy lại mã ổn định PASS.
- Thu hồi exact alarm trong instrumentation làm Android đóng tiến trình: chuyển cấp/thu hồi sang host; lượt test riêng sau đó PASS.

## Việc chưa được xác minh đầy đủ
- Điện thoại mất kết nối sau các kết quả trên. RecoveryProbeTest (process death thực tế), PresentationTest (dark/large font) đã viết/build nhưng chưa chạy được.
- Chưa đo độ trễ trên phiên 25 phút/Doze, chưa thử reboot thật hoặc API24–28; logic boot thay đổi đã PASS bằng fake TimeSource trong test Room.
- Chưa thử cuộc gọi di động/VoIP thật. Kiểm tra ZenPolicy không thay thế kiểm thử tiếng chuông.
- Emulator sẵn có thiếu hypervisor driver; thử headless với và không có tăng tốc chưa đưa được thiết bị lên ADB. Không thay cấu hình BIOS/Windows hoặc cài driver.
- Quyền exact alarm đã được bật tạm trên Samsung để test riêng. Điện thoại ngắt kết nối trước khi có thể trả lại mặc định. DND policy access được test tự trả về trạng thái cũ, rule app được tắt.
- Cần test lại instrumented suite trên APK cuối khi thiết bị kết nối lại (các thay đổi cuối: nhóm task, bảng màu, ticker theo lifecycle). Xem DEVICE-TESTS.md.

## Bản dùng thử
APK: app/build/outputs/apk/debug/app-debug.apk
Copy bàn giao local: artifacts/Nhip-debug.apk (không commit vào Git).
SHA256: 2F164376C2840C903F2D27F853452FDD3DC6D8D725D34C163E74162DAEE67B44

Không xóa dữ liệu ứng dụng để cài lại. Dùng install -r. Chưa có release signing key hoặc phát hành cửa hàng.

## Git
Origin: https://github.com/nvdung1607/PodomoroApp.git. Đã commit và push MVP lên nhánh feature/offline-mvp theo yêu cầu triển khai dự án trên repository người dùng chỉ định. Commit MVP đầu: 4329b35. Remote xác nhận nhánh này; repository ban đầu rỗng nên GitHub dùng nó làm default branch. Các file cấu hình máy và kết quả build được ignore. Workflow Android checks build, chạy unit tests/lint và đính kèm APK/report; lượt đầu đã PASS như bằng chứng phía trên.

## Lượt tiếp theo
Khi có thiết bị: chạy scripts/test-device.ps1, RecoveryProbeTest hai bước theo DEVICE-TESTS.md, kiểm tra screenshot dark/large font, gọi thử có phối hợp với người dùng, cập nhật các mục VERIFY_DEVICE. Không đổi các mục này sang DONE chỉ vì build đạt.
