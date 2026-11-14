using System;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using LeHoangControlCenter.Models;

namespace LeHoangControlCenter.Services
{
    public interface IRobotWebSocketService
    {
        bool IsConnected { get; }
        event Action<bool>? ConnectionStateChanged;
        event Action<TelemetryMessage>? TelemetryReceived;
        event Action<MapDataMessage>? MapDataReceived;
        event Action<CommandResultMessage>? CommandResultReceived;
        event Action<string>? LogOccurred;

        Task ConnectAsync(string host, int port, CancellationToken cancellationToken = default);
        Task DisconnectAsync();
        Task SendCommandAsync(BaseRobotCommand command);
    }

    public class RobotWebSocketService : IRobotWebSocketService
    {
        private ClientWebSocket? _webSocket;
        private CancellationTokenSource? _cts;
        private readonly object _lockObj = new();

        public bool IsConnected => _webSocket != null && _webSocket.State == WebSocketState.Open;

        public event Action<bool>? ConnectionStateChanged;
        public event Action<TelemetryMessage>? TelemetryReceived;
        public event Action<MapDataMessage>? MapDataReceived;
        public event Action<CommandResultMessage>? CommandResultReceived;
        public event Action<string>? LogOccurred;

        public async Task ConnectAsync(string host, int port, CancellationToken cancellationToken = default)
        {
            if (IsConnected)
            {
                await DisconnectAsync();
            }

            _cts = new CancellationTokenSource();
            _webSocket = new ClientWebSocket();

            var uri = new Uri($"ws://{host}:{port}");
            Log($"Đang kết nối tới Robot tại {uri}...");

            try
            {
                using var linkedCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken, _cts.Token);
                await _webSocket.ConnectAsync(uri, linkedCts.Token);
                Log($"Kết nối thành công tới Robot: {uri}");
                ConnectionStateChanged?.Invoke(true);

                // Start listening thread
                _ = Task.Run(() => ReceiveLoopAsync(_cts.Token));
            }
            catch (Exception ex)
            {
                Log($"Lỗi kết nối tới Robot: {ex.Message}");
                ConnectionStateChanged?.Invoke(false);
                throw;
            }
        }

        public async Task DisconnectAsync()
        {
            if (_webSocket != null)
            {
                try
                {
                    _cts?.Cancel();
                    if (_webSocket.State == WebSocketState.Open || _webSocket.State == WebSocketState.CloseReceived)
                    {
                        await _webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Client closing", CancellationToken.None);
                    }
                }
                catch (Exception ex)
                {
                    Log($"Lỗi đóng kết nối: {ex.Message}");
                }
                finally
                {
                    _webSocket.Dispose();
                    _webSocket = null;
                    ConnectionStateChanged?.Invoke(false);
                    Log("Đã ngắt kết nối với Robot.");
                }
            }
        }

        public async Task SendCommandAsync(BaseRobotCommand command)
        {
            if (!IsConnected || _webSocket == null)
            {
                Log($"[CẢNH BÁO] Không thể gửi lệnh '{command.Type}': Chưa kết nối với Robot.");
                return;
            }

            try
            {
                var json = JsonSerializer.Serialize(command, command.GetType());
                var bytes = Encoding.UTF8.GetBytes(json);
                var segment = new ArraySegment<byte>(bytes);

                await _webSocket.SendAsync(segment, WebSocketMessageType.Text, true, CancellationToken.None);
                Log($"[GỬI LỆNH] {command.Type} -> {json}");
            }
            catch (Exception ex)
            {
                Log($"[LỖI GỬI LỆNH] {ex.Message}");
            }
        }

        private async Task ReceiveLoopAsync(CancellationToken token)
        {
            var buffer = new byte[8192];
            using var ms = new MemoryStream();

            try
            {
                while (!token.IsCancellationRequested && _webSocket != null && _webSocket.State == WebSocketState.Open)
                {
                    ms.SetLength(0);
                    WebSocketReceiveResult result;
                    do
                    {
                        result = await _webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), token);
                        if (result.MessageType == WebSocketMessageType.Close)
                        {
                            await DisconnectAsync();
                            return;
                        }
                        ms.Write(buffer, 0, result.Count);
                    }
                    while (!result.EndOfMessage);

                    ms.Seek(0, SeekOrigin.Begin);
                    var jsonStr = Encoding.UTF8.GetString(ms.ToArray());
                    ProcessIncomingMessage(jsonStr);
                }
            }
            catch (OperationCanceledException)
            {
                // Normal exit
            }
            catch (Exception ex)
            {
                Log($"Lỗi luồng nhận dữ liệu: {ex.Message}");
                ConnectionStateChanged?.Invoke(false);
            }
        }

        private void ProcessIncomingMessage(string jsonStr)
        {
            try
            {
                using var doc = JsonDocument.Parse(jsonStr);
                if (doc.RootElement.TryGetProperty("type", out var typeProp))
                {
                    var msgType = typeProp.GetString();
                    if (msgType == "TELEMETRY")
                    {
                        var telemetry = JsonSerializer.Deserialize<TelemetryMessage>(jsonStr);
                        if (telemetry != null)
                        {
                            TelemetryReceived?.Invoke(telemetry);
                        }
                    }
                    else if (msgType == "MAP_DATA")
                    {
                        var mapData = JsonSerializer.Deserialize<MapDataMessage>(jsonStr);
                        if (mapData != null)
                        {
                            MapDataReceived?.Invoke(mapData);
                            Log($"[BẢN ĐỒ] Nhận thông tin bản đồ: {mapData.CurrentMap} ({mapData.MapList.Count} bản đồ khả dụng, {mapData.Places.Count} điểm đến)");
                        }
                    }
                    else if (msgType == "COMMAND_RESULT")
                    {
                        var result = JsonSerializer.Deserialize<CommandResultMessage>(jsonStr);
                        if (result != null)
                        {
                            CommandResultReceived?.Invoke(result);
                            Log($"[KẾT QUẢ LỆNH] {result.Command} => {(result.Success ? "THÀNH CÔNG" : "THẤT BẠI")}: {result.Message}");
                        }
                    }
                    else
                    {
                        Log($"[NHẬN TIN] {jsonStr}");
                    }
                }
            }
            catch (Exception ex)
            {
                Log($"Lỗi phân tích JSON: {ex.Message} (Data: {jsonStr})");
            }
        }

        private void Log(string message)
        {
            var formatted = $"[{DateTime.Now:HH:mm:ss.fff}] {message}";
            LogOccurred?.Invoke(formatted);
        }
    }
}

