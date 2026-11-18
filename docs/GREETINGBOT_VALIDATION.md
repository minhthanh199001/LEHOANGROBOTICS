# Kiểm thử Greetingbot — 18/11/2026

## Đã xác minh

- Android `:greetingbot:assembleDebug`: thành công với Gradle 7.4, JDK 17, SDK 29, build-tools 30.0.3.
- Hai module Android hiện có `:app:assembleDebug` và `:cloudmobile:assembleDebug`: biên dịch thành công sau khi thêm module Greetingbot.
- `dotnet build LeHoangCloudServer -c Release`: thành công, 0 cảnh báo / 0 lỗi.
- `node scripts/test-greeting-integration.mjs`: **39 kiểm tra thành công**, dùng SQLite và server riêng, không gửi lệnh chuyển động tới robot thật.
- `node scripts/test-greeting-integration.mjs '..\cloud_fleet.db'`: **42 kiểm tra thành công** trên bản sao SQLite của DB tham khảo người vận hành cung cấp. Schema/nội dung cả 5 bảng Fleet cũ được giữ nguyên, hai bảng Greetingbot nằm trong cùng DB, hash file nguồn không thay đổi. `GreetingService` dùng trực tiếp connection string của `CloudDatabaseService`.
- Các trường hợp đã kiểm tra: phân quyền quản trị/thiết bị, cách ly mã theo robot, thiết bị offline, app chạy nền, SDK mất quyền hoặc bị tiếp quản, chưa định vị, bản đồ cũ, điểm không tồn tại/không thể tới, chống trùng điều hướng, ưu tiên dừng, ACK cuối không bị lùi trạng thái, cấu hình sai khoảng, đồng bộ cấu hình, microphone đang bận, khởi động lại server, hết hạn lệnh, mất phản hồi và không phát lại chuyển động.
- Web trên server kiểm thử: tab Greetingbot, ghép nối, trạng thái offline và lưu cấu hình hoạt động qua giao diện trình duyệt.
- Script cập nhật Windows: đã chạy publish sang thư mục phiên bản mới, sao lưu DB, đọc lại mã DPAPI và chạy server thử nghiệm; bản ghi robot và cấu hình được giữ nguyên.
- Đã chạy cập nhật lần nữa trên cổng thử nghiệm đang có server: script nhận diện/dừng đúng tiến trình, khởi động phiên bản mới và giữ dữ liệu.
- APK đã được `adb install -r` thành công qua `192.168.0.23:5555`; package riêng `com.lehoangrobotics.greetingbot`, version `1.0.0`.
- Robot thử nghiệm: Android 9, model `OS_R_SD03`, ROM `V11.9.2026052915.MINUUO`. SDK kết nối và đọc được bản đồ ban đầu cùng **6 điểm**. Sau khi vận hành chọn/định vị bản đồ khác trong Map Tool, app tự cập nhật **5 điểm**, SDK xác nhận **đã định vị**. Map Tool đã xác minh là `com.ainirobot.maptool`.
- Nút test đã yêu cầu được quyền microphone. Kiểm tra phần cứng phát hiện `AudioTrack.MODE_STATIC` có trạng thái `STATE_NO_STATIC_DATA` trước lần ghi đầu; đã sửa và cài lại APK. Chu trình thu 5 giây và phát lại hoàn tất trên robot, không có lỗi AndroidRuntime.
- Ghép nối robot thật với server kiểm thử cổng 5051 qua ADB reverse. Server nhận đủ bản đồ, 5 điểm và trạng thái SDK/định vị thật. Lệnh `refresh_places` có ACK **completed** từ app.
- Lệnh `microphone_test` từ server kiểm thử có ACK **completed**. Đã quan sát đủ trạng thái **recording → playing → idle**; cấu hình ghi âm **3 giây** được chuyển từ Web xuống robot và áp dụng.
- Đã ngắt ghép nối kiểm thử bằng giao diện app, xác minh xóa token/URL kiểm thử và gỡ ADB reverse. App đã cài bản cuối, đang mở ở màn hình chính; URL mặc định trở về `192.168.0.123:5050`, chờ ghép nối server vận hành. SHA-256 APK trên robot khớp file bàn giao trong `releases/`.

## Cần hoàn tất tại nơi vận hành

- Người vận hành nghe thử để đánh giá chất lượng/âm lượng thực tế; hệ thống đã xác nhận hoàn tất chu trình thu/phát, chưa có xác nhận nghe từ người tại robot.
- Thử đi tới điểm được người vận hành lựa chọn và dừng. Robot đã định vị; chưa gửi lệnh di chuyển vật lý trong quá trình kiểm thử.
- Cập nhật server Windows `192.168.0.123:5050` bằng `scripts/Update-GreetingServer.ps1`, giữ đúng `DATABASE_PATH`, sau đó ghép nối app với server này. Chưa có quyền thực thi từ xa/đường dẫn DB trên máy server để triển khai trực tiếp.
- Ghép nối lại với server vận hành sau cập nhật. Luồng bản đồ/điểm và ACK đã kiểm tra với robot thật trên server kiểm thử cổng 5051, không phải bản đang chạy trên 192.168.0.123.

Không coi các kiểm tra giả lập giao thức là bằng chứng robot đã di chuyển hoặc người vận hành đã nghe âm thanh. Không thay đổi vị trí robot, bản đồ, app mặc định khởi động hay dữ liệu Fleet hiện có trong quá trình cài thử.
