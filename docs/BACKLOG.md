# Backlog điều phối

Trạng thái: TODO, IN_PROGRESS, BLOCKED, VERIFY_DEVICE, DONE. Mã MVP đã triển khai; DONE chỉ dùng cho phần đã đủ kiểm chứng trong phạm vi ticket. Các phần platform/giao diện/phát hành còn VERIFY_DEVICE theo bằng chứng trong HANDOFF.md, không đồng nghĩa thiếu code.

| ID | Công việc / kết quả bàn giao | Phụ thuộc | Nghiệm thu |
|---|---|---|---|
| ENV-01 | DONE: dùng JBR 17.0.14, compileSdk 37, thêm script build và hướng dẫn | — | assembleDebug + testDebugUnitTest PASS; xem HANDOFF/BUILD |
| ENV-02 | DONE: Git local nhánh main, origin theo repository người dùng chỉ định; chưa commit/push | — | git check-ignore xác nhận local.properties, app/build, .gradle được loại trừ |
| UX-01 | VERIFY_DEVICE: đã có giao diện Compose 4 tab, dialogs và empty states; chờ dark/large font trên bản cuối | — | UI CRUD đã PASS; PresentationTest đã build, chưa chạy |
| SPIKE-01 | VERIFY_DEVICE: alarm/DND PASS Samsung API36; ADR đã ghi; còn API cũ/Doze/cuộc gọi | ENV-01 | A10–A13 một phần |
| CORE-01 | DONE: Room schema v1 export, repository, DI thủ công, DataStore, navigation | ENV-01 | Build + Room/DAO tests PASS |
| TASK-01 | DONE: thêm/sửa/note, hoàn thành/mở lại, xóa/undo | CORE-01 | RepositoryTest và UiFlowTest PASS |
| TASK-02 | DONE: bộ lọc, tìm kiếm, ưu tiên và ngày, nhóm Hôm nay/Sắp tới | TASK-01 | RulesTest PASS; build/lint bản nhóm mới PASS |
| GOAL-01 | DONE: goal CRUD/archive, liên kết, tiến độ | TASK-01 | Room tests liên kết/xóa/undo/snapshot PASS; UI build PASS |
| TIMER-01 | DONE: TimerRules + transition transaction, injected clock, intervals, idempotency | CORE-01, SPIKE-01 | RulesTest + RepositoryTest PASS |
| TIMER-02 | VERIFY_DEVICE: UI focus/settings/kết quả đã triển khai, chờ kiểm tra UI cuối trọn vòng | TIMER-01, TASK-02, GOAL-01, UX-01 | Logic A06–A09 PASS, chọn việc trước start qua UI PASS |
| PLATFORM-01 | VERIFY_DEVICE: alarm receiver PASS; process recovery/reboot có code và tests, còn probe thực tế | TIMER-02, SPIKE-01 | A10/A12/A13 một phần |
| PLATFORM-02 | VERIFY_DEVICE: DND policy/bật-tắt PASS Samsung; còn chuông cuộc gọi/VoIP thật | PLATFORM-01 | A11–A13 một phần |
| STATS-01 | DONE: báo cáo/biểu đồ/lịch sử/lọc goal, trạng thái hiện tại | PLATFORM-02 | RulesTest midnight/pause/DST/fixture/distinct events PASS |
| BACKUP-01 | DONE: export/import strict, validation, transaction, bản dự phòng | STATS-01 | Round-trip/replacement/invalid file/preserved data PASS |
| RELEASE-01 | VERIFY_DEVICE: APK debug + unit tests + lint PASS; còn checklist máy thật | BACKUP-01 | A18 một phần; chưa phát hành công khai |

## Các mốc bàn giao
M0: ENV + spike có kết quả, rủi ro platform rõ. M1: Todo/mục tiêu dùng được. M2: Pomodoro + nền/DND đã kiểm chứng. M3: Thống kê + backup + bản dùng thử.

## Definition of done cho từng ticket
Code đúng scope; build/test liên quan đạt; có các trạng thái lỗi/quyền nếu liên quan; không mất dữ liệu; cập nhật kết quả trong HANDOFF và trạng thái bảng. Test trên máy thật còn thiếu thì ghi VERIFY_DEVICE, không đánh dấu DONE. Tạo commit chỉ khi được giao, không push/publish ngầm.

## Ngoài MVP
Checklist, task lặp theo occurrence riêng, nhắc việc, widget, sync/login, ghi chú độc lập. Không làm trước khi các mốc MVP được nghiệm thu.
