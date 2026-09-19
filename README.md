# Nhịp — Todo & Pomodoro

Ứng dụng Android cá nhân để ghi việc cần làm, theo dõi mục tiêu và tập trung theo Pomodoro. Kotlin + Jetpack Compose; dữ liệu lưu ngoại tuyến bằng Room.

## Chức năng
- Todo: ghi chú, ngày dự định, hạn chót, ưu tiên, ước lượng Pomodoro, tìm kiếm; hoàn thành/mở lại, xóa và hoàn tác.
- Mục tiêu: liên kết task, tiến độ, hoàn thành/lưu trữ.
- Pomodoro 25/5/15, nghỉ dài mỗi 4 phiên; tùy chỉnh, pause/resume, kết thúc sớm; chọn task hoặc mục tiêu.
- Lưu lịch sử phiên và khoảng thời gian thực tế; khôi phục bộ đếm khi mở lại app.
- Thống kê ngày/tuần/tháng, biểu đồ thời gian, lọc mục tiêu, lịch sử và trạng thái công việc.
- DND riêng cho phiên tập trung, cho phép cuộc gọi trong chính sách Android.
- Xuất/khôi phục JSON, có bản dự phòng trước khi thay thế dữ liệu.

## Chạy dự án
Xem [BUILD.md](docs/BUILD.md). Cần JDK tương thích (đã build với JBR 17), Android SDK platform 37; minSdk 24, targetSdk 36.

```powershell
./scripts/build.ps1 -JdkPath 'duong-dan-den-JDK'
```

APK dùng thử: `app/build/outputs/apk/debug/app-debug.apk`. Đây là debug build; chưa có signing key phát hành hoặc cấu hình Google Play.

GitHub Actions `Android checks` chạy build/unit tests/lint khi push và lưu APK/report dưới artifact `nhip-debug-and-reports`. Kiểm thử máy thật chạy riêng theo [DEVICE-TESTS.md](docs/DEVICE-TESTS.md).

## Quyền và giới hạn
Ứng dụng hướng dẫn cấp quyền thông báo, báo thức chính xác và DND khi cần. Không có quyền thì todo vẫn dùng được; báo hết giờ khi chạy nền có thể trễ nếu thiếu exact alarm.

DND tự động dùng API29+; API24–28 hướng dẫn người dùng cấu hình thủ công. Quy tắc khác của hệ thống, silent mode hoặc cách ứng dụng VoIP phân loại cuộc gọi có thể ảnh hưởng ngoại lệ cuộc gọi. Force-stop có thể hủy báo thức và để lại DND; mở Cài đặt hệ thống để tắt quy tắc Nhịp nếu cần.

Auto Backup hệ thống bị tắt để không phục hồi trạng thái timer/DND từ máy khác. Dùng xuất JSON để chuyển dữ liệu. Cài đặt cá nhân không bị thay khi import. Không có tài khoản hoặc backend.

## Tài liệu và kiểm chứng
- [Yêu cầu](docs/PRD.md), [Kiến trúc](docs/ARCHITECTURE.md), [Quyết định platform](docs/ADR-001-platform.md).
- [Backlog](docs/BACKLOG.md), [Tiêu chí](docs/ACCEPTANCE.md), [Kết quả mới nhất](docs/HANDOFF.md).
- Unit tests: RulesTest; kiểm thử Room/timer/backup trên thiết bị: RepositoryTest; DND/alarm: PlatformTest; luồng giao diện: UiFlowTest.

Không coi việc build thành công là chứng minh mọi thiết bị Android đều báo giờ hoặc nhận cuộc gọi đúng; xem các mục còn cần kiểm chứng trong HANDOFF.
