# Tiêu chí nghiệm thu

Kết quả ngày 2026-09-19: A01–A09 và A14–A17 có kiểm thử logic/Room/UI tương ứng đã PASS; A10–A13 và A18 mới được kiểm chứng một phần trên Samsung API36, còn VERIFY_DEVICE cho process death thực tế, Doze, reboot, cuộc gọi, API cũ và giao diện cuối. Chi tiết bằng chứng/phạm vi trong HANDOFF.md; không coi toàn bộ checklist bên dưới đã đạt.

| ID | Tình huống và kết quả bắt buộc |
|---|---|
| A01 | Tạo task chỉ có tên; tên trắng bị từ chối; mở lại app vẫn còn task và note tiếng Việt |
| A02 | Hoàn thành không cần timer; mở lại giữ lịch sử; hoàn thành lặp trong cùng kỳ chỉ tính một task distinct |
| A03 | Xóa task rồi undo khôi phục đầy đủ; task đã xóa không làm mất focus history |
| A04 | plannedDate hôm nay/deadline ngày mai chỉ hiển thị một dòng Hôm nay; dueDate hôm qua vào quá hạn; plannedDate hôm qua không có deadline không bị gắn quá hạn; tìm tên/note và loại task đã xóa |
| A05 | Goal có 2/4 task DONE hiển thị 50%; goal không task không chia cho 0; xóa goal giữ task độc lập và snapshot lịch sử |
| A06 | Bốn focus hoàn thành đưa ra nghỉ dài; phiên abort không tăng đếm; nghỉ không tăng focus; không tự chạy pha kế tiếp |
| A07 | Focus 25 phút, chạy 10, pause 5, chạy 15: 25 phút focus, 1 Pomodoro; pause không được tính |
| A08 | Abort sau 10 phút: 10 phút focus, 0 Pomodoro; complete callback lặp không nhân đôi session; callback generation cũ không tác động phiên mới |
| A09 | Chọn task thì goal suy ra đúng; task độc lập hợp lệ; task DONE không chọn được; đổi tên/chuyển goal không đổi lịch sử phiên cũ; cài đặt mới không sửa pha đang chạy |
| A10 | Khóa màn hình/đổi app/Doze/process death cùng boot: countdown phục hồi và chỉ kết thúc một lần; ghi độ trễ tín hiệu quan sát được, không chỉ kiểm tra UI |
| A11 | Có quyền: DND của app bật khi focus, pause/nghỉ/kết thúc tắt rule app; cuộc gọi di động từ số bất kỳ được phép theo policy; kiểm tra silent mode/DND khác và VoIP riêng; không tắt rule người dùng |
| A12 | Từ chối/thu hồi quyền notification, DND, exact alarm: không crash; UI chỉ rõ khả năng còn hoạt động; không báo DND bật khi chưa active |
| A13 | Reboot ghi INTERRUPTED; force-stop không được quảng cáo vẫn báo đúng giờ; kiểm tra rule DND còn sót và đường tắt thủ công; phân biệt với vuốt recent |
| A14 | Phiên 23:50–00:15 liên tục: ngày trước 10 phút, ngày sau 15 phút và 1 Pomodoro. Pause 23:55–00:05 thì loại 10 phút pause |
| A15 | Query tuần/tháng tại biên dùng [start,end), không trùng; đổi múi giờ regroup nhất quán; đổi giờ máy không làm duration âm; DST trong timezone hỗ trợ không làm sai tổng duration |
| A16 | Fixture: 2 focus hoàn thành x25 phút + abort 10 phút + nghỉ 5 phút = 2 Pomodoro, 60 phút focus. Task hoàn thành trong kỳ và task hiện đang DONE có nhãn/phép tính riêng |
| A17 | Backup/restore round-trip giữ task/goal/events/history; JSON sai phiên bản/quan hệ hoặc bị cắt không thay dữ liệu; hủy chọn file không lỗi; import không tự khởi động timer/DND |
| A18 | Build/unit tests/lint đạt; kiểm tra font lớn, dark mode, xoay màn hình, nút accessibility, tên dài, empty state; APK cài và mở được trên máy thật |

## Ma trận tối thiểu
Emulator API 24 và target API 36 (hoặc image tương ứng sẵn có, ghi rõ thiếu sót); ít nhất điện thoại Android thực tế của người dùng cho báo giờ/DND. Mở rộng Samsung/Xiaomi/Pixel khi phát hành rộng. Không coi emulator là bằng chứng đầy đủ cho tối ưu pin OEM.
