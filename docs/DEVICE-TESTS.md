# Kiểm thử thiết bị và bàn giao APK

## Chạy tự động
Build debug + test APK trước. `scripts/test-device.ps1 -AdbPath <adb.exe> -Serial <device-id>` cài cập nhật (không xóa dữ liệu) rồi chạy Room/timer/backup, DND, báo thức và giao diện. Test tạo dữ liệu riêng và dọn đúng các ID đã tạo; không dùng clear-data trên ứng dụng người dùng.

PlatformTest chỉ chạy exact alarm khi thiết bị đã cho phép; nếu chưa sẽ SKIP. Có thể cấp quyền trong Cài đặt của ứng dụng. Thu hồi quyền exact alarm có thể đóng tiến trình, vì vậy không thu hồi bên trong instrumentation. Nếu người kiểm thử dùng shell để cấp tạm, phải ghi trạng thái cũ và khôi phục sau khi test kết thúc.

## Probe process death
RecoveryProbeTest có hai bước độc lập, mặc định skip nếu không có argument. Chỉ chạy khi không có phiên người dùng đang hoạt động.

1. Chạy instrumentation với `-e probe stage -e class com.trustMePro.podomoroapp.RecoveryProbeTest#stage`.
2. Sau khi instrumentation kết thúc, kiểm tra `pidof` và dùng `am kill com.trustMePro.podomoroapp` khi cần; không dùng force-stop để mô phỏng system process death.
3. Chờ hơn 10 giây, không mở Activity. Báo thức phải tự tạo lại tiến trình và ghi phiên hoàn thành.
4. Chạy instrumentation với `-e probe verify -e class com.trustMePro.podomoroapp.RecoveryProbeTest#verifyAndClean` để kiểm tra và dọn các bản ghi probe. Không bỏ qua bước này nếu đã stage.

Trạng thái cũ được giữ trong file marker private của app; chỉ dữ liệu probe bị xóa khi dọn. Không stage lần hai khi marker còn tồn tại.

## Kiểm thử thủ công còn cần
- Cuộc gọi di động thật khi focus đang chạy: từ danh bạ và số lạ. Ghi trạng thái silent mode/DND có sẵn; không tự gọi số của người khác để thử.
- Cuộc gọi Zalo/Messenger: kiểm tra riêng, không suy ra từ ZenPolicy.
- Doze/tiết kiệm pin qua một phiên đủ 25 phút; đo độ trễ tiếng báo trên máy thực tế.
- Khởi động lại máy giữa phiên: mở app thấy gián đoạn, không cộng Pomodoro. Trước thử phải báo người dùng vì thao tác làm gián đoạn toàn điện thoại.
- Từ chối rồi thu hồi từng quyền, force-stop, mở lại, kiểm tra hướng dẫn tắt DND thủ công.
- API24–28: xác minh todo/timer và hướng dẫn DND thủ công; minSdk chưa được coi là đủ kiểm chứng nếu chỉ thử API36.

## Trước khi phát hành công khai
Debug APK phù hợp dùng thử cá nhân. Cần signing key riêng, versioning phát hành, khai báo quyền/chính sách cửa hàng và hoàn thành các mục máy thật trước Google Play. Không đưa keystore hoặc dữ liệu sao lưu người dùng vào Git.
