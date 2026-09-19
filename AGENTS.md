# Quy tắc làm việc cho AI — PodomoroApp

Repository chính thức: https://github.com/nvdung1607/PodomoroApp. Thư mục làm việc hiện tại được liên kết bằng remote origin. Dùng nhánh feature/fix cho triển khai sau baseline, không force-push hoặc ghi đè lịch sử remote. Kiểm tra remote trước khi đồng bộ.

## Đọc trước khi sửa
Đọc docs/PRD.md, docs/ARCHITECTURE.md, docs/BACKLOG.md và tiêu chí liên quan trong docs/ACCEPTANCE.md. docs/HANDOFF.md là điểm bắt đầu và báo cáo bàn giao hiện tại. Yêu cầu trực tiếp của người dùng có ưu tiên cao hơn tài liệu này.

## Phạm vi và cách thực hiện
- Mỗi lượt triển khai một ticket đủ nhỏ trong backlog; chọn ticket có dependency đã hoàn thành. Không tự mở rộng sang đồng bộ, tài khoản, AI chat hoặc backend.
- Trước khi code: ghi ticket, kết quả mong đợi và cách kiểm chứng. Sau khi code: báo file thay đổi, lệnh kiểm tra, kết quả thật và phần chưa kiểm chứng.
- Không coi viết xong code là hoàn thành. Chỉ đánh dấu DONE khi đạt tiêu chí nghiệm thu; nếu thiếu máy thật thì ghi VERIFY_DEVICE, nếu bị chặn thì ghi BLOCKED kèm nguyên nhân.
- Không bịa kết quả test, ảnh chụp hoặc khả năng hoạt động nền. Phân biệt process death, vuốt khỏi recent, force-stop và reboot.
- Không ghi đè thay đổi có sẵn; không xóa dữ liệu, sửa applicationId hoặc nâng/hạ hàng loạt dependency để né lỗi.
- Giữ một module app, chia theo feature. Không tạo abstraction/framework vượt nhu cầu.
- Không cần xin lại phép cho chỉnh sửa thông thường trong ticket đã được giao. Hỏi khi có lựa chọn làm mất dữ liệu hoặc thay đổi phạm vi sản phẩm.

## Quy ước code
- Kotlin, Compose Material 3; ViewModel + StateFlow, state đi xuống và event đi lên.
- UI không gọi DAO, điều khiển DND hoặc tính trạng thái timer trực tiếp. Inject Clock/Scheduler để test timer.
- Room là nguồn dữ liệu chính; DataStore chỉ chứa cài đặt. Không lưu trạng thái sản phẩm chỉ bằng remember.
- Chuỗi giao diện dùng resources, mặc định tiếng Việt. Hỗ trợ chữ lớn, mô tả accessibility và dark mode.
- Dùng version catalog. Kiểm tra tương thích với AGP hiện có trước khi thêm Room/KSP/Hilt; không áp cấu hình Kotlin Android cũ vào AGP mới theo trí nhớ.
- Tiền tố package hiện tại: com.trustMePro.podomoroapp. Giữ nguyên cho đến khi có quyết định khác.
- Migration Room phải giữ dữ liệu. Không dùng destructive migration cho dữ liệu người dùng.
- Không log nội dung ghi chú cá nhân. Không commit local.properties, keystore, token hay bản sao lưu người dùng.

## Kiểm chứng
- Chạy build và test liên quan sau thay đổi code; lint tại mốc tích hợp. Không lặp lại kiểm tra không có lý do.
- Test logic có giá trị: trạng thái timer, hoàn thành idempotent, task filters, thống kê theo ngày, migration/restore. Không chỉ test giá trị hằng hoặc chi tiết triển khai.
- Thay đổi UI phải kiểm tra trạng thái rỗng, lỗi, chữ dài và bàn phím. Chức năng nền/DND cần bằng chứng máy thật.
- Không commit thay người dùng nếu chưa được giao; cung cấp trạng thái thay đổi để review.
