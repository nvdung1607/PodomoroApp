# Đặc tả sản phẩm v0.1

Ngày: 2026-09-19. Trạng thái: cơ sở triển khai; các mặc định bổ sung bên dưới có thể điều chỉnh theo phản hồi người dùng.

## Mục đích và phạm vi
Ứng dụng Android todo list cá nhân, quản lý mục tiêu, tập trung theo Pomodoro và thống kê công việc/thời gian. Dùng ngoại tuyến, không bắt buộc tài khoản. Người dùng ghi nhanh một việc mà không phải thiết lập mục tiêu hoặc chạy giờ.

MVP: task CRUD/hoàn tác xóa, ghi chú, ngày dự định làm, deadline, ưu tiên, tìm kiếm, mục tiêu, Pomodoro, DND, lịch sử/thống kê ngày-tuần-tháng, sao lưu/khôi phục thủ công. Sau MVP: checklist, task lặp lại, nhắc việc theo giờ, widget, đồng bộ, ghi chú độc lập.

## Todo và mục tiêu
- Tạo task chỉ cần tên sau trim, không chấp nhận tên rỗng. Note là nội dung trong task, chưa phải sổ ghi chú độc lập.
- Task có trạng thái TODO/IN_PROGRESS/DONE, ưu tiên NORMAL/HIGH/LOW, plannedDate và dueDate độc lập, ghi chú, ước lượng Pomodoro tùy chọn và goalId tùy chọn.
- Inbox: task chưa có ngày dự định và chưa có mục tiêu. Task có deadline vẫn ở Inbox nếu chưa được sắp xếp.
- Hôm nay: task chưa xong có plannedDate hoặc dueDate là hôm nay; nhóm quá hạn riêng; không hiển thị trùng task. Task xong hôm nay có nhóm thu gọn.
- Sắp tới: task chưa xong có plannedDate hoặc dueDate sau hôm nay, nhóm theo ngày gần nhất tương ứng; ngày dự định và hạn được gắn nhãn riêng.
- Quá hạn: dueDate trước hôm nay và chưa DONE; ngày dự định đã qua không tự trở thành quá hạn.
- Có Tất cả, Đã hoàn thành và tìm theo tên/ghi chú. Task hoàn thành không bắt buộc có phiên.
- Bắt đầu focus cho task TODO chuyển nó sang IN_PROGRESS; không tự chuyển task DONE ngược lại. Muốn làm tiếp task DONE phải mở lại trước.
- Hoàn thành task lưu sự kiện; mở lại không xóa lịch sử. Thống kê số task hoàn thành dùng distinct taskId có sự kiện hoàn thành trong kỳ. Một task mở lại và hoàn thành lần nữa ở kỳ khác có thể được tính trong kỳ mới.
- Goal: tên, mô tả, deadline tùy chọn, ACTIVE/COMPLETED/ARCHIVED. Tiến độ = số task DONE / số task không bị xóa; mục tiêu chưa có task hiển thị “Chưa có công việc”.
- Xóa mềm task/goal và cho hoàn tác; xóa goal không xóa task, chuyển task sang độc lập. Phiên giữ snapshot tên và goalId lúc bắt đầu. Lưu trữ goal không sửa các phiên cũ.

## Pomodoro
- Mặc định 25 phút focus, 5 phút nghỉ ngắn, 15 phút nghỉ dài sau 4 focus hoàn thành. 1 Pomodoro là 1 focus hoàn thành, không phải cả vòng.
- Thời lượng tùy chỉnh số phút nguyên dương trong khoảng 1–180. Thay đổi cài đặt chỉ áp dụng pha mới.
- Trước khi chạy bắt buộc chọn đúng một task chưa DONE hoặc một goal ACTIVE. Chọn task thì goal được suy ra; task độc lập hợp lệ.
- Chỉ một pha đang hoạt động hoặc tạm dừng trên toàn ứng dụng. Không đổi liên kết trong phiên.
- Có bắt đầu, tạm dừng, tiếp tục, kết thúc sớm, bỏ qua nghỉ. Tạm dừng không tính thời gian focus.
- Focus hoàn thành được ghi đúng một lần; kết thúc sớm giữ thời gian đã làm nhưng không cộng Pomodoro. Nghỉ không tính focus.
- Hết focus không tự hoàn thành task; hiển thị kết quả và đề nghị nghỉ. Không tự chạy pha tiếp theo. Bỏ qua nghỉ không cộng focus, không sửa số focus đã đạt.
- Bộ đếm 4 phiên tăng khi focus hoàn thành; sau focus thứ 4 thì đề nghị nghỉ dài và bắt đầu vòng mới khi người dùng chạy focus kế tiếp. Không reset lúc qua ngày.
- Reboot trong phiên: phiên INTERRUPTED, không tự suy đoán phần thời gian không thể xác minh. Process death cùng boot: phục hồi dựa trên mốc thời gian đã lưu.

## DND và cuộc gọi
- Chỉ bật quy tắc DND của app khi focus đang chạy và người dùng đã cho phép. Pause/nghỉ/kết thúc thì tắt quy tắc của app.
- Yêu cầu cho phép cuộc gọi từ mọi người; không hứa vượt qua silent mode hoặc quy tắc hệ thống khác. VoIP cần test riêng.
- Không xóa thông báo, đọc nội dung thông báo hoặc dùng Accessibility để đóng app khác.
- Không có quyền DND thì timer vẫn chạy và UI thể hiện DND chưa hoạt động.
- Không sửa/tắt quy tắc do người dùng hoặc app khác sở hữu. Tình huống app bị force-stop và DND còn hiệu lực phải có hướng dẫn tắt thủ công; độ tin cậy khi app chết là điều kiện cần thử ở spike.

## Thống kê và ngày giờ
- Chọn ngày/tuần/tháng; tuần bắt đầu thứ Hai. Hiển thị tổng focus, số Pomodoro, phiên dừng sớm/bị gián đoạn, task hoàn thành và trạng thái task hiện tại.
- Mặc định múi giờ báo cáo theo thiết bị; query dùng cùng một zone cho cả kỳ. Nếu đổi múi giờ, số liệu nhóm ngày có thể thay đổi; không chỉnh timestamp gốc.
- Focus qua nửa đêm: chia active intervals theo ngày. Số Pomodoro tính ngày kết thúc. Dùng khoảng [start, end) để không đếm trùng biên.
- Thời gian pause/nghỉ bị loại. Thời gian focus là thời gian timer chạy, không khẳng định người dùng thực sự chú ý.
- Trạng thái task hiện tại được ghi nhãn riêng với “hoàn thành trong kỳ”. Số phiên và mức hoàn thành mục tiêu là hai chỉ số độc lập.

## UX và dữ liệu
4 tab: Công việc, Tập trung, Mục tiêu, Thống kê. Cài đặt trong menu. Nút thêm task dễ tiếp cận. Empty state có hành động cụ thể. Tiếng Việt, sáng/tối, accessibility.

Sao lưu JSON có schemaVersion qua bộ chọn tài liệu hệ thống. Khôi phục MVP là thay thế dữ liệu sau kiểm tra file và xác nhận rõ ràng, không merge; phải atomic và có bản sao phục hồi trước thay thế. Không khôi phục phiên đang chạy hoặc trạng thái DND từ backup.
