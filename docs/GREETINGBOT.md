# Greetingbot — Android, Web và server

Ứng dụng riêng `com.lehoangrobotics.greetingbot`, dùng `robotservice.jar` hiện có của Orion. Module Luckibot `:app`, Cloud Mobile và giao thức FleetHub hiện tại tiếp tục phát triển độc lập. Greetingbot được quản lý trong tab **GREETINGBOT** trên cùng Web/server ASP.NET.

## Chức năng

- Đọc tên bản đồ và các điểm trong Map Tool qua `getMapName` / `getPlaceList`; đọc lại khi trở về từ Map Tool.
- Chỉ cho đi tới điểm thuộc bản đồ hiện tại, `status=0`, sau khi `isRobotEstimate` xác nhận định vị. Dùng `startNavigation` / `stopNavigation` cùng cặp API.
- Nút dừng và test microphone luôn nằm ngoài vùng cuộn danh sách điểm.
- Ghi âm 2–15 giây rồi phát lại **âm thanh vừa ghi**, không chuyển thành TTS. Theo hướng dẫn Orion: `MIC`, 16 kHz, PCM16, đầu vào stereo; tách kênh trái và phát mono qua loa media. Âm thanh chỉ ở RAM, không tải lên server.
- Web cấu hình tốc độ tiến/xoay, khoảng kiểm tra đã tới điểm, thời gian chờ vật cản, thời gian ghi âm và mức phát lại. Cấu hình lưu SQLite trên server và bộ nhớ riêng của app.
- App vẫn đọc điểm, điều hướng và test âm thanh trực tiếp khi không có Web/server.

## Biên dịch

Yêu cầu JDK 17 (hoặc JDK tương thích Gradle 7.4), Android SDK platform 29/build-tools 30.0.3 và .NET 8 SDK.

```powershell
cd RobotSample-main
.\gradlew.bat :greetingbot:assembleDebug
cd ..
dotnet build .\LeHoangCloudServer\LeHoangCloudServer.csproj -c Release
node .\scripts\test-greeting-integration.mjs
```

APK: `RobotSample-main/greetingbot/build/outputs/apk/debug/greetingbot-debug.apk`. Bản bàn giao có trong `releases/LeHoangGreetingbot_v1.0.0-debug.apk`. Đây là bản debug để triển khai LAN; khi phân phối chính thức cần ký bằng khóa release của đơn vị vận hành.

```powershell
adb connect 192.168.0.23:5555
adb -s 192.168.0.23:5555 install -r .\releases\LeHoangGreetingbot_v1.0.0-debug.apk
```

Trên robot, **mở từ Home / Application Center của RobotOS**, không dùng `am start` để thay thế bước cấp quyền SDK. Muốn tự khởi động: Developer Settings → Boot Apps → Lê Hoàng Greetingbot. Không cần thay app mặc định trong lần cài đặt thử.

## Cập nhật server Windows 192.168.0.123:5050

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

Server chỉ lưu SHA-256 của mã thiết bị. Mã quản trị Web chỉ nằm trong bộ nhớ trang, không ghi localStorage. Nếu mất mã ghép nối, tạo ID thiết bị mới và ghép lại; bản này chưa có chức năng xoay mã/xóa Greetingbot.

## Luồng đồng bộ và giới hạn

App chủ động POST `/api/greeting/device/{id}/sync` mỗi khoảng 2 giây khi mạng hoạt động (không cần mở cổng điều khiển trên robot). Server trả cấu hình và tối đa một lệnh. Web cập nhật mỗi 2 giây; trạng thái ngoại tuyến sau 10 giây không có heartbeat. HTTP được hỗ trợ cho LAN hiện tại; khi đưa ra ngoài LAN cần HTTPS.

Các API quản trị `/api/greeting/robots...` yêu cầu header `X-Greeting-Admin-Key`; API thiết bị yêu cầu `X-Greeting-Device-Token`. Không dùng mật khẩu đăng nhập demo của Web để thay mã quản trị Greetingbot. Các API Fleet cũ giữ nguyên cơ chế của dự án hiện tại.

Lệnh chờ nhận hết hạn sau 15 giây. Server đánh dấu đã gửi trước khi trả lệnh, không phát lại khi mất phản hồi. App ghi ID đã nhận vào bộ nhớ trước khi chạy, kiểm tra thời hạn lần nữa sau bước đọc bản đồ. Lệnh dừng hủy lệnh điều hướng chưa gửi. Web phân biệt **Đã gửi**, **Đang thực hiện**, **Hoàn tất**, **Thất bại**, **Chưa nhận xác nhận**; không coi HTTP 202 là robot đã thực hiện xong.

Khi app rời màn hình hoặc RobotOS tiếp quản, app hủy tác vụ hiện tại và không tự chạy lại khi phục hồi. Không tắt xử lý dừng khẩn/sạc của RobotOS, không tự ghi tọa độ khôi phục định vị. ROM V11.9 trên robot thử nghiệm không có `current_pkg_acl`; app dùng hợp đồng kết nối/suspend/recovery chính thức trong trường hợp thiếu trường này. Khi trường tồn tại, vẫn yêu cầu giá trị cấp quyền của SDK.

Nút **Dừng điều hướng** chỉ dừng tác vụ `startNavigation` của app, không thay nút dừng khẩn vật lý. Chỉ hiển thị xác nhận dừng sau callback SDK. Mất mạng không tự dừng một chuyến đã được SDK tiếp nhận; robot tiếp tục tránh vật cản theo RobotOS. Có thể dùng nút dừng trên màn hình hoặc nút vật lý.

## Tài liệu Orion

- [APK / vòng đời cấp quyền](https://doc.orionstar.com/en/knowledge-base/sdk-access/)
- [Bản đồ và điểm](https://doc.orionstar.com/en/knowledge-base/map-and-position/)
- [Điều hướng](https://doc.orionstar.com/en/knowledge-base/navigation/)
- [Thông số mảng microphone và phát âm thanh](https://doc.orionstar.com/en/knowledge-base/speech-ability/)

Tình trạng kiểm thử triển khai thực tế được ghi trong `docs/GREETINGBOT_VALIDATION.md`.
