# Kiến trúc và các quyết định kỹ thuật

## Baseline đã quan sát
- Một module app, màn hình Hello Android; chưa có nghiệp vụ.
- namespace/applicationId com.trustMePro.podomoroapp; minSdk 24; targetSdk 36; compileSdk 37 (ENV-01 sửa từ 36.1 theo AAR metadata).
- Version catalog hiện ghi AGP 9.3.3, Kotlin Compose plugin 2.2.10, Compose BOM 2026.02.01; Gradle wrapper 9.5.0. assembleDebug và testDebugUnitTest đã PASS với JBR 17.0.14.
- Java compile target11; launcher JBR17.0.14; daemon Java21 theo gradle-daemon-jvm.properties có sẵn. Đã xác minh bằng gradlew --version.

## Cấu trúc đề xuất
Giữ một module; bên trong package hiện tại chia app (navigation/composition root), core (time/database/settings/design), feature/tasks, feature/goals, feature/focus, feature/statistics, feature/backup. Mỗi feature có UI/ViewModel; repositories và xử lý hệ thống ở data/core. Domain use case chỉ tách khi logic cần tái sử dụng hoặc kiểm thử độc lập.

Compose -> ViewModel/StateFlow -> repository/use case -> Room hoặc Android adapter. Không để composable là chủ sở hữu timer. Đề xuất Room, DataStore, Navigation Compose, Hilt; chỉ chốt version sau kiểm tra tài liệu chính thức và build tương thích. java.time trên minSdk 24 cần core library desugaring hoặc giải pháp tương thích được kiểm chứng.

Triển khai MVP thực tế: core chứa model/database/repository/time/platform/backup; ui chứa các màn hình theo nhóm; AppViewModel và AppContainer điều phối. Dùng DI thủ công thay Hilt để giữ một module đơn giản, vẫn inject TimeSource/EndScheduler/SettingsProvider; đã bật desugaring. Xem ADR-001-platform.md. Cấu trúc feature nhiều tầng bên trên là hướng mở rộng, không phải yêu cầu tạo thư mục rỗng.

## Mô hình dữ liệu dự kiến
| Bảng | Trường chính |
|---|---|
| Goal | id UUID, title, description, dueDate?, status, createdAt, updatedAt, deletedAt? |
| Task | id UUID, goalId?, title, note, plannedDate?, dueDate?, priority, status, estimatedPomodoros?, completedAt?, createdAt, updatedAt, deletedAt? |
| TaskEvent | id, taskId, type COMPLETED/REOPENED, occurredAt |
| FocusSession | id, taskId?, goalIdSnapshot?, titleSnapshot, goalTitleSnapshot?, plannedDuration, status, startedAt, endedAt?, activeDuration, endReason? |
| FocusInterval | id, sessionId, startedAtUtc, endedAtUtc?, monotonicStart, duration, bootMarker |
| TimerState | singleton id, generation, phase, status, sessionId?, duration, remainingAtPause?, monotonicDeadline?, wallDeadline?, bootMarker, completedFocusInCycle |

UTC timestamps cho sự kiện; LocalDate cho ngày dự định/deadline. Room indexes cho ngày/trạng thái/quan hệ. Mọi mutation kết thúc phiên phải transaction + điều kiện trạng thái/generation để receiver cũ không hoàn thành phiên mới. Snapshot giữ lịch sử khi đổi tên, chuyển goal hoặc xóa mềm. Event task hỗ trợ thống kê khi mở lại.

## Timer
State machine: IDLE -> FOCUS_RUNNING <-> FOCUS_PAUSED -> AWAITING_BREAK -> BREAK_RUNNING <-> BREAK_PAUSED -> READY. Kết thúc sớm focus về READY; bỏ qua nghỉ về READY. Terminal session status: COMPLETED/ABORTED/INTERRUPTED.

Inject TimeSource và EndScheduler. Trong cùng boot, elapsedRealtime là nguồn đo duration; UI tick chỉ để vẽ. Lưu state và interval khi chuyển trạng thái; không ghi Room mỗi giây. Lịch báo hết pha mang generation/sessionId. Khôi phục process death cùng boot có thể đóng interval tại deadline, không tại thời điểm mở app lại. Clock thay đổi: duration vẫn dựa monotonic, timestamp kết thúc suy ra từ anchor và duration; ghi rõ quy tắc trong tests.

AlarmManager cho hạn giờ, kiểm tra quyền exact alarm theo OS; WorkManager không dùng để tick hay báo hết phiên đúng giây. Quyền bị từ chối: cho dùng foreground UI nhưng thông báo hạn chế tín hiệu nền. Không tự chọn foreground service type không hợp lệ để né hạn chế Android. Spike phải quyết định và ghi ADR cho lịch nền, thông báo, quyền, Doze, reboot và force-stop.

## DND
Adapter riêng theo API; ưu tiên AutomaticZenRule do app sở hữu, lưu ruleId. Chỉ active trong focus running. Kiểm tra policy access mỗi lần, reconcile với state khi mở app/nhận sự kiện hợp lệ. Không dùng “tắt DND toàn cục” làm cleanup. MinSdk 24 cần xác minh nhánh tương thích thay vì gọi API mới trực tiếp.

## Backup
Export DTO có schemaVersion, kiểm tra quan hệ/enum/date/size trước import; không chứa TimerState hoạt động hoặc ruleId. Import ở trạng thái không có phiên chạy, transaction và có khả năng rollback; lỗi không được thay đổi dữ liệu hiện tại. Không tự động tải dữ liệu ra mạng.

## Nguồn phải kiểm tra khi triển khai platform
- https://developer.android.com/reference/android/app/NotificationManager
- https://developer.android.com/reference/android/app/AutomaticZenRule
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/ui/compose/architecture
- https://developer.android.com/training/data-storage/room/migrating-db-versions

Các URL là điểm tham chiếu; ticket triển khai phải đọc tài liệu hiện hành, không coi danh sách này là bằng chứng API đã được kiểm thử.
