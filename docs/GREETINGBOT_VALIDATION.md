# Kiểm thử Greetingbot — 18/11/2026

## Đã xác minh

- Android `:greetingbot:assembleDebug`: thành công với Gradle 7.4, JDK 17, SDK 29, build-tools 30.0.3.
- Hai module Android hiện có `:app:assembleDebug` và `:cloudmobile:assembleDebug`: biên dịch thành công sau khi thêm module Greetingbot.
- `dotnet build LeHoangCloudServer -c Release`: thành công, 0 cảnh báo / 0 lỗi.
- `node scripts/test-greeting-integration.mjs`: **39 kiểm tra thành công**, dùng SQLite và server riêng, không gửi lệnh chuyển động tới robot thật.
- Các trường hợp đã kiểm tra: phân quyền quản trị/thiết bị, cách ly mã theo robot, thiết bị offline, app chạy nền, SDK mất quyền hoặc bị tiếp quản, chưa định vị, bản đồ cũ, điểm không tồn tại/không thể tới, chống trùng điều hướng, ưu tiên dừng, ACK cuối không bị lùi trạng thái, cấu hình sai khoảng, đồng bộ cấu hình, microphone đang bận, khởi động lại server, hết hạn lệnh, mất phản hồi và không phát lại chuyển động.
- Web trên server kiểm thử: tab Greetingbot, ghép nối, trạng thái offline và lưu cấu hình hoạt động qua giao diện trình duyệt.
- Script cập nhật Windows: đã chạy publish sang thư mục phiên bản mới, sao lưu DB, đọc lại mã DPAPI và chạy server thử nghiệm; bản ghi robot và cấu hình được giữ nguyên.
- APK đã được `adb install -r` thành công qua `192.168.0.23:5555`; package riêng `com.lehoangrobotics.greetingbot`, version `1.0.0`.
- Robot thử nghiệm: Android 9, model `OS_R_SD03`, ROM `V11.9.2026052915.MINUUO`. SDK kết nối và đọc được bản đồ đang sử dụng cùng **6 điểm**. Map Tool đã xác minh là `com.ainirobot.maptool`.
- Nút test đã yêu cầu được quyền microphone và thu âm 5 giây trên robot. Kiểm tra phần cứng phát hiện `AudioTrack.MODE_STATIC` có trạng thái `STATE_NO_STATIC_DATA` trước lần ghi đầu; bản APK cuối đã sửa kiểm tra này và đã cài lại.

## Cần hoàn tất tại nơi vận hành

- Kiểm tra nghe thực tế và xác nhận chu trình phát lại sau sửa `AudioTrack` trên bản APK cuối.
- Định vị robot đúng vị trí thật trong Map Tool rồi thử đi tới điểm và dừng. Lần kiểm tra SDK gần nhất báo chưa định vị, nên chưa thực hiện chuyển động vật lý.
- Cập nhật server Windows `192.168.0.123:5050` bằng `scripts/Update-GreetingServer.ps1`, giữ đúng `DATABASE_PATH`, sau đó ghép nối app với server này. Chưa có quyền thực thi từ xa/đường dẫn DB trên máy server để triển khai trực tiếp.
- Sau khi ghép nối, xác nhận Web nhận bản đồ/điểm thật và kết quả lệnh từ SDK. Server kiểm thử trên máy phát triển dùng cổng 5051, không phải bản đang chạy trên 192.168.0.123.

Không coi các kiểm tra giả lập giao thức là bằng chứng robot đã di chuyển hoặc người vận hành đã nghe âm thanh. Không thay đổi vị trí robot, bản đồ, app mặc định khởi động hay dữ liệu Fleet hiện có trong quá trình cài thử.
