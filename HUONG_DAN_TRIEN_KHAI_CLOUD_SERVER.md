# 🚀 HƯỚNG DẪN CHI TIẾT TRIỂN KHAI LÊ HOÀNG CLOUD SERVER TỪ CON SỐ 0

Tài liệu này hướng dẫn đầy đủ các bước từ việc chuẩn bị hạ tầng Cloud, triển khai ứng dụng `LeHoangCloudServer` lên môi trường sản xuất (Production), đến việc gắn tên miền, bảo mật SSL và kết nối các chi nhánh.

---

## 📌 LỰA CHỌN MÔI TRƯỜNG MÁY CHỦ (SERVER HOSTING)

| Môi trường | Chi phí ước tính | Độ ổn định | Phù hợp với |
| :--- | :--- | :--- | :--- |
| **VPS Linux (Ubuntu 22.04 / 24.04)** *(Khuyên dùng)* | 80.000đ - 150.000đ/tháng (Vietnix, TinoHost, VPS Viettel, DigitalOcean, Linode) | ⭐⭐⭐⭐⭐ (99.9%) | Chuỗi từ 5 - 100 chi nhánh, hoạt động 24/7 không bao giờ tắt. |
| **Cloud Miễn Phí (Oracle Cloud Always Free / AWS Free Tier)** | Miễn phí (0đ) | ⭐⭐⭐⭐ (99.5%) | Giai đoạn thử nghiệm hoặc chuỗi dưới 10 chi nhánh. |
| **Windows Server / Máy tính PC cố định tại trụ sở** | Có sẵn máy tính | ⭐⭐⭐ (Phụ thuộc điện & mạng) | Triển khai nhanh trong mạng nội bộ hoặc mở Port modem. |

---

## 🐳 CÁCH 1: TRIỂN KHAI NHANH BẰNG DOCKER (KHUYÊN DÙNG TRÊN VPS LINUX)

Đây là cách chuẩn nhất, sạch nhất, chỉ mất **2 phút** và không lo lỗi môi trường.

### Bước 1: Mua hoặc tạo VPS Ubuntu
* Hệ điều hành: **Ubuntu 22.04 LTS** hoặc **24.04 LTS**.
* Cấu hình tối thiểu: 1 vCPU, 1GB hoặc 2GB RAM, 20GB SSD.

### Bước 2: Tải mã nguồn về VPS
Đăng nhập SSH vào VPS của bạn:
```bash
ssh root@<IP_VPS_CUA_BAN>
```

Tải mã nguồn từ GitHub:
```bash
git clone https://github.com/minhthanh199001/LEHOANGROBOTICS.git
cd LEHOANGROBOTICS
```

### Bước 3: Chạy script cài đặt tự động 1-click
Chỉ cần chạy 1 lệnh duy nhất:
```bash
chmod +x deploy_vps_ubuntu.sh
./deploy_vps_ubuntu.sh
```
Script này sẽ tự động:
1. Cài đặt Docker & Docker Compose.
2. Mở cổng tường lửa `5050`, `80`, `443`.
3. Đóng gói Container `lehoang-cloud-server` với .NET 8.
4. Tự động lưu cơ sở dữ liệu bền vững vào thư mục volume `lehoang_cloud_data`.
5. Thiết lập tự khởi động lại khi VPS bị reboot (`restart: unless-stopped`).

### Bước 4: Kiểm tra kết quả
Mở trình duyệt trên máy tính hoặc điện thoại bất kỳ và truy cập:
```text
http://<IP_VPS_CUA_BAN>:5050
```
Bạn sẽ thấy ngay giao diện **LÊ HOÀNG ROBOTICS - CLOUD FLEET DASHBOARD** hiển thị trực tiếp.

---

## 🐧 CÁCH 2: CHẠY NATIVE SERVICE TRÊN LINUX (KHÔNG DÙNG DOCKER)

Nếu bạn không muốn cài Docker mà muốn chạy trực tiếp bằng .NET Runtime:

### Bước 1: Cài đặt .NET 8 Runtime trên Ubuntu
```bash
sudo apt-get update
sudo apt-get install -y dotnet-sdk-8.0
```

### Bước 2: Biên dịch và đóng gói ứng dụng
```bash
cd LEHOANGROBOTICS/LeHoangCloudServer
dotnet publish -c Release -o /var/www/lehoang-cloud
```

### Bước 3: Cài đặt Systemd Service chạy nền 24/7
```bash
sudo cp lehoang-cloud.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable lehoang-cloud
sudo systemctl start lehoang-cloud
sudo systemctl status lehoang-cloud
```

---

## 🔒 CÁCH 3: GẮN TÊN MIỀN & BẢO MẬT SSL MIỄN PHÍ (HTTPS / WSS)

Để ứng dụng di động và trình duyệt kết nối an toàn qua `https://` và `wss://`:

1. **Trỏ tên miền:** 
   * Đăng nhập trang quản lý tên miền (như Cloudflare, MatBao, PA Vietnam...).
   * Tạo bản ghi `A Record`: `cloud.tenmiencuaban.com` trỏ về IP của VPS.

2. **Cài đặt Nginx & Certbot SSL:**
   ```bash
   sudo apt-get install -y nginx certbot python3-certbot-nginx
   ```

3. **Cấp phát chứng chỉ SSL miễn phí:**
   ```bash
   sudo certbot --nginx -d cloud.tenmiencuaban.com
   ```

4. **Sử dụng file cấu hình Nginx mẫu:**
   * File cấu hình mẫu đã được chuẩn bị sẵn tại: `LeHoangCloudServer/nginx_lehoang_cloud.conf`.
   * Copy vào thư mục Nginx:
     ```bash
     sudo cp LeHoangCloudServer/nginx_lehoang_cloud.conf /etc/nginx/sites-available/lehoang-cloud
     sudo ln -s /etc/nginx/sites-available/lehoang-cloud /etc/nginx/sites-enabled/
     sudo nginx -t
     sudo systemctl restart nginx
     ```

Sau khi hoàn tất, bạn có thể truy cập Cloud qua tên miền bảo mật:
```text
https://cloud.tenmiencuaban.com
```

---

## 🖥️ CÁCH 4: TRIỂN KHAI TRÊN MÁY TÍNH WINDOWS CÓ SẴN (LOCAL PC)

Nếu bạn chưa mua VPS và muốn dùng thử ngay trên 1 máy tính bàn/laptop:

1. Tải và cài đặt [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).
2. Chuột phải vào file **[deploy_windows_server.bat](file:///f:/EDU/2026/LEHOANGROBOTICS/deploy_windows_server.bat)** chọn **Run as administrator**.
3. Cửa sổ dòng lệnh sẽ tự động mở cổng tường lửa và khởi chạy server tại cổng `5050`.
4. Nếu muốn máy ngoài Internet truy cập được vào máy này: Đăng nhập vào modem Wi-Fi chính của quán (192.168.1.1) và cấu hình **NAT Port Forwarding: Cổng 5050 -> IP máy tính này**.

---

## 🔌 BƯỚC CUỐI CÙNG: KẾT NỐI CÁC CHI NHÁNH VỀ CLOUD SERVER

Sau khi Cloud Server đã chạy và có IP/Tên miền:

1. Tại máy tính chi nhánh bất kỳ, mở file **[cloud_config.json](file:///f:/EDU/2026/LEHOANGROBOTICS/LeHoangControlCenter/cloud_config.json)** trong thư mục `LeHoangControlCenter`.
2. Sửa địa chỉ Cloud thành địa chỉ máy chủ của bạn:
   ```json
   {
     "CloudServerUrl": "http://<IP_VPS_HOAC_TEN_MIEN>:5050/fleetHub",
     "Description": "Địa chỉ Cloud Server trung tâm"
   }
   ```
   *(Nếu dùng HTTPS: `https://cloud.tenmiencuaban.com/fleetHub`)*
3. Mở phần mềm `LeHoangControlCenter.exe`. 
4. Quan sát góc trái dưới cùng của màn hình:
   * Chữ **`☁️ CLOUD SYNC: ĐÃ KẾT NỐI`** hiện màu xanh ngọc.
   * Toàn bộ tọa độ robot, trạng thái pin, số lượng đơn giao của chi nhánh đó sẽ lập tức xuất hiện trực tiếp trên trang Web Cloud trung tâm!
