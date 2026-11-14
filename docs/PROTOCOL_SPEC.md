# LE HOANG ROBOTICS - COMMUNICATION PROTOCOL SPECIFICATION (v1.0)
## Giao thức truyền thông giữa PC Control Center và Android Robot APK

---

### 1. Nguyên lý giao tiếp
- **Cơ chế**: WebSocket 2 chiều (Bidirectional WebSockets) chạy trên cổng TCP `8088` (mặc định) của Robot.
- **Định dạng dữ liệu**: JSON UTF-8.
- **Chu kỳ Telemetry**: Robot tự động gửi bản tin Telemetry về PC mỗi **500ms** (2Hz).
- **Cơ chế Watchdog**: Nếu PC đang gửi lệnh chuyển động liên tục mà ngắt quãng quá **800ms**, Robot tự động kích hoạt `stopMove()`.

---

### 2. Các bản tin từ Robot gửi về PC (Robot -> PC)

#### 2.1. Bản tin Heartbeat & Trạng thái tổng thể (TELEMETRY)
```json
{
  "type": "TELEMETRY",
  "robotId": "LH-ROBOT-01",
  "timestamp": 1731301000,
  "battery": {
    "level": 85,
    "isCharging": false
  },
  "pose": {
    "x": 12.45,
    "y": -3.20,
    "theta": 1.57,
    "isEstimated": true
  },
  "status": {
    "state": "IDLE", 
    "currentMap": "Floor_1",
    "emergencyStop": false,
    "currentPlace": "Bàn số 3",
    "doorStatus": "CLOSED",
    "speed": 0.0
  },
  "errors": []
}
```
*Ghi chú `state`: `IDLE` (chờ), `MOVING` (đang di chuyển), `NAVIGATING` (đang tự hành đến điểm), `CRUISING` (đang tuần tra), `LEADING` (đang dẫn khách), `CHARGING` (đang sạc).*

#### 2.2. Bản tin phản hồi kết quả lệnh (ACK / RESULT)
```json
{
  "type": "COMMAND_RESULT",
  "msgId": "cmd_001",
  "command": "NAVIGATE_TO",
  "success": true,
  "resultCode": 0,
  "message": "Arrived at destination"
}
```

---

### 3. Các bản tin từ PC gửi đến Robot (PC -> Robot)

#### 3.1. Điều khiển chuyển động thủ công (CMD_MOVE)
```json
{
  "type": "CMD_MOVE",
  "msgId": "cmd_002",
  "action": "FORWARD", 
  "linearSpeed": 0.4,
  "angularSpeed": 25.0
}
```
*Các giá trị `action`: `FORWARD`, `BACKWARD`, `TURN_LEFT`, `TURN_RIGHT`, `STOP`.*

#### 3.2. Điều khiển xoay cụm đầu (CMD_HEAD)
```json
{
  "type": "CMD_HEAD",
  "msgId": "cmd_003",
  "horizontalAngle": 15,
  "verticalAngle": -10
}
```

#### 3.3. Dừng khẩn cấp (CMD_EMERGENCY_STOP)
```json
{
  "type": "CMD_EMERGENCY_STOP",
  "msgId": "cmd_004"
}
```

#### 3.4. Ra lệnh điều hướng tự hành (CMD_NAVIGATE)
```json
{
  "type": "CMD_NAVIGATE",
  "msgId": "cmd_005",
  "destination": "Bàn số 12",
  "maxDistance": 1.5,
  "timeoutSeconds": 60
}
```

#### 3.5. Dừng điều hướng (CMD_STOP_NAVIGATE)
```json
{
  "type": "CMD_STOP_NAVIGATE",
  "msgId": "cmd_006"
}
```

#### 3.6. Phát giọng nói TTS (CMD_SPEAK)
```json
{
  "type": "CMD_SPEAK",
  "msgId": "cmd_007",
  "text": "Xin chào quý khách! Lê Hoàng Robotics hân hạnh phục vụ."
}
```

#### 3.7. Ra lệnh về trạm sạc tự động (CMD_CHARGE)
```json
{
  "type": "CMD_CHARGE",
  "msgId": "cmd_008",
  "action": "START_CHARGE"
}
```
*Các giá trị `action`: `START_CHARGE`, `STOP_CHARGE`, `LEAVE_PILE`.*

#### 3.8. Điều khiển cửa khoang tự động (CMD_DOOR)
```json
{
  "type": "CMD_DOOR",
  "msgId": "cmd_009",
  "doorId": 1,
  "action": "OPEN" 
}
```
*Các giá trị `action`: `OPEN`, `CLOSE`.*

