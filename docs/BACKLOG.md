# Backlog điều phối

Trạng thái: TODO, IN_PROGRESS, BLOCKED, VERIFY_DEVICE, DONE. Mã MVP đã triển khai; DONE chỉ dùng cho phần đã đủ kiểm chứng trong phạm vi ticket. Các phần platform/giao diện/phát hành còn VERIFY_DEVICE theo bằng chứng trong HANDOFF.md, không đồng nghĩa thiếu code.

| ID | Công việc / kết quả bàn giao | Phụ thuộc | Nghiệm thu |
|---|---|---|---|
| ENV-01 | DONE: dùng JBR 17.0.14, compileSdk 37, thêm script build và hướng dẫn | — | assembleDebug + testDebugUnitTest PASS; xem HANDOFF/BUILD |
| ENV-02 | DONE: Git local nhánh main, origin theo repository người dùng chỉ định; chưa commit/push | — | git check-ignore xác nhận local.properties, app/build, .gradle được loại trừ |
| UX-01 | DONE: giao diện Compose 4 tab, dialogs và empty states; PresentationTest (dark/large font 1.6x) PASS trên Samsung API36 | UI CRUD PASS; PresentationTest PASS, ảnh lưu artifacts/ui-proof |
| SPIKE-01 | VERIFY_DEVICE: alarm/DND PASS Samsung API36; ADR đã ghi; còn API cũ/Doze/cuộc gọi | ENV-01 | A10–A13 một phần |
| CORE-01 | DONE: Room schema v1 export, repository, DI thủ công, DataStore, navigation | ENV-01 | Build + Room/DAO tests PASS |
| TASK-01 | DONE: thêm/sửa/note, hoàn thành/mở lại, xóa/undo | CORE-01 | RepositoryTest và UiFlowTest PASS |
| TASK-02 | DONE: bộ lọc, tìm kiếm, ưu tiên và ngày, nhóm Hôm nay/Sắp tới | TASK-01 | RulesTest PASS; build/lint bản nhóm mới PASS |
| GOAL-01 | GỠ BỎ KHỎI UI: gỡ bỏ khỏi Navigation/UI theo yêu cầu; bảo toàn entity Room để tránh destructive migration | TASK-01 | UI loại bỏ hoàn toàn; Room tests bảo toàn dữ liệu cũ PASS |
| TIMER-01 | DONE: TimerRules + transition transaction, injected clock, intervals, idempotency | CORE-01, SPIKE-01 | RulesTest + RepositoryTest PASS |
| TIMER-02 | DONE: UI focus/settings/kết quả đã triển khai, kiểm tra UiFlowTest trọn vòng PASS trên Samsung API36 | TIMER-01, TASK-02, UX-01 | Logic A06–A09 PASS, tập trung tự do hoặc gắn task qua UI PASS |
| PLATFORM-01 | DONE: alarm receiver PASS; process recovery đã kiểm chứng bằng RecoveryProbeTest 2 bước (am kill -> alarm wake -> verify PASS) trên Samsung API36 | TIMER-02, SPIKE-01 | A10/A12/A13 đạt trên API36 |
| PLATFORM-02 | VERIFY_DEVICE: DND policy/bật-tắt PASS Samsung; còn chuông cuộc gọi/VoIP thật | PLATFORM-01 | A11–A13 một phần |
| STATS-01 | DONE: báo cáo/biểu đồ/lịch sử/lọc task, trạng thái hiện tại | PLATFORM-02 | RulesTest midnight/pause/DST/fixture/distinct events PASS |
| BACKUP-01 | DONE: export/import strict, validation, transaction, bản dự phòng | STATS-01 | Round-trip/replacement/invalid file/preserved data PASS |
| TASK-03 | DONE: Tái cấu trúc Todo List Task cha - Task con (Subtasks): inline quick-add, toggle expand/collapse, checkbox con, tiến độ x/y | TASK-02, CORE-01 | RepositoryTest + UiFlowTest + PresentationTest PASS trên máy thật Samsung API36 |
| RELEASE-01 | VERIFY_DEVICE: APK debug + unit tests + lint PASS; 13/13 instrumented tests PASS; còn checklist máy thật trước phát hành | BACKUP-01, TASK-03 | A18 đạt kiểm thử local; chưa phát hành công khai |

## Các mốc bàn giao
M0: ENV + spike có kết quả, rủi ro platform rõ. M1: Todo/mục tiêu dùng được. M2: Pomodoro + nền/DND đã kiểm chứng. M3: Thống kê + backup + bản dùng thử. M4: Mở rộng tính năng cốt lõi (Checklist).

## Definition of done cho từng ticket
Code đúng scope; build/test liên quan đạt; có các trạng thái lỗi/quyền nếu liên quan; không mất dữ liệu; cập nhật kết quả trong HANDOFF và trạng thái bảng. Test trên máy thật còn thiếu thì ghi VERIFY_DEVICE, không đánh dấu DONE. Tạo commit chỉ khi được giao, không push/publish ngầm.

## Ngoài MVP
Task lặp theo occurrence riêng, nhắc việc theo giờ, widget, sync/login, ghi chú độc lập. Không làm trước khi các mốc liên quan được nghiệm thu.
