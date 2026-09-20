# Bàn giao: FocusDo • Todo & Pomodoro — 2026-09-20 (Đợt 7: Tái thiết kế Màn hình Thêm & Chỉnh sửa công việc — Quick-Add BottomSheet & Smart Attribute Chips)

## 1. Kết quả thực hiện theo yêu cầu người dùng

Đã giải quyết triệt để cảm giác "điền một cái form dài ngoằng" bằng trải nghiệm **Quick-Add BottomSheet** hiện đại hàng đầu:

### A. Chuyển sang Modal BottomSheet hiện đại thay cho AlertDialog
- Thay thế hộp thoại `AlertDialog` chật chội giữa màn hình bằng **Modal BottomSheet** chuẩn Material 3:
  - Trượt êm ái từ cạnh dưới lên, bo góc mềm mại 28dp (`topStart = 28.dp, topEnd = 28.dp`), tích hợp `DragHandle`.
  - Tự động gắn tự nhiên trên đỉnh bàn phím ảo (`imePadding`), giải phóng hoàn toàn cảm giác tù túng khi nhập liệu.
  - Header thanh lịch với icon bo tròn (`➕` khi thêm mới, `📝` khi chỉnh sửa), tiêu đề rõ ràng và nút đóng `✕`.

### B. Dẹp bỏ hoàn toàn các ô nhập chuỗi ngày tháng (`yyyy-MM-dd`) và số khô khan
- **Trước đây**: Người dùng phải nhìn thấy tới 5 ô Text box xếp chồng, trong đó có 2 ô ngày bắt gõ chuỗi `2026-09-20` và ô gõ số Pomodoro.
- **Bây giờ**: Toàn bộ được thay thế bằng thẻ **Thiết lập nhanh (Smart Attribute Chips)** chỉ cần **1 chạm**:
  - **Ngày dự định (Planned Date)**: Các chip bấm chọn tức thì: `📅 Hôm nay` (mặc định cho việc mới), `📅 Ngày mai`, `📅 Cuối tuần`, `⚪ Chờ lên lịch`. Nếu muốn chọn ngày khác: bấm `📅 Chọn ngày…` mở lịch `DatePickerDialog` Material 3 trực quan.
  - **Độ ưu tiên (Priority)**: 3 chip màu sắc trực quan: `⚪ Thấp`, `🟡 Vừa`, `🚩 Cao`.
  - **Dự tính Pomodoro (Estimate)**: Các chip cà chua trực quan: `1 🍅`, `2 🍅`, `4 🍅`, `6 🍅` và chip `+ Tùy chỉnh`.
  - **Hạn chót (Deadline)**: Các chip: `Không hạn`, `Hôm nay`, `Ngày mai`, `Cuối tuần`, `Tuần sau`, `Chọn ngày…`.

### C. Thêm việc trong 2–3 giây (Quick Add)
- Hàng **Gợi ý thông minh (Smart Suggestions)** cuộn ngang: "Đọc sách 30p", "Học tập / Lập trình", "Tập thể dục", "Viết báo cáo", "Dọn dẹp bàn", "Lên kế hoạch tuần". Chạm 1 phát là điền ngay tiêu đề.
- Ô nhập tên việc to rõ với placeholder *"Bạn muốn làm gì hôm nay?"*.
- Hỗ trợ phím **Enter (ImeAction.Done)** trên bàn phím: Gõ xong tên việc chỉ cần bấm Enter là lưu ngay lập tức!
- **Nút "Lưu" luôn ghim cố định ở đáy (Pinned Bottom Button)**: To, nổi bật với màu cam thương hiệu, không bao giờ bị cuộn mất khi nhập liệu.

### D. Chỉnh sửa công việc (Task Editor) tiện nghi & chi tiết
- Giữ nguyên các chip thông minh để đổi ngày/độ ưu tiên/Pomodoro chỉ trong 1 chạm.
- Khu vực **Danh sách việc con (Checklist / Subtasks)**: Hiển thị việc con, checkbox đánh dấu hoàn thành, sửa việc con tại chỗ, thêm nhanh việc con mới.
- Nút **"Xóa"** màu đỏ tinh tế trong vùng cuộn (có thể cuộn tới để xóa an toàn).

---

## 2. Bằng chứng kiểm chứng thực tế trên thiết bị thật (Samsung Galaxy SM-G990U3, Android 16)

### 1. Kiểm thử tự động (Unit Tests & Instrumented Tests)
- **Unit Tests**: 26/26 tests PASS 100% (`testDebugUnitTest`).
- **Instrumented Tests trên máy thật**: **13/13 tests PASS 100%** (`scripts/test-device.ps1`):
  - `UiFlowTest.createPersistCompleteReopenAndUndoDelete`: **PASS**.
  - `RepositoryTest`: **9/9 PASS**.
  - `PlatformTest`: **2/2 PASS**.
  - `PresentationTest`: **PASS**.

### 2. Ảnh chụp màn hình kiểm chứng trực tiếp từ thiết bị thật
- `screen_add_task_sheet.png`: Giao diện Quick-Add BottomSheet hiện đại mở lên từ dưới đáy, gợi ý thông minh và các chip thuộc tính 1 chạm.
- `screen_add_task_filled.png`: Chọn "Đọc sách 30p" + "2 🍅" + "🚩 Cao" bằng 3 chạm, nút "Lưu" sáng cam rực rỡ ở đáy.
- `screen_task_created.png`: Công việc được tạo thành công trên danh sách với đầy đủ huy hiệu "Cao", "🍅 0/2", "📌 Hôm nay".
- `screen_edit_task_opened.png`: Chạm vào công việc mở BottomSheet sửa với các thông tin đã điền sẵn, thẻ danh sách việc con.
- `screen_edit_task_scrolled.png`: Cuộn xuống xem việc con và nút "Xóa" công việc.

---

## 3. Danh sách tệp tin thay đổi
- `app/src/main/res/values/strings.xml`: Thêm chuỗi cho chip ngày mai, cuối tuần, tuần sau, chọn ngày, placeholder gợi ý.
- `app/src/main/java/com/trustMePro/podomoroapp/ui/TasksUi.kt`: Tái cấu trúc `TaskEditor` thành `ModalBottomSheet` với Smart Attribute Chips, DatePickerDialog, Pinned Save Button.
