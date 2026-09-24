# Bàn giao: FocusDo • Todo & Pomodoro — 2026-09-24 (Đợt 9: Hoàn tất Sửa lỗi Toàn diện QA-01 — QA-32)

## 1. Mục tiêu và Kết quả thực hiện

Đã xử lý và kiểm chứng toàn bộ 32 phát hiện từ báo cáo kiểm thử QA (`output/qa-2026-09-23/BAO-CAO-KIEM-THU-FOCUSDO.md` và `output/qa-2026-09-23/HANDOFF-FIX-CHO-AI.md`).

### A. Hạ tầng & Instrumented Test (QA-14)
- **QA-14**: Khôi phục khả năng biên dịch và chạy của Android Instrumented Tests (`RepositoryTest.kt`) bằng cách cập nhật các tham số có tên (`database`, `settings`, `clock`, `scheduler`, `dnd`, `syncEngine`). Build `:app:assembleDebugAndroidTest` và thực thi thành công trên thiết bị thật: **9/9 tests PASS**.

### B. Độ tin cậy Đồng bộ Cloud & Sao lưu Dữ liệu (QA-18 — QA-24, QA-27)
- **QA-18**: Bổ sung ánh xạ đồng bộ 2 chiều cho `FocusInterval` trên Firestore; kích hoạt tải lên trong `syncAll()` và gọi `pushInterval()` khi hoàn thành/đóng phiên trong `Repository.kt`.
- **QA-19**: Bổ sung `uploadEvents()` trong `syncAll()` để đồng bộ toàn bộ lịch sử `TaskEvent` từ Room lên Firestore khi đăng nhập hoặc đồng bộ.
- **QA-20**: Cô lập dữ liệu đa tài khoản thông qua `lastSyncedUid` trong `SettingsStore.kt`; tự động kiểm tra và làm sạch cache Room trước khi nạp tài khoản mới để ngăn dữ liệu tài khoản cũ ghi đè tài khoản mới.
- **QA-21**: Triển khai giải quyết xung đột Last-Write-Wins (LWW) dựa trên `updatedAt` trong `syncAll()`. Bản ghi remote chỉ ghi đè local khi `remote.updatedAt > local.updatedAt`.
- **QA-22**: Lắng nghe sự kiện `DocumentChange.Type.REMOVED` trong snapshot listener của việc con và gọi `deleteRemoteChecklist()` khi xóa việc con ở local.
- **QA-23**: Thêm `.await()` cho các tác vụ ghi Firestore và bắt ngoại lệ cập nhật `SyncStatus.Error`, đảm bảo chỉ báo "Đã đồng bộ an toàn" khi server đã nhận.
- **QA-24**: Bọc quy trình `restore()` trong `Backup.kt` với chuỗi an toàn: tạm dừng đồng bộ `pauseSync()`, nạp Room trong transaction, đối soát `reconcile()`, đồng bộ ghi đè `syncAll(replaceRemote = true)`, và tiếp tục đồng bộ `resumeSync()`.
- **QA-27**: Chặn gia hạn thời lượng tối đa `180 phút` (10.800.000 ms) trong `Repository.kt:extendCurrentTimer` và vô hiệu hóa nút `+1m`/`+5m` trên UI khi đã đạt ngưỡng, đảm bảo không tạo dữ liệu vi phạm schema sao lưu.

### C. Khắc phục Lỗi Điều hướng & Trực quan UI/UX (QA-01 — QA-06, QA-10, QA-15, QA-16, QA-28, QA-29, QA-30)
- **QA-01**: Sửa lỗi kẹt điều hướng khi chuyển từ Task -> Focus -> Thống kê -> Công việc bằng cách chuẩn hóa điều hướng tab top-level qua `findStartDestination().id` kèm `saveState = true`, đồng thời truyền task cần tập trung qua state thay vì nested route.
- **QA-02 & QA-03**: Hỗ trợ responsive layout khi chữ lớn (`LocalDensity.current.fontScale >= 1.3f`) cho thẻ mục tiêu ngày (`StatsSettingsUi.kt`) và thẻ tóm tắt công việc (`TasksUi.kt`), ngăn tình trạng chữ bị ép thành cột 1 ký tự hoặc rớt dòng emoji.
- **QA-04**: Tách huy hiệu chu kỳ `CycleInfoBadge` ra ngoài vòng tròn đồng hồ đếm ngược `ZenDialTimer`, tránh đè chữ khi tăng cỡ chữ hoặc khi xoay ngang màn hình.
- **QA-05**: Đồng bộ màu biểu tượng thanh trạng thái (status bar) và thanh điều hướng theo chế độ sáng/tối độc lập của ứng dụng qua `WindowInsetsController`.
- **QA-06**: Tinh chỉnh màu `CoralPrimary` (`#C83E12`) và `AmberTertiary` (`#B45309`) đạt độ tương phản vượt ngưỡng chuẩn WCAG AA (> 4.5:1).
- **QA-10**: Cho phép áp dụng đổi Theme ngay lập tức trong Cài đặt ngay cả khi các trường nhập thời gian đang có lỗi định dạng, đồng thời hiển thị thông báo lỗi trực tiếp.
- **QA-15**: Bổ sung `contentDescription` và ngữ nghĩa trợ năng (semantics) cho toàn bộ các nút icon (chuyển tuần/tháng, xóa/sửa việc con, checkbox hoàn thành).
- **QA-16**: Đổi tên bộ lọc `filter_inbox` thành "Không đặt ngày" đồng nhất với giao diện tạo/sửa việc.
- **QA-28**: Thêm giới hạn chiều cao `heightIn(max = 620.dp)` và cuộn trang `verticalScroll` cho hộp thoại Đăng nhập/Đăng ký `AuthDialog`, không bị bàn phím ảo che mất nút.
- **QA-29**: Bổ sung tùy chọn "Quên mật khẩu?" và hàm gửi email đặt lại mật khẩu từ Firebase trong `AuthService.kt` và `AuthDialog.kt`.
- **QA-30**: Đảm bảo màn hình sửa task chỉ đóng sau khi transaction lưu vào Room hoàn tất thành công (`model.saveTask(task) { editTask = null }`).

### D. Hoàn thiện Logic Nghiệp vụ & Thống kê (QA-07 — QA-09, QA-11 — QA-13, QA-17, QA-25, QA-26, QA-31, QA-32)
- **QA-07**: Thêm `DayHeatmapStat` và `monthHeatmapStats` trong `Rules.kt`; lịch Heatmap tính tổng thời gian tập trung thực tế từ các khoảng `FocusInterval` thay vì nhân 25 phút cố định.
- **QA-08**: Phân biệt chuỗi tóm tắt `today_summary`, `day_summary`, `period_summary` đúng theo ngữ cảnh ngày/tuần/tháng đang chọn.
- **QA-09**: Biểu đồ cột tuần `WeeklyBarChartView` phản ánh chính xác tuần đang được chọn kèm tiêu đề khoảng ngày rõ ràng.
- **QA-11**: Thêm khả năng Hoàn tác (Undo) khi xóa việc con thông qua Snackbar phản hồi.
- **QA-12**: Thêm chú thích hướng dẫn lưu tự động cho việc con trong `TaskEditor`.
- **QA-13**: Thêm banner cảnh báo và nút cấp quyền DND khi tính năng Không làm phiền được bật nhưng chưa cấp quyền trong hệ thống.
- **QA-17**: Điều chỉnh chuỗi mô tả DND chính xác theo cơ chế cấp quyền của Android.
- **QA-25**: Bảo toàn tùy chọn `skipAuthPrompt` khi lưu cài đặt người dùng.
- **QA-26**: Phân tách thông báo hết giờ nghỉ, không báo nhầm "hoàn thành tập trung" khi kết thúc phiên nghỉ ngơi.
- **QA-31**: Bổ sung hộp thoại `PomodoroCycleDialog` giải thích rõ cơ chế chu kỳ 4 phiên, lý do tự đặt lại sau nửa đêm / 3 giờ nhàn rỗi, kèm nút "Đặt lại chu kỳ" cho người dùng chủ động điều khiển.
- **QA-32**: Bổ sung `LifecycleEventObserver` để tự động làm mới mốc ngày `today = LocalDate.now()` trong màn Thống kê khi mở lại ứng dụng qua ngày mới.

---

## 2. Bằng chứng kiểm thử & Xác minh thực tế

### A. Kiểm thử tự động (Unit Tests & Instrumented Tests)
- **Unit Tests**: `:app:testDebugUnitTest` — **BUILD SUCCESSFUL** (27 tasks UP-TO-DATE, toàn bộ test `RulesTest`, `SyncTest`, `StatisticsTest` đều PASS).
- **Instrumented Tests**: `:app:assembleDebugAndroidTest` — **BUILD SUCCESSFUL**. Chạy `RepositoryTest` trên máy thật Samsung Galaxy S21 FE (`R5CT60Q8B6L`): **9/9 tests PASS**.

### B. Kiểm thử trên thiết bị thật (Samsung Galaxy S21 FE 5G - SM-G990U3 - Android 16 API 36)
- Đã cài đặt APK debug mới nhất và kiểm tra trực tiếp tất cả các luồng sửa lỗi.
- Đã thu thập 74 ảnh chụp màn hình kiểm chứng lưu trữ tại:
  `output/qa-2026-09-23/verified_screenshots/`
  Các ảnh chính:
  - `screen_verify_tasks_direct.png`: Điều hướng mượt mà, trở lại đúng danh sách Công việc.
  - `screen_font16_stats.png` & `screen_font16_home.png` & `screen_font16_focus.png`: Hiển thị chuẩn mực ở cỡ chữ 1.6x.
  - `screen_dark_mode.png`: Thanh trạng thái sáng rõ trên nền tối.
  - `screen_verify_focus.png`: Vòng đồng hồ và huy hiệu chu kỳ tách biệt, banner cảnh báo DND.
  - `screen_verify_cycle_dialog.png`: Hộp thoại giải thích chu kỳ và nút đặt lại.
  - `screen_snackbar_undo.png` & `screen_subtask_undo.png`: Xóa việc con có nút Hoàn tác khôi phục ngay lập tức.
  - `screen_break_idle_fixed.png`: Hết giờ nghỉ báo đúng trạng thái nghỉ, không cộng sai Pomodoro.

---

## 3. Danh sách tệp tin thay đổi

| Tệp tin | Mô tả thay đổi |
|---|---|
| `app/src/androidTest/java/.../RepositoryTest.kt` | Sửa constructor `Repository` (QA-14) |
| `app/src/main/java/.../ui/AppShell.kt` | Chuẩn hóa điều hướng tab (QA-01), callback lưu task (QA-30) |
| `app/src/main/java/.../ui/FocusUi.kt` | Tách dial & cycle badge (QA-04), banner DND (QA-13), chặn 180m (QA-27), hộp thoại chu kỳ (QA-31) |
| `app/src/main/java/.../ui/TasksUi.kt` | Responsive chữ lớn (QA-03), chú thích lưu subtask (QA-12), trợ năng (QA-15) |
| `app/src/main/java/.../ui/StatsSettingsUi.kt` | Responsive chữ lớn (QA-02), Heatmap phút thực tế (QA-07), tóm tắt ngày/kỳ (QA-08), biểu đồ theo tuần (QA-09), đổi theme ngay (QA-10), bảo toàn skipAuth (QA-25), refresh mốc ngày (QA-32) |
| `app/src/main/java/.../ui/AuthDialog.kt` | Hỗ trợ cuộn chống che bàn phím (QA-28), nút Quên mật khẩu (QA-29) |
| `app/src/main/java/.../ui/theme/Color.kt` | Tinh chỉnh độ tương phản WCAG AA cho CoralPrimary & AmberTertiary (QA-06) |
| `app/src/main/java/.../ui/theme/Theme.kt` | Đồng bộ WindowInsetsController cho status/navigation bar (QA-05) |
| `app/src/main/java/.../core/Rules.kt` | Bổ sung `DayHeatmapStat` & `monthHeatmapStats` (QA-07) |
| `app/src/main/java/.../core/Repository.kt` | Phân tách hết giờ nghỉ (QA-26), chặn thời lượng <= 180m (QA-27), gọi pushInterval (QA-18) |
| `app/src/main/java/.../core/SyncEngine.kt` | Đồng bộ intervals (QA-18), events (QA-19), cô lập UID (QA-20), LWW conflict (QA-21), xóa việc con (QA-22), await/error (QA-23) |
| `app/src/main/java/.../core/Backup.kt` | Chuỗi an toàn pauseSync -> reconcile -> syncAll -> resumeSync khi restore (QA-24) |
| `app/src/main/java/.../core/AuthService.kt` | Thêm hàm gửi email đặt lại mật khẩu (QA-29) |
| `app/src/main/java/.../core/SettingsStore.kt` | Bổ sung `lastSyncedUid` (QA-20) |
| `app/src/main/java/.../AppViewModel.kt` | Hoàn tác xóa việc con (QA-11), gửi mail reset pass (QA-29) |
| `app/src/main/res/values/strings.xml` | Cập nhật nhãn thống kê, bộ lọc không đặt ngày (QA-16), mô tả DND (QA-17), chuỗi giải thích chu kỳ |
| `app/src/test/java/.../SyncTest.kt` | Unit test ánh xạ interval, event và LWW conflict |
| `output/qa-2026-09-23/SO-THEO-DOI-SUA-LOI.md` | Bảng theo dõi tiến độ chi tiết toàn bộ QA-01 — QA-32 |
