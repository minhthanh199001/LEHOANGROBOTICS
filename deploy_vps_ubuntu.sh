#!/bin/bash
# ================================================================
# LE HOANG ROBOTICS - ONE-CLICK CLOUD SERVER INSTALLER FOR LINUX VPS
# Hỗ trợ: Ubuntu 20.04 / 22.04 / 24.04 LTS & Debian 11/12
# ================================================================

set -e

echo "🚀 [1/5] Đang cập nhật hệ điều hành..."
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y curl wget git ufw

echo "📦 [2/5] Kiểm tra & cài đặt Docker + Docker Compose..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    rm get-docker.sh
fi

if ! command -v docker-compose &> /dev/null; then
    sudo apt-get install -y docker-compose-plugin
fi

echo "🛡️ [3/5] Mở cổng tường lửa UFW (5050, 80, 443, 22)..."
sudo ufw allow 22/tcp || true
sudo ufw allow 80/tcp || true
sudo ufw allow 443/tcp || true
sudo ufw allow 5050/tcp || true
sudo ufw --force enable || true

echo "🏗️ [4/5] Khởi động Lê Hoàng Cloud Server với Docker Compose..."
sudo docker compose down || true
sudo docker compose up -d --build

echo "✅ [5/5] KIỂM TRA TRẠNG THÁI SERVER:"
sleep 3
sudo docker ps | grep lehoang-cloud-server || true

SERVER_IP=$(curl -s ifconfig.me || hostname -I | awk '{print $1}')
echo "=================================================="
echo "🎉 TRIỂN KHAI THÀNH CÔNG LÊ HOÀNG CLOUD SERVER!"
echo "👉 Web Dashboard:    http://${SERVER_IP}:5050"
echo "👉 SignalR Hub:      http://${SERVER_IP}:5050/fleetHub"
echo "👉 REST API Summary: http://${SERVER_IP}:5050/api/fleet/summary"
echo "=================================================="
echo "💡 Để kết nối chi nhánh, hãy điền địa chỉ sau vào file cloud_config.json:"
echo "   \"CloudServerUrl\": \"http://${SERVER_IP}:5050/fleetHub\""
echo "=================================================="
