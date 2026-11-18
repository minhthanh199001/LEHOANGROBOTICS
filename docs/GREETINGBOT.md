# Greetingbot — Android, Web và server

Ứng dụng riêng `com.lehoangrobotics.greetingbot`, dùng `robotservice.jar` để điều khiển phần cứng và OpenAI để nhận câu gọi. Module Luckibot `:app`, Cloud Mobile và giao thức FleetHub hiện tại tiếp tục phát triển độc lập. Greetingbot được quản lý trong tab **GREETINGBOT** trên cùng Web/server ASP.NET.

## Chức năng

- Đọc tên bản đồ và các điểm trong Map Tool qua `getMapName` / `getPlaceList`; đọc lại khi trở về từ Map Tool.
- Chỉ cho đi tới điểm thuộc bản đồ hiện tại, `status=0`, sau khi `isRobotEstimate` xác nhận định vị. Dùng `startNavigation` / `stopNavigation` cùng cặp API.
- Nút dừng và test microphone luôn nằm ngoài vùng cuộn danh sách điểm.
- Ghi âm 2–15 giây rồi phát lại **âm thanh vừa ghi**, không chuyển thành TTS. Theo hướng dẫn Orion: `MIC`, 16 kHz, PCM16, đầu vào stereo; tách kênh trái và phát mono qua loa media. Âm thanh chỉ ở RAM, không tải lên server.
- Web cấu hình tốc độ tiến/xoay, khoảng kiểm tra đã tới điểm, thời gian chờ vật cản, thời gian ghi âm và mức phát lại. Cấu hình lưu SQLite trên server và bộ nhớ riêng của app.
- App vẫn đọc điểm, điều hướng và test âm thanh trực tiếp khi không có Web/server.
- Test hướng âm trên màn hình: nghe tối đa 60 giây, nhận câu gọi **Hello robot** hoặc **robot ơi**, chờ góc MicroArray hợp lệ rồi gọi `wakeUp` / `stopWakeUp`. Không xoay khi chưa có góc thực từ hệ thống.

## Giọng nói OpenAI / test hướng âm — bản thử 1.3.6

1. Mở app từ Home / Application Center của robot. Dừng điều hướng và test ghi/phát âm thanh.
2. Ngắt dây sạc trước khi kiểm tra chuyển động. Bấm **Test hướng âm**, chờ đo tiếng ồn nền khoảng 0,6 giây và dòng **OpenAI đang nghe**, đứng lệch bên trái/phải rồi nói câu gọi. Chờ robot hoàn tất và nghỉ nửa giây trước lần gọi tiếp theo.
3. Xem dòng **Nghe được** và trạng thái góc MicroArray. App chỉ xử lý câu nhận dạng cuối cùng; câu nhận sai hoặc chưa có góc không sinh chuyển động.
4. Nút **DỪNG CHUYỂN ĐỘNG**, nút dừng test, hết 60 giây hoặc rời màn hình đều kết thúc nghe và yêu cầu dừng vòng xoay đang chạy. Không tự tiếp tục sau khi quay lại app.

ROM được kiểm tra là `V11.9.2026052915.MINUUO`. Bản 1.2.0 bỏ phụ thuộc AgentOS/AppAgent và giữ lại luồng kết nối RobotApi bằng Activity như bản 1.0.0. Nhận dạng không dùng `SkillApi`: app thu `MIC` 16 kHz PCM16 stereo, tách kênh trái, phân đoạn câu nói rồi gửi WAV mono lên proxy ASP.NET. Proxy gọi `https://api.openai.com/v1/audio/transcriptions`, model **gpt-transcribe**, gợi ý tiếng Việt/Anh và hai câu gọi. Không gọi mô hình lập kế hoạch hay cấp quyền AI tự chọn chuyển động.

Audio của test ghi/phát vẫn chỉ ở RAM trên robot. Với test OpenAI, từng đoạn có hoạt động âm thanh được gửi qua server đến OpenAI theo yêu cầu người vận hành; app/server không ghi bản thu xuống file hoặc DB. Test tự tắt sau 60 giây, chỉ có một yêu cầu nhận dạng đang chạy, chặn audio quá dài/sai định dạng. Dừng test sẽ hủy ghi âm và yêu cầu mạng; phản hồi muộn không sinh chuyển động.

OpenAI trả văn bản, không trả góc MicroArray. ROM V11.9 hiện không gửi góc vào callback app; `getVoiceAngle()` trả map rỗng. Bản 1.3.0 bổ sung cầu nối chẩn đoán **PC/ADB** đọc thụ động các mẫu `SoundInfoTask` của AgentService. Không thay callback của ứng dụng thu thập dữ liệu hệ thống, không truy cập socket microphone riêng và không yêu cầu root. Callback wake chuẩn vẫn được hỗ trợ nếu ROM phát sự kiện.

Cầu nối chỉ bật với đúng SHA-256 AgentService 100.3.168 đã đối chiếu trên robot. Native `SoundInfoTask` cộng 30° vào `audio_angle` trước khi trả cho client Orion; cầu nối áp dụng cùng phép đổi tọa độ. Chỉ dùng mẫu `active=1`; giá trị khi `active=0` là góc cũ. Chọn cụm góc trong bán kính 25° có **trên 60%** số mẫu active trong cửa sổ xét. Với từ 3 mẫu, yêu cầu trải qua ít nhất 100 ms, độ tập trung tròn ≥0,92 và độ lệch lớn nhất ≤35°. Với câu ngắn chỉ có 2 mẫu, chỉ chấp nhận khi chúng cách nhau 80–180 ms và chênh nhau tối đa khoảng 10°; một mẫu đơn hoặc hai mẫu đổi hướng không đủ. Góc 0° hợp lệ; không lấy `beam_angle` làm góc người nói.

Bản 1.3.6 dùng mức PCM của ba khung mạnh liên tiếp trong cửa sổ 300 ms để tìm phần giọng rõ; một tiếng click đơn không chọn được cửa sổ. Header `X-Voice-Focus-Ms` giúp xét hướng MicroArray trong ±200 ms quanh phần đó trước, rồi mới thử cả phần có tiếng nói. Cả hai cách vẫn phải đạt điều kiện mẫu hợp lệ; không phát lệnh khi dữ liệu thật chưa đủ.

Trước khi nghe, app dùng trung vị 20 khung âm thanh để đo nền; tiếng quạt đều không tự mở một đoạn thu dài liên tục như khi khởi tạo ngưỡng cố định thấp. Một tiếng động ngắn trong lúc hiệu chỉnh không nâng toàn bộ ngưỡng nghe. Bước đo nền không lưu hoặc tải riêng audio.

Bản 1.3.3 giữ một luồng đọc PCM trong khi yêu cầu OpenAI đang xử lý, với bộ đệm RAM giới hạn 80 khung (khoảng 2,6 giây). Mỗi khung giữ thời điểm thu thật; khung cũ bị loại khi đầy. Chỉ ngắt đoạn khi bộ đệm thực sự tràn hoặc chuyển sang giai đoạn xoay; độ trễ giữa hai lần Android trả dữ liệu không tự được coi là mất mẫu. Khi robot xoay, app bỏ âm thanh/bộ đệm của giai đoạn chuyển động; câu gọi trong lúc xoay không phát thêm lệnh.

Ngưỡng mở câu là tối thiểu RMS 200 hoặc gấp 2 nền đã đo, với ít nhất 3 khung lớn và audio tối thiểu 0,45 giây. Giao diện báo microphone đang có tín hiệu hay đang chờ giọng rõ hơn. Log chẩn đoán chỉ chứa mức RMS, ngưỡng, số khung và thời gian; không ghi bản thu hoặc khóa.

Bản 1.3.6 giữ trước câu 0,5 giây, gửi một lần nhận dạng sớm tại khoảng 1,4 giây nếu câu vẫn tiếp diễn, và kết thúc khi im lặng 0,6 giây hoặc thu đủ 3 giây. Mỗi câu tối đa hai yêu cầu ASR; ID câu gọi ngăn kết quả sớm/đầy đủ gây hai lượt xoay. Nếu kết quả sớm chưa đủ, kết quả đầy đủ có thể cập nhật góc tốt hơn của cùng câu. Header `X-Voice-End-Ms` chỉ vị trí khung tiếng nói cuối; proxy không dùng tiếng nền sau đó để chọn hướng. Callback wake trực tiếp từ SDK vẫn được ưu tiên. Có góc nhưng chưa nhận đúng một trong hai câu gọi thì app báo rõ và tiếp tục nghe. Các mốc này là lịch gửi; thời gian OpenAI/mạng được đo riêng trong log.

Theo yêu cầu mới của người vận hành, **tổng mức bù là 25° theo chiều quay**, sau phép đổi tọa độ native +30°. Đổi góc SDK 0…360° sang góc có dấu trước khi bù: **+30° → +55°**, **−30° → −55°** (góc SDK 330° → 305°). Không cộng cố định +25° vào cả hai phía. Góc 0° giữ nguyên. Phép bù được giữ đầy đủ khi qua 180°: +175° → +200°, −175° → −200°; mã hóa lại 0…360° để SDK chọn đường tới cùng hướng đích.

Chỉnh tại **Cấu hình → Giọng nói OpenAI → Bù theo chiều quay** hoặc trường cùng tên trong tab Greetingbot Web; mặc định 25, khoảng -90…90°. Nếu nâng cấp thiết bị đã lưu giá trị cũ, đặt lại trường này thành 25; robot đang thử đã được lưu 25 qua giao diện app. Giá trị dương tăng độ lớn góc ở cả hai chiều; giá trị âm giảm độ lớn, dừng ở 0° thay vì đảo chiều. Web đồng bộ qua `GreetingSettings.voiceAngleOffset`; giá trị Web thay cấu hình cục bộ khi robot ghép nối/đồng bộ với server đã cập nhật. Bù dựa trên số đo thân tiếp theo dùng đích đã hiệu chỉnh, không áp dụng mức bù cấu hình lần thứ hai.

Lệnh dùng `wakeUp(reqId, angle, false, listener)` để không phân góc sang chuyển động đầu riêng. App đọc `getPosition().theta` (radian) trước/sau và tính hướng tuyệt đối cần đạt. Khi SDK kết thúc còn sai 5–45°, app được bù **tối đa một lần**, chỉ khi còn quyền điều khiển, test vẫn bật và câu gọi chưa quá hạn 8 giây. Giữ ngừng nhận câu tiếp theo trong cả lúc đo/bù; dừng test hoặc RobotOS tiếp quản hủy phần bù chưa gửi. Sai lệch lớn hơn 45°, thiếu số đo hoặc hết hạn không sinh vòng bù. Giao diện hiển thị độ quay thực/yêu cầu và sai lệch còn lại; số đo thân vẫn không thay cho kiểm chứng đúng hướng người gọi.

App gửi boot ID của chính robot và mốc bắt đầu/kết thúc đoạn thu theo đồng hồ robot. Proxy chỉ ghép mẫu cùng robot/phiên khởi động và cùng cửa sổ thu; app kiểm tra lại nguồn, mốc thời gian và độ ổn định. Kết quả nhận dạng đến trễ hơn 8 giây bị loại. Không có góc phù hợp thì không xoay. Mất PC/ADB thì test hướng âm không hoạt động; các chức năng SDK/bản đồ vẫn độc lập.

Tình trạng xác minh phần cứng được cập nhật trong `GREETINGBOT_VALIDATION.md`; bản thử chưa được coi là nghiệm thu xoay theo giọng nói cho tới khi thử thành công tại robot.

### Chạy proxy OpenAI trên Windows

Khóa OpenAI chỉ được đọc ở server qua `OPENAI_API_KEY_FILE` hoặc `OPENAI_API_KEY`; file được chỉ định có ưu tiên. Không đặt khóa này trong APK, mã JavaScript, DB hay Git. Robot dùng mã kết nối riêng qua header Bearer. Proxy không trả nội dung lỗi thô hoặc khóa từ OpenAI.

```powershell
.\scripts\Start-GreetingSpeech.ps1 -ApiKeyFile 'C:\DUONG_DAN_RIENG\OPEN AI API KEY.txt' -RobotSerial '192.168.0.23:5555' -AdbPath 'C:\DUONG_DAN_ADB\adb.exe' -ShowPairingCode
```

Script tạo mã kết nối lưu bằng DPAPI trên máy/tài khoản hiện tại, publish server riêng và mở tiến trình nền ẩn, mặc định chỉ nghe `127.0.0.1:5053`. Nó dùng DB riêng cho proxy thử, không thay DB/server Fleet đang chạy. Có `-RobotSerial` thì script tự tạo ADB reverse và chờ cả OpenAI lẫn luồng MicroArray sẵn sàng. Cầu nối hướng âm chỉ cho phép listen loopback. Có thể khôi phục reverse sau khi robot/ADB khởi động lại:

```powershell
adb -s 192.168.0.23:5555 reverse tcp:5053 tcp:5053
```

Trên robot: **Cấu hình → Giọng nói OpenAI**, server `http://127.0.0.1:5053`, nhập mã kết nối script tạo. Đường này cần máy Windows/proxy và kết nối ADB hoạt động. Không coi đây là triển khai trên server vận hành `192.168.0.123:5050`. Cầu nối bật mức log `SoundInfoTask=V` trong thời gian chạy; khi tắt server bình thường sẽ khôi phục mức cũ. Nếu buộc dừng tiến trình/mất kết nối, có thể xóa mức log tạm bằng `adb -s 192.168.0.23:5555 shell "setprop log.tag.SoundInfoTask ''"` sau khi dừng cầu nối.

Để chạy trên server LAN hiện có, cập nhật server bằng script giữ nguyên DB ở phần dưới, thiết lập `OPENAI_API_KEY_FILE` và `GREETING_SPEECH_TOKEN` cho tiến trình server; đổi URL giọng nói trên robot sang URL server LAN. Tham số `-OpenAiKeyFile` và `-SpeechTokenFile` của `Update-GreetingServer.ps1` hỗ trợ nạp file khóa và mã DPAPI được tạo **trên chính máy/tài khoản server**. File DPAPI không giải mã được khi sao chép sang máy/tài khoản khác.

Kiểm tra proxy bằng `GET /api/greeting/speech/status` có Bearer token; trường `direction.ready` xác nhận luồng phần cứng. `POST /api/greeting/speech/transcribe` nhận body WAV PCM16 mono 16 kHz, tối đa 8 giây; các header `X-Robot-Boot-Id`, `X-Audio-Start-Ms`, `X-Audio-End-Ms`, `X-Voice-End-Ms`, `X-Voice-Focus-Ms` dùng ghép góc. `captureStatus` chỉ chứa chẩn đoán số lượng mẫu/trạng thái/thời gian, không chứa bản thu. Token sai trả 401; chưa cấu hình trả 503; audio sai trả 400; đang bận trả 429. Không có endpoint đọc khóa OpenAI.

## Biên dịch

Yêu cầu JDK 17 (hoặc JDK tương thích Gradle 7.4), Android SDK platform 29/build-tools 30.0.3 và .NET 8 SDK. Greetingbot 1.3.6 hỗ trợ Android API 23 trở lên, không cần thư viện AgentOS.

```powershell
cd RobotSample-main
.\gradlew.bat :greetingbot:assembleDebug
cd ..
dotnet build .\LeHoangCloudServer\LeHoangCloudServer.csproj -c Release
node .\scripts\test-greeting-integration.mjs
dotnet run --project .\tests\GreetingSpeechTests -c Release
```

Nếu source nằm trên ổ mạng Windows có đường dẫn dài, chuyển riêng thư mục đầu ra build sang ổ cục bộ:

```powershell
.\gradlew.bat :greetingbot:testDebugUnitTest :greetingbot:assembleDebug -PgreetingBuildDir=C:/greetingbot-build
```

Khi đó APK nằm tại `C:/greetingbot-build/outputs/apk/debug/greetingbot-debug.apk`. Hai module Android cũ vẫn dùng cấu hình build của chúng.

APK: `RobotSample-main/greetingbot/build/outputs/apk/debug/greetingbot-debug.apk`. Bản thử MicroArray có trong `releases/LeHoangGreetingbot_v1.3.6-debug.apk`; bản 1.3.5 và các bản trước còn được giữ để khôi phục. Các bản 1.1.0, 1.3.2 và 1.3.4 đã được rút khỏi gói bàn giao sau thử nghiệm tại robot. Đây là bản debug để triển khai LAN; khi phân phối chính thức cần ký bằng khóa release của đơn vị vận hành.

Thuật toán nhận dạng/hướng âm đã được người vận hành xác nhận nhạy và chính xác, được giữ tại tag `greetingbot-v1.3.6-voice-stable`. Xem [mốc thuật toán đã xác nhận](GREETINGBOT_VOICE_STABLE.md) trước khi chỉnh sửa phần này. Thay mức bù trong cấu hình không cần sửa thuật toán thu/nhận dạng/lọc góc.

```powershell
adb connect 192.168.0.23:5555
adb -s 192.168.0.23:5555 install -r .\releases\LeHoangGreetingbot_v1.3.6-debug.apk
```

Trên robot, **mở từ Home / Application Center của RobotOS**, không dùng `am start` để thay thế bước cấp quyền SDK. Muốn tự khởi động: Developer Settings → Boot Apps → Lê Hoàng Greetingbot. Không cần thay app mặc định trong lần cài đặt thử.

## Cập nhật server Windows 192.168.0.123:5050

### Kết nối database theo các service hiện có

Hệ thống dùng file SQLite qua `Microsoft.Data.Sqlite`, không cần tài khoản SQL Server. Hai ứng dụng có database riêng:

| Thành phần | File | Quy tắc tìm đường dẫn |
| --- | --- | --- |
| Web/server — `CloudDatabaseService` | `cloud_fleet.db` | Biến môi trường `DATABASE_PATH`; nếu chưa đặt thì nằm cạnh DLL/exe server (`AppContext.BaseDirectory`). |
| Control Center — `DatabaseService` | `lehoang_fleet.db` | Nằm cạnh exe Control Center (`AppDomain.CurrentDomain.BaseDirectory`). |

`GreetingService` nhận **chính connection string của `CloudDatabaseService`**, nên dữ liệu Greetingbot nằm trong cùng `cloud_fleet.db` với Web hiện tại. Nó thêm hai bảng `GreetingRobots`, `GreetingCommands`. Các bảng Fleet, tổ chức, chi nhánh và tài khoản tiếp tục dùng dữ liệu cũ. Hai file DB có cấu trúc khác nhau; bản dành cho Control Center có cột `BatteryLevel`, còn bản Cloud dùng `Battery` và các cột pose.

Robot gửi dữ liệu/lệnh xác nhận đến HTTP API của server; robot không cần quyền mở trực tiếp file SQLite. Control Center đồng bộ với server qua các API/FleetHub hiện có.

Các file tham khảo `CloudDatabaseService.cs`, `DatabaseService.cs`, `cloud_fleet.db`, `lehoang_fleet.db` trong thư mục `ELEVATOR PROJECT` đã được đối chiếu. Kiểm tra DB có sẵn bằng **bản sao SQLite cách ly** (cần Node.js 24 trở lên):

```powershell
node .\scripts\test-greeting-integration.mjs '..\cloud_fleet.db'
```

Lệnh kiểm tra mở file nguồn ở chế độ chỉ đọc, tạo bản sao bằng SQLite Backup API, chạy server trên bản sao rồi so sánh schema/nội dung từng bảng cũ và hash file nguồn. Không đưa các file DB có tài khoản/dữ liệu vận hành này lên GitHub.

### Thao tác trên máy server

1. Cập nhật checkout từ GitHub về commit chứa Greetingbot.
2. Xác định **đúng file `cloud_fleet.db` server đang dùng** (`DATABASE_PATH`, hoặc cạnh DLL/exe của server). Không thay file này bằng DB demo hay DB từ máy khác.
3. Chạy trên chính máy server, trong thư mục checkout:

```powershell
.\scripts\Update-GreetingServer.ps1 -DatabasePath 'C:\DUONG_DAN_SERVER_HIEN_TAI\cloud_fleet.db'
```

Script publish vào thư mục phiên bản mới trước khi dừng tiến trình LeHoangCloudServer đang chiếm cổng 5050. Nó kiểm tra tên tiến trình, sao lưu DB, giữ nguyên đường dẫn DB và khởi động bản mới ở cửa sổ hiện tại. Nếu server chạy bằng Windows Service, script yêu cầu dừng đúng dịch vụ trước. Không tự thay cấu hình tự khởi động/dịch vụ cũ. Chỉ chạy một server cùng cổng; dừng tác vụ tự khởi động cũ nếu nó tự mở lại.

Mã quản trị được tạo ngẫu nhiên và lưu mã hóa bằng Windows DPAPI theo tài khoản Windows đang chạy script. Xem mã trong cửa sổ PowerShell khác trên **cùng máy, cùng tài khoản**:

```powershell
.\scripts\Update-GreetingServer.ps1 -ShowAdminKey
```

Nếu triển khai theo cách hiện có (dịch vụ, Docker...), cấu hình biến môi trường `GREETING_ADMIN_KEY` ngẫu nhiên, tối thiểu 24 ký tự, và giữ `DATABASE_PATH` hiện có. Docker Compose hỗ trợ biến này qua môi trường máy chủ. Không đưa mã vào Git. Tab Greetingbot từ chối quản lý khi chưa có mã; các màn hình Luckibot hiện tại không phụ thuộc mã này.

## Ghép nối

1. Mở `http://192.168.0.123:5050`, chọn **GREETINGBOT**.
2. Nhập mã quản trị → Kết nối quản lý → Tạo mã ghép nối với ID `GREETING-01`.
3. Lưu mã thiết bị hiển thị một lần. Trên robot: **Kết nối Web**, nhập URL `http://192.168.0.123:5050`, ID và mã thiết bị. Tên gói Map Tool mặc định `com.ainirobot.maptool`, đã xác minh trên robot.
4. Cấp quyền microphone lần đầu bằng nút test trên robot. Nếu công tắc microphone toàn hệ thống đang tắt, bật trên robot trước khi thử giọng nói.
5. Mở Map Tool, chọn đúng bản đồ và định vị tại vị trí thật. Quay về Greetingbot, kiểm tra **Đã định vị**, chọn điểm để di chuyển.

Nút **Ngắt kết nối** trong cấu hình app xóa mã thiết bị đã lưu và đưa URL về mặc định `http://192.168.0.123:5050`. Chức năng SDK trên robot vẫn dùng được khi chưa ghép nối Web.

Server chỉ lưu SHA-256 của mã thiết bị. Mã quản trị Web chỉ nằm trong bộ nhớ trang, không ghi localStorage. Nếu mất mã ghép nối, tạo ID thiết bị mới và ghép lại; bản này chưa có chức năng xoay mã/xóa Greetingbot.

## Luồng đồng bộ và giới hạn

App chủ động POST `/api/greeting/device/{id}/sync` mỗi khoảng 2 giây khi mạng hoạt động (không cần mở cổng điều khiển trên robot). Server trả cấu hình và tối đa một lệnh. Web cập nhật mỗi 2 giây; trạng thái ngoại tuyến sau 10 giây không có heartbeat. HTTP được hỗ trợ cho LAN hiện tại; khi đưa ra ngoài LAN cần HTTPS.

Các API quản trị `/api/greeting/robots...` yêu cầu header `X-Greeting-Admin-Key`; API thiết bị yêu cầu `X-Greeting-Device-Token`. Không dùng mật khẩu đăng nhập demo của Web để thay mã quản trị Greetingbot. Các API Fleet cũ giữ nguyên cơ chế của dự án hiện tại.

Lệnh chờ nhận hết hạn sau 15 giây. Server đánh dấu đã gửi trước khi trả lệnh, không phát lại khi mất phản hồi. App ghi ID đã nhận vào bộ nhớ trước khi chạy, kiểm tra thời hạn lần nữa sau bước đọc bản đồ. Lệnh dừng hủy lệnh điều hướng chưa gửi. Web phân biệt **Đã gửi**, **Đang thực hiện**, **Hoàn tất**, **Thất bại**, **Chưa nhận xác nhận**; không coi HTTP 202 là robot đã thực hiện xong.

Khi app rời màn hình hoặc RobotOS tiếp quản, app hủy tác vụ hiện tại và không tự chạy lại khi phục hồi. Không tắt xử lý dừng khẩn/sạc của RobotOS, không tự ghi tọa độ khôi phục định vị. ROM V11.9 trên robot thử nghiệm không có `current_pkg_acl`; app dùng hợp đồng kết nối/suspend/recovery chính thức trong trường hợp thiếu trường này. Khi trường tồn tại, vẫn yêu cầu giá trị cấp quyền của SDK.

Nút **Dừng điều hướng** chỉ dừng tác vụ `startNavigation` của app, không thay nút dừng khẩn vật lý. Chỉ hiển thị xác nhận dừng sau callback SDK. Mất mạng không tự dừng một chuyến đã được SDK tiếp nhận; robot tiếp tục tránh vật cản theo RobotOS. Có thể dùng nút dừng trên màn hình hoặc nút vật lý.

## Tài liệu tham khảo

- [APK / vòng đời cấp quyền](https://doc.orionstar.com/en/knowledge-base/sdk-access/)
- [Bản đồ và điểm](https://doc.orionstar.com/en/knowledge-base/map-and-position/)
- [Điều hướng](https://doc.orionstar.com/en/knowledge-base/navigation/)
- [Wake-up: xoay theo góc nguồn âm](https://doc.orionstar.com/en/knowledge-base/basic-sence/)
- [Thông số mảng microphone và phát âm thanh](https://doc.orionstar.com/en/knowledge-base/speech-ability/)
- [OpenAI: file transcription và gợi ý ngôn ngữ/từ khóa](https://developers.openai.com/api/docs/guides/speech-to-text)

Tình trạng kiểm thử triển khai thực tế được ghi trong `docs/GREETINGBOT_VALIDATION.md`.
