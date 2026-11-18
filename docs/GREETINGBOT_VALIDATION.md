# Kiểm thử Greetingbot — 18/11/2026

## Bản sửa bù theo chiều quay 1.3.5

- Người vận hành làm rõ: góc dương phải +10°, góc âm phải −10°. Bản 1.3.4 cộng cố định +10° làm giảm độ lớn góc âm: log góc native 302,20° (−57,80°) gửi lệnh 312,20° (−47,80°), thân quay 46,85°. Đây là lỗi công thức; đã sửa theo chiều quay, cùng dữ liệu trên sẽ gửi 292,20° (−67,80°).
- Chuyển sang góc có dấu trước khi bù độ lớn rồi mã hóa lại cho SDK. Giữ 0°, chặn đổi chiều ở biên 180° và khi cấu hình giảm góc về dưới 0°. Giao diện robot/log hiển thị dấu góc nguồn, số bù thực tế và góc đích. Nhãn cấu hình app/Web làm rõ ý nghĩa; không đổi tên trường JSON/schema.
- Android **25 test / 0 lỗi**, build/cài 1.3.5 thành công. Kiểm tra hai chiều, góc nhỏ âm 355°, 0°/360°, biên nửa vòng, cấu hình giảm góc và vòng bù số đo không áp offset lần thứ hai. Backend/API giữ nguyên so với bản đã qua 44 kiểm tra Speech/MicroArray và 41 kiểm tra Fleet.
- Hash APK trên robot khớp `releases/LeHoangGreetingbot_v1.3.5-debug.apk`: `692D60FA08172E0799A35C0AEDAD95014B9B3488B048AAAB41FDAC2BF8F67169`. SDK vẫn kết nối/đã định vị, đọc 5 điểm trên `LH2-1006084342`.
- Đang chờ xác nhận thử thực tế hai phía với cách bù đã sửa. APK 1.3.4 được rút khỏi gói bàn giao; bản cũ còn trong lịch sử Git.

## Bản thử offset +10° / bù góc 1.3.4

- Người vận hành xác nhận 1.3.3 xoay tốt hơn, còn cần gọi lại và yêu cầu cộng thêm 10° offset. Đã đặt mặc định +10°, có trường chỉnh tại app và Web, lưu/đồng bộ trong GreetingSettings; native angle +30° vẫn giữ đúng phép đổi tọa độ của firmware.
- Đã đo thân trên 1.3.3: lệnh 31,02° → thân 31,45°; 32,78° → 33,35°; 42,33° → 38,77°; 179,99° → 164,50°; 134,27° → 124,77°. Vì vậy callback hoàn tất vẫn có trường hợp sai 9–15° so với chính lệnh gửi.
- Thêm một lượt bù có giới hạn, dựa trên heading trước/sau và quy ước chiều quay đã quan sát. Chỉ bù sai 5–45°, tối đa một lần và trong hạn 8 giây; kiểm tra quyền/test/generation trước khi gửi, giữ ngừng nhận câu mới trong giai đoạn đo/bù. Dừng giữa hai lượt hủy lượt chưa gửi; vẫn có watchdog 12 giây.
- Giữ trước câu 0,5 giây, khoảng im lặng cuối câu 0,8 giây; chọn góc chỉ đến khung có tiếng nói cuối để giảm ảnh hưởng âm thanh nền cuối bản thu. Ưu tiên góc wake trực tiếp của SDK nếu hợp lệ; làm rõ trạng thái có góc nhưng chưa đúng câu gọi.
- Android **22 test / 0 lỗi**; backend Speech/MicroArray **44 kiểm tra thành công**; Fleet/Greetingbot integration **41 kiểm tra thành công** trên DB riêng. Bao gồm offset/vòng 360°, chiều bù hai phía, giới hạn/hết hạn bù, không lấy hướng từ tiếng nền cuối câu, giới hạn offset và đồng bộ Web → thiết bị.
- Đã cài 1.3.4; proxy OpenAI và MicroArray báo streaming. APK `releases/LeHoangGreetingbot_v1.3.4-debug.apk`, SHA-256 `5EB8A337D40834C5991760C855BD5947A103EF2E1AF6797537FA67940079BAA7`.
- Người vận hành báo xoay chưa đúng và làm rõ phải bù theo dấu; công thức cộng cố định được thay ở 1.3.5. Chưa nghiệm thu hướng âm chính xác trên bản này.

## Bản thử bắt câu ngắn / trạng thái microphone 1.3.3

- Sửa điều kiện ngắt đoạn thu của luồng PCM liên tục: dùng thế hệ bộ đệm khi tràn/đổi sang xoay, không coi khoảng cách giữa hai lần read hơn 200 ms là mất mẫu. Hạ ngưỡng mở câu và số khung lớn tối thiểu; bổ sung chỉ báo microphone và số đo RMS/khung (không lưu audio).
- Android **18 test / 0 lỗi**, build/cài thành công. Đã quan sát nhiều đoạn PCM thật tiếp tục gửi tới proxy/OpenAI trong một phiên; âm thanh nền trả văn bản rỗng, không phát lệnh xoay. Độ trễ giữa một số lần read lên đến 213–230 ms, chứng minh điều kiện cũ có thể ngắt đoạn dù Android vẫn trả đủ dữ liệu.
- APK hiện tại: `releases/LeHoangGreetingbot_v1.3.3-debug.apk`, SHA-256 `ACCE51D43D89773EB0C1C3ED871893BB95EB16F4BAF55FE8AF08B93636DCE986`.
- Đang chờ câu gọi thật và số đo trước/sau để xác minh nhận dạng và độ quay; chưa nghiệm thu lỗi xoay thiếu góc. Backend giữ bản đã qua 41 kiểm tra.

## Bản thử thu liên tục / đo góc thân 1.3.2

- Người vận hành xác nhận 1.3.1 nhận câu gọi và xoay, nhưng phải gọi nhiều lần và đôi khi xoay thiếu góc. Đây là lỗi còn mở, không coi callback hoàn tất là đã đối diện đúng hướng.
- Đã thay việc dừng AudioRecord trong lúc gọi OpenAI bằng luồng đọc PCM riêng, bộ đệm RAM giới hạn và timestamp lúc thu. Bộ đệm bị xóa khi xoay; khung quá cũ/đứt quãng không được ghép thành câu mới.
- Dùng overload `wakeUp(..., needMoveHead=false, ...)` đã kiểm tra trong RobotService jar. Bổ sung đọc hướng thân trước/sau qua `getPosition`; quy đổi radian và xử lý vòng ±π để đo độ xoay thực, chưa áp dụng hệ số bù góc suy đoán.
- Android **18 test / 0 lỗi**, build/cài APK thành công; SDK kết nối, đã định vị, 5 điểm vẫn được đọc. Hash APK trên robot khớp `releases/LeHoangGreetingbot_v1.3.2-debug.apk`: `FF0BBB378E868AACD4CAABEBB0CE4BDC6DCF7AA4F0D99281D79A1C829A46D7F6`.
- Người vận hành báo bản này không ghi nhận câu gọi/thực hiện lệnh; log chỉ có một đoạn nền nhận văn bản rỗng. Đã thay bằng 1.3.3 và rút APK 1.3.2 khỏi gói bàn giao. Chưa có số đo góc thân ở lần này. Backend dùng bản đã qua 41 kiểm tra của 1.3.1.

## Bản thử ổn định hướng âm 1.3.1

- Lần thử 1.3.0 tiếp theo, log đã ghi nhận **“robot ơi” → góc 330,45° → SDK hoàn tất wakeUp**; người vận hành xác nhận **robot xoay 1 lần, các lần sau không xoay**. Các lần bị chặn vẫn nhận đúng câu gọi, nhưng bộ lọc toàn đoạn báo `unstable_direction`.
- Đối chiếu mẫu native thấy một cụm hướng chính quanh 280–300° xen các mẫu nhảy về 0° hoặc hướng khác trong cùng đoạn thu. Bản 1.3.1 chọn cụm đa số rõ ràng thay vì bắt tất cả mẫu trong đoạn đồng nhất; giữ điều kiện active, cùng boot ID/cửa sổ thu, ít nhất 3 mẫu/100 ms, trên 60% số mẫu active và độ tập trung/độ lệch của cụm.
- App đo trung vị 20 khung tiếng nền trước khi nghe, tránh ngưỡng khởi tạo thấp làm tiếng quạt mở đoạn thu 4,5 giây liên tục. Giao diện yêu cầu chờ hiệu chỉnh xong trước khi nói.
- Android **16 test / 0 lỗi**, build thành công. Backend **41 kiểm tra thành công**, bổ sung trường hợp góc chủ đạo có nhiễu, vòng 360°, hai nguồn ngang nhau và đa số yếu phải bị loại. Kiểm tra VAD xác nhận nền quạt đều không mở đoạn nói, vẫn nhận mức giọng cao hơn nền và không bị một tiếng click làm lệch hiệu chỉnh.
- Đã cài APK 1.3.1; hash trên robot khớp file bàn giao `releases/LeHoangGreetingbot_v1.3.1-debug.apk`: `CAC0BAE244566CA441D355193F81404070D1A8D1CB80D31B1C5032A09DB21936`. SDK kết nối/đã định vị, 5 điểm vẫn được đọc.
- Trong lần thử tiếp theo trên 1.3.1, log ghi nhận **5 câu gọi có góc hợp lệ và 5 callback `wakeUp` hoàn tất**: “robot ơi” tại 30° (6 mẫu được giữ trong 8 mẫu active), 317,43° (35/37), 340,45° (11/11), 320,53° (15/21), và “Hello robot” tại 336,11° (9/9). Trước đó có một câu bị loại do không có cụm góc đủ rõ; câu không nhận dạng được không sinh chuyển động.
- Người vận hành sau đó xác nhận có xoay, nhưng cần gọi nhiều lần và có lúc xoay chưa tới; phần ổn định/độ chính xác chưa được nghiệm thu. Xem bản 1.3.2 ở trên.

## Bản thử MicroArray 1.3.0

- Người vận hành xác nhận bản 1.2.0 đã nhận đúng câu gọi qua OpenAI, nhưng chưa có góc nên không xoay. SDK/map vẫn hoạt động.
- Đã xác minh luồng góc thật của MicroArray: AgentService 100.3.168 ghi `SoundInfoTask` khoảng 10 Hz qua log verbose. SHA-256 APK hệ thống trên robot khớp `27a3aa8f6b263cff696b69dee8afcd3218f1989456a3000d9e3f38d334a3c555`. Phép cộng 30° của native getter được áp dụng khi chuyển `audio_angle` sang hệ tọa độ client Orion. Không commit firmware/decompile/log hệ thống.
- Cầu nối PC/ADB đọc thụ động luồng này; không thay callback VoiceDataCollection đang dùng bởi ứng dụng hệ thống. Đối chiếu boot ID đọc bằng UID của chính app, mốc thời gian thu và độ ổn định; bỏ mẫu `active=0`, góc lỗi, dữ liệu cũ/sai phiên và nguồn không được hỗ trợ.
- Android: **13 test / 0 lỗi**, build APK thành công. Backend: **37 kiểm tra thành công**, gồm định dạng góc, phép hiệu chỉnh +30°, góc 0°/vòng 360°, số mẫu tối thiểu, mẫu inactive, tiếng từ nhiều hướng, cửa sổ thời gian và các kiểm tra OpenAI trước đó. Fleet integration: **39 kiểm tra thành công** trên DB riêng.
- Đã cài bản 1.3.0 qua ADB Wi-Fi; SDK kết nối, đã định vị, bản đồ `LH2-1006084342`, 5 điểm. Proxy `127.0.0.1:5053` báo `direction.ready=true`, `state=streaming`.
- Đã quan sát app nhận góc phần cứng khớp đoạn thu: 30° (3 và 8 mẫu), 27,18° (7 mẫu), 10,69° (20 mẫu). Cả chuỗi native → proxy → Android đã thông. Các đoạn này chưa nhận đúng câu gọi, nên không phát lệnh `wakeUp`. Những đoạn góc không ổn định bị loại.
- Lần thử 1.3.0 đầu, người vận hành báo **không cử động**; log còn lại không có câu gọi hợp lệ/lệnh xoay. Đã bổ sung chẩn đoán số mẫu, nguyên nhân không ghép được góc và số ký tự nhận dạng; không ghi audio hoặc khóa. Lần sau đã có một lần xoay được xác nhận, nhưng chưa ổn định; xem bản 1.3.1 ở trên.
- APK bàn giao `releases/LeHoangGreetingbot_v1.3.0-debug.apk`, SHA-256 `72C939EDA01ED9C7277A2E35716EFC1278232A966765D57EDE598C9222123ED3`.
- Bản này vẫn cần PC/proxy/ADB hoạt động. Server vận hành `192.168.0.123:5050` chưa được triển khai lại; không thay DB Fleet hiện tại.

## Bản thử OpenAI 1.2.0

- `:greetingbot:testDebugUnitTest` và `:greetingbot:assembleDebug`: thành công; **10 test / 0 lỗi**. Bao gồm hai câu gọi, dấu tiếng Việt, góc sai/hết hạn, trùng callback, WAV PCM16 mono tương thích proxy, gắn góc với đúng đoạn thu, loại góc của người nói/câu trước và phản hồi ASR trễ.
- `dotnet run --project tests/GreetingSpeechTests -c Release`: **23 kiểm tra thành công** bằng HttpMessageHandler giả, không dùng audio/khóa thật. Kiểm tra auth, giới hạn WAV, endpoint/model/ngôn ngữ/từ khóa gửi OpenAI, giữ nguyên audio, chặn yêu cầu đồng thời, ưu tiên file khóa được chỉ định và không lộ khóa qua lỗi upstream.
- Chạy lại `scripts/test-greeting-integration.mjs`: **39 kiểm tra thành công** trên DB riêng, không chuyển động thật. Server build 0 cảnh báo / 0 lỗi.
- Khóa đọc trực tiếp từ file người vận hành chỉ định ở ngoài repo. Kiểm tra OpenAI `/v1/models` trả **200**, model `gpt-transcribe` có sẵn. Một yêu cầu WAV im lặng 0,5 giây qua proxy tới OpenAI trả **200, text rỗng**; không dùng câu trả giả làm kết quả nhận dạng robot.
- Kiểm thử API thật bổ sung: dùng Windows SAPI tạo âm thanh tổng hợp “Hello robot” (không phát ra loa), gửi qua proxy/OpenAI trả **200, “Hello robot.”**. Đây là kiểm tra nhận dạng API bằng âm thanh tổng hợp, không thay cho thử giọng người tại robot và không kích hoạt chuyển động.
- Proxy thật trên Windows hiện tại: `127.0.0.1:5053`, token riêng lưu DPAPI, khóa OpenAI không đưa xuống robot. Kiểm tra HTTP thật: không có token trả **401**, WAV sai trả **400** mà không gọi OpenAI. Robot truy cập qua `adb reverse tcp:5053 tcp:5053`; cấu hình giọng nói đã lưu bằng giao diện app, độc lập cấu hình Web cũ.
- Đã cài 1.2.0, mở từ Home. SDK kết nối, **đã định vị**, bản đồ `LH2-1006084342`, **5 điểm**. Đã quan sát các đoạn thu thực từ robot đi qua proxy/OpenAI và callback về app. Sau đó người vận hành xác nhận nhận đúng câu gọi, nhưng SDK chưa báo góc xoay.
- APK trên robot khớp `releases/LeHoangGreetingbot_v1.2.0-debug.apk`, SHA-256 `22EC117EB99F0CCF568C321ECFEA02F729A8B3DA5681D4130CFC0A6213BEB191`.
- Góc MicroArray: chưa quan sát được sự kiện hợp lệ đi vào app. Interface `getVoiceAngle()` trong ROM hiện tại có implementation trả map rỗng; callback VoiceDataCollection dùng chung đang có app hệ thống kết nối, không thay callback này để chiếm luồng dữ liệu. Chỉ dùng callback wake qua SDK/system khi có góc thật. **OpenAI trả văn bản không thay thế việc lấy góc từ phần cứng. Phần xoay chưa hoàn tất/nghiệm thu.**
- Bản 1.1.0 dùng SkillApi/AgentOS không đạt: người vận hành báo nghe sai, không xoay, rồi mất SDK/bản đồ. Đã bỏ AgentOS/AppAgent khỏi app và khôi phục `RobotApi.connectServer(Activity, listener)` như 1.0.0. APK 1.1.0 bị rút khỏi thư mục bàn giao; 1.0.0 được giữ để khôi phục.
- Server vận hành `192.168.0.123:5050` chưa cập nhật; cấu hình Web hiện tại trên robot trả HTTP **405**. Proxy OpenAI thử nghiệm chạy riêng, không thay đổi DB/server vận hành này.

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
