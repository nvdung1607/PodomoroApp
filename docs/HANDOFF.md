# Bàn giao: FocusDo • Todo & Pomodoro — 2026-09-22 (Đợt 8: Rà soát & Khắc phục Lỗi UI toàn diện — Bottom Navigation, Nút Điều khiển Bấm giờ & Trình chỉnh sửa)

## 1. Kết quả thực hiện theo yêu cầu người dùng

Đã giải quyết triệt để các lỗi UI phát hiện trên thiết bị thực tế:

### A. Chuẩn hóa Thanh điều hướng dưới đáy (Bottom Navigation Bar) & Thanh bên (Landscape Rail)
- **Trước đây**: Dùng `Surface` tùy biến bao trọn cả cột (icon + chữ) với khung viền cam và chiều cao 68dp, khiến icon `⏱` bị ép sát mép viền trên (cách chỉ ~2px), bố cục méo và lệch chuẩn Material 3.
- **Bây giờ**: Chuyển hoàn toàn sang chuẩn Material 3 `NavigationBar` và `NavigationBarItem` (cùng `NavigationRail` khi xoay ngang màn hình):
  - Khung màu cam (active indicator pill) chỉ bao quanh riêng icon, căn giữa hoàn hảo.
  - Nhãn chữ bên dưới hiển thị thanh thoát, cỡ chữ 12sp, đậm khi chọn.
  - Không còn viền cứng bao quanh toàn bộ nút, loại bỏ hoàn toàn hiện tượng ép sát mép.

### B. Khắc phục Nút "Kết thúc sớm" bị vỡ dòng và chạm viền
- **Trước đây**: 3 nút `[+1 phút]`, `[+5 phút]` và `[⏹ Kết thúc sớm]` bị dồn vào 1 hàng ngang với `weight = 1f`. Nút "⏹ Kết thúc sớm" có quá ít khoảng trống khiến chữ bị rớt dòng thành `⏹ Kết thúc \n sớm` và chạm sát vào viền nút.
- **Bây giờ**: Tách biệt rõ ràng theo thứ bậc hành vi người dùng trong `FocusUi.kt`:
  - Hàng 1: Nút chính lớn `[ ⏸ Tạm dừng ]` / `[ ▶ Tiếp tục ]` (chiều cao 52dp).
  - Hàng 2: Hai nút gia hạn `[ +1 phút ]` và `[ +5 phút ]` cân đối nằm ngang (chiều cao 44dp).
  - Hàng 3: Nút `[ ⏹  Kết thúc sớm ]` dạng Outlined màu đỏ viền cảnh báo, chiếm trọn 1 dòng rộng rãi (chiều cao 46dp), không bao giờ bị rớt dòng hay chạm viền.

### C. Loại bỏ Trùng lặp Huy hiệu Tạm dừng trên màn hình Bấm giờ
- **Trước đây**: Khi bấm tạm dừng, vừa có huy hiệu `[⏸ Đã tạm dừng]` ở phía trên đồng hồ, vừa có một khung nhãn `[Đang tạm dừng]` khác nằm ngay dưới số phút `29:11` bên trong mặt đồng hồ.
- **Bây giờ**: Loại bỏ khung nhãn thừa bên trong `ZenDialTimer`, giữ lại huy hiệu trên cùng giúp mặt đồng hồ sạch sẽ, tối giản và thanh thoát.

### D. Đồng bộ hóa & Tối ưu hóa UI toàn app
- **Thứ tự chip ngày trong TaskEditor**: Chuẩn hóa cả ngày dự định và hạn chót theo đúng một trình tự: `Hôm nay` → `Ngày mai` → `Cuối tuần` → `Tuần sau` → `Không đặt ngày`.
- **Đơn vị phút trên chip nghỉ ngơi**: Chuẩn hóa định dạng `3p`, `5p`, `10p`, `15p` trong `BreakCardContent` đồng nhất với toàn app (thay vì `$mins'`).
- **Nút Sao lưu & Khôi phục**: Đổi cặp nút "Xuất bản sao lưu" và "Khôi phục từ tệp" trong `StatsSettingsUi.kt` sang dạng 2 nút full-width để chữ tiếng Việt dài không bị cắt hoặc rớt dòng.

---

## 2. Bằng chứng kiểm chứng thực tế trên thiết bị thật (Huawei Mate 50 Pro - BLT0222B02001337)

### 1. Kiểm thử tự động (Unit Tests & Instrumented Tests)
- **Unit Tests**: Pass 100% (`testDebugUnitTest`).
- **Instrumented Tests trên máy thật**: **13/13 tests PASS** (`scripts/test-device.ps1`):
  - `UiFlowTest.createPersistCompleteReopenAndUndoDelete`: **PASS**.
  - `RepositoryTest`: **7/7 PASS**.
  - `PlatformTest`: **PASS**.
  - `PresentationTest`: **PASS**.

### 2. Ảnh chụp màn hình kiểm chứng trực tiếp từ thiết bị thật
- `screen_tasks.png`: Thanh điều hướng M3 với active pill cam bao trọn icon, nhãn căn chỉnh chuẩn mực.
- `screen_focus_idle.png`: Màn hình bấm giờ tạm dừng: chỉ còn 1 huy hiệu tạm dừng trên đỉnh, 2 nút `[+1 phút]`, `[+5 phút]` cân đối, nút `[⏹ Kết thúc sớm]` rộng rãi 1 dòng.
- `screen_stop_dialog.png`: Hộp thoại xác nhận kết thúc sớm rõ ràng, nút bấm phân cấp hợp lý.
- `screen_focus_ready.png`: Trạng thái sẵn sàng tập trung sạch sẽ, thẩm mỹ cao.
- `screen_stats.png`: Màn hình thống kê với lưới chỉ số và biểu đồ 7 ngày ngay ngắn.

---

## 3. Danh sách tệp tin thay đổi
- `app/src/main/java/com/trustMePro/podomoroapp/ui/AppShell.kt`: Dùng M3 `NavigationBar` + `NavigationBarItem` và `NavigationRail` + `NavigationRailItem`.
- `app/src/main/java/com/trustMePro/podomoroapp/ui/FocusUi.kt`: Tách hàng nút điều khiển, bỏ huy hiệu tạm dừng trùng lặp trong dial, chuẩn hóa chip nghỉ ngơi.
- `app/src/main/java/com/trustMePro/podomoroapp/ui/TasksUi.kt`: Đồng bộ thứ tự chip ngày và nhãn ước tính Pomodoro.
- `app/src/main/java/com/trustMePro/podomoroapp/ui/StatsSettingsUi.kt`: Chuyển nút sao lưu/khôi phục sang full-width để tránh rớt dòng.
