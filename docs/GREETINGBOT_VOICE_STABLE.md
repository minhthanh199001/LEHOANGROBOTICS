# Mốc thuật toán giọng nói đã xác nhận — 18/11/2026

Người vận hành đã xác nhận trên Greetingbot: nhận diện chính xác và nhạy. Tag `greetingbot-v1.3.6-voice-stable` giữ mã, kiểm thử và APK của mốc này. Khi chỉ cần chỉnh góc bù, dùng cấu hình `voiceAngleOffset`; giữ nguyên thuật toán nhận dạng/ghép hướng đã xác nhận.

## Thông số của mốc

| Thành phần | Giá trị |
| --- | --- |
| Thu microphone | Orion MIC, stereo PCM16 16 kHz; tách kênh trái thành mono |
| Lưu trữ | RAM; bộ đệm 80 khung; không ghi bản thu xuống file/DB |
| Nền âm | Trung vị 20 khung; ngưỡng `max(200, nền × 2)`; ít nhất 3 khung lớn |
| Phân đoạn | Giữ trước câu 0,5 giây; nhận dạng sớm 1,4 giây; kết thúc sau im lặng 0,6 giây hoặc 3 giây thu |
| OpenAI | Proxy có token riêng; `gpt-transcribe`; gợi ý `en`, `vi`, “Hello robot”, “robot ơi” |
| Vùng giọng rõ | Mức mạnh thứ ba trong cửa sổ 300 ms; xét native trong ±200 ms quanh vùng này trước |
| Nguồn hướng | AgentService 100.3.168 đã xác minh hash; chỉ `active=1`; native +30°; ưu tiên callback wake trực tiếp hợp lệ |
| Cụm hướng | Bán kính 25°; trên 60% mẫu; độ tập trung ≥0,92; lệch tối đa 35° |
| Cặp mẫu ngắn | 2 mẫu active cách 80–180 ms, chênh góc tối đa 10°; không dùng mẫu đơn |
| Ghép câu | Cùng boot ID, cùng cửa sổ thu; trả kết quả không quá 8 giây |
| Chống lặp | Một ID câu chỉ xoay một lần dù có preview/final; nghỉ sau xoay 0,5 giây |
| Phiên thử | Tối đa 60 giây; bỏ bộ đệm/âm thanh khi đang xoay |

Các tệp chính:

- `RobotSample-main/greetingbot/src/main/java/com/lehoangrobotics/greetingbot/OpenAISpeechInput.java`
- `SpeechNoiseFloor.java`, `SpeechScanPolicy.java`, `VoiceFocus.java`, `CapturedSoundAngle.java`, `MicroArrayReading.java`, `VoiceDirectionPolicy.java`, `VoiceDirectionTest.java` trong cùng thư mục Java.
- `LeHoangCloudServer/Services/MicroArrayTelemetry.cs`, `GreetingSpeechService.cs` và `Controllers/GreetingSpeechController.cs`.

## Cấu hình góc riêng với thuật toán nhận dạng

Mặc định **25° theo chiều quay**: +30° → +55°, −30° → −55°. Trường **Bù theo chiều quay** có tại cấu hình OpenAI trên robot và tab Greetingbot của mã Web. API lưu `GreetingSettings.voiceAngleOffset`, khoảng -90…90°, trong cùng database Cloud. Giá trị Web đồng bộ xuống robot ở lượt sync kế tiếp; cấu hình giữ qua khởi động lại server.

Phép bù không bị áp dụng lại trong vòng kiểm tra heading. `wakeUp(..., false, ...)` điều khiển thân; một lượt bù theo số đo còn lại chỉ được phép trong giới hạn đã kiểm tra. Xem `GREETINGBOT.md` để biết chi tiết điều khiển.

## Bằng chứng và khôi phục

- Android: 34 test; backend Speech/MicroArray: 50 kiểm tra; Fleet/Web: 41 kiểm tra trên DB cách ly.
- Lượt thử ghi lại 5 câu đúng từ khóa có góc và hoàn tất xoay; phần nhận dạng sớm gửi sau khoảng 1,4 giây, OpenAI xử lý khoảng 0,9–1,1 giây trong lần đo.
- APK: `releases/LeHoangGreetingbot_v1.3.6-debug.apk`; SHA-256 `B3DACB3C4C4E2B3B5C81DA2241F86F04608C687855E996C5666545C3762D0B65`.
- Khi cần khôi phục, lấy APK/mã từ tag trên; cài APK bằng `adb install -r`. Giữ khóa OpenAI và token ngoài Git. Vẫn cần proxy Windows/ADB hoạt động với ROM hiện tại.

Web vận hành `192.168.0.123:5050` đã được cập nhật ngày 18/11/2026; HTTP xác nhận có tab Greetingbot và trường bù góc. Xem `GREETINGBOT_VALIDATION.md` để biết tình trạng ghép nối robot với Web vận hành. Tag giữ nguyên mã thuật toán đã được nghiệm thu trước đợt triển khai Web này.
