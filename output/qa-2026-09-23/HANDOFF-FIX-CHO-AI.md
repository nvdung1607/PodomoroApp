# Bàn giao sửa lỗi FocusDo cho AI tiếp theo

## Nhiệm vụ

Đọc báo cáo kiểm thử và sửa các lỗi còn tồn tại trong ứng dụng, không chỉ đưa ra nhận xét hoặc kế hoạch. Giữ nguyên mã và dữ liệu có sẵn của người dùng. Làm từng ticket nhỏ, xác minh kết quả rồi cập nhật tiến độ để có thể tiếp tục ở lượt sau.

## Đầu vào bắt buộc

- Workspace hiện tại: `E:\Android\PodomoroApp`.
- Báo cáo đầy đủ: `output/qa-2026-09-23/BAO-CAO-KIEM-THU-FOCUSDO.md`.
- Bản đọc có ảnh: `output/qa-2026-09-23/BAO-CAO-KIEM-THU-FOCUSDO.html`.
- Bằng chứng PNG/XML: cùng thư mục báo cáo. Các ID ổn định là QA-01 đến QA-32.
- Đọc `AGENTS.md`, `docs/HANDOFF.md`, `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/BACKLOG.md`, và `docs/ACCEPTANCE.md` trước khi sửa.

Báo cáo là đầu vào điều tra, không phải chân lý thay thế code hiện tại. QA-01–17 có quan sát/đo/log; QA-18–32 chủ yếu là phân tích mã nguồn. Phải kiểm tra lỗi còn tồn tại trước khi sửa; nếu nhận định không đúng, ghi rõ bằng chứng phản biện. Các dòng mã trong báo cáo có thể đã dịch chuyển.

## Đặc biệt quan trọng về checkout

Lúc audit: nhánh `feature/firebase-sync`, HEAD `07567ac`, **nhiều thay đổi chưa commit và file mới chưa tracked**. APK được test chứa cả các thay đổi này. Chỉ clone GitHub hoặc checkout HEAD cũ sẽ không có đúng bản được kiểm tra, nhất là SyncEngine/AuthService/AlarmPlayer và UI liên quan.

Trước khi làm: kiểm tra `git status --short`, `git diff`, `git remote -v` và các hướng dẫn mới của người dùng. Không reset, clean, checkout đè, stash hoặc bỏ file chưa tracked để làm repo sạch. Không commit/push nếu chưa được giao. Nếu làm ở môi trường khác, cần đúng bản source hiện tại với thay đổi chưa commit; bộ báo cáo không chứa source đầy đủ hay cấu hình bí mật.

## Cách triển khai và ưu tiên

1. **Ticket đầu tiên: QA-14 — khôi phục khả năng build instrumented tests.** Kiểm tra constructor Repository và lời gọi trong RepositoryTest; dùng tham số có tên đúng vị trí nếu lỗi còn tồn tại. Không xóa test, bỏ assertion hoặc đổi dependency để né lỗi. Build test APK mới. Chỉ chạy tests sau khi đọc tác động của chúng lên dữ liệu đang có.
2. **Ưu tiên bảo toàn dữ liệu:** điều tra QA-18–24 và QA-27. Đây là sửa lỗi trong chức năng đồng bộ/backup đã tồn tại, không phải yêu cầu mở rộng sang backend mới, tài khoản mới hoặc sản phẩm khác. Chia ticket theo dependency. Dùng dữ liệu/tài khoản thử riêng; không đổi account hoặc restore thay thế trên dữ liệu người dùng để tái hiện lỗi nguy hiểm. Nếu cần quyết định có thể mất dữ liệu hoặc thay đổi semantics sản phẩm, nêu lựa chọn cụ thể và hỏi người dùng.
3. **Luồng chính:** QA-01 điều hướng; QA-07/08/09 thống kê; QA-10 cài đặt. Mỗi sửa phải có ca regression đúng chuỗi gây lỗi trong báo cáo.
4. **UI:** QA-02–06, QA-15/16/28. Kiểm dọc/ngang, chữ 1,0×/1,6×/2×, tên dài, empty/error, bàn phím; không chỉ assert node tồn tại. Tăng khả năng đọc và bố cục co giãn, không giảm cỡ chữ toàn ứng dụng để che lỗi.
5. **Hoàn thiện UX và trạng thái:** QA-11/12/13/17/25/26/29/30/32. QA-31 là xung đột yêu cầu: tìm quyết định mới hơn của người dùng trước khi thay chính sách chu kỳ. Không tự đưa hành vi về PRD cũ nếu đã có yêu cầu mới hợp lệ.

Có thể xử lý ticket độc lập trong lúc một ticket khác thiếu thiết bị hoặc quyết định; không bỏ quên các ID còn lại. Tuân thủ quy tắc mỗi lượt một ticket nhỏ của AGENTS.md; không đổi toàn bộ 32 mục trong một diff khó review.

## Điều kiện kiểm chứng

Các lệnh từng dùng được trên máy này; xác minh đường dẫn/JDK còn tồn tại trước khi chạy:

```powershell
./scripts/build.ps1 -JdkPath 'C:/Users/dungk/.jdks/jbr-17.0.14' -Tasks ':app:assembleDebug',':app:testDebugUnitTest'
./scripts/build.ps1 -JdkPath 'C:/Users/dungk/.jdks/jbr-17.0.14' -Tasks ':app:assembleDebugAndroidTest'
./scripts/build.ps1 -JdkPath 'C:/Users/dungk/.jdks/jbr-17.0.14' -Tasks ':app:lintDebug'
& 'C:/Users/dungk/AppData/Local/Android/Sdk/platform-tools/adb.exe' devices -l
```

- Audit từng dùng Samsung SM-G990U3 API36; không giả định máy còn kết nối. Chọn serial rõ nếu có nhiều thiết bị.
- Lần audit: assembleDebug/lint qua; unit tests UP-TO-DATE với 24 kết quả không lỗi; Android test APK **không compile**. Không lặp lại tuyên bố “13/13 PASS” từ HANDOFF cũ cho bản hiện tại.
- Tests đồng bộ hiện chủ yếu là mapping, không thay thế test hai client, conflict, account isolation, pending writes, deletes, intervals/events và backup trên máy nhận.
- Tests timer cần phân biệt hết focus/hết nghỉ, callback cũ/idempotency, pause, gia hạn trên giới hạn và restore. Home không phải process death. Lệnh am kill trong audit không làm PID biến mất nên không chứng minh phục hồi process death.
- UI fix cần ảnh mới cùng tình huống với ảnh lỗi cũ. DND/âm thanh/Doze/cuộc gọi cần bằng chứng thiết bị; không suy ra âm thanh từ chữ “đang đổ chuông”.
- DONE chỉ khi đạt acceptance và có bằng chứng; thiếu máy thật dùng VERIFY_DEVICE; chặn dùng BLOCKED và ghi nguyên nhân. Phần phân tích chưa tái hiện không được báo là đã kiểm thử thành công.

## Đầu ra sau mỗi ticket

Ghi ID QA, nguyên nhân gốc, file thay đổi, hành vi trước/sau, test/lệnh và kết quả thực tế, bằng chứng mới, phần chưa kiểm chứng. Cập nhật docs/HANDOFF.md và backlog theo quy tắc repo; giữ báo cáo audit và ảnh gốc để đối chiếu, không sửa ảnh cũ thành ảnh đã fix.

Tạo sổ theo dõi sửa lỗi riêng, bao phủ đủ QA-01–QA-32 với các cột: ID, ticket, xác minh còn lỗi hay không, trạng thái, thay đổi, bằng chứng/test, phần còn thiếu. Trường hợp không phải lỗi cần giải thích, không tự đánh dấu DONE như đã sửa.

**Bắt đầu ngay bằng đọc tài liệu, kiểm tra checkout, rồi triển khai ticket QA-14 nếu lỗi vẫn còn.** Không dừng ở câu “tôi có thể sửa”. Không tự tạo nhiệm vụ khác, triển khai cloud hay phát hành ứng dụng khi chưa được giao.
