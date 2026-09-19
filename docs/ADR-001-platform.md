# ADR 001 — timer nền, DND và dependency

Quyết định triển khai 2026-09-19, bằng chứng test được bổ sung trong HANDOFF khi chạy xong.

- Đo duration bằng elapsedRealtime cùng BOOT_COUNT. Chỉ ghi segment khi pause/finish. Reboot không suy đoán duration chưa được xác minh; đánh dấu INTERRUPTED.
- Dùng PendingIntent + AlarmManager ELAPSED_REALTIME_WAKEUP, setExactAndAllowWhileIdle khi có quyền; nếu thiếu dùng setAndAllowWhileIdle và hiển thị cảnh báo trễ. Không có foreground service hoặc WorkManager tick mỗi giây.
- Chuyển trạng thái trong Room transaction, generation loại callback cũ. Receiver goAsync, thao tác database ngắn trong coroutine IO. App mở lại reconcile các side effect chưa thực hiện nếu crash giữa commit và schedule.
- AutomaticZenRule với ZenPolicy cho phép mọi cuộc gọi và báo thức; tắt âm thanh khác, hạn chế visual effects. Chỉ điều khiển rule app sở hữu. API29+ hỗ trợ điều khiển theo rule; API24–28 hỗ trợ timer/todo, DND có hướng dẫn thủ công vì không sửa chính sách toàn cục của người dùng.
- Force-stop do người dùng dừng alarms: không cam kết báo đúng giờ; có hướng dẫn kiểm tra/tắt rule thủ công. Chưa bảo đảm VoIP được hệ thống phân loại đúng là cuộc gọi.
- Không sao lưu tự động toàn database qua Android Auto Backup: tránh phục hồi TimerState/boot/ruleId sai thiết bị. Dùng backup JSON có validation, không chứa timer/ruleId và có bản dự phòng trước restore.
- Dùng DI thủ công qua AppContainer cho một module nhỏ thay vì thêm Hilt; TimeSource, EndScheduler và SettingsProvider được inject để test độc lập. Navigation Compose và DataStore vẫn theo kiến trúc.
- Room 2.8.4, KSP 2.3.9 (KSP2), DataStore 1.1.7, Navigation 2.8.8, desugar_jdk_libs 2.1.5. Không đổi các dependency nền đã có. Schema v1 được export để hỗ trợ migration sau này.

Nguồn:
- https://developer.android.com/reference/android/app/NotificationManager#setAutomaticZenRuleState(java.lang.String,%20android.service.notification.Condition)
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://github.com/google/ksp/releases/tag/2.3.9
