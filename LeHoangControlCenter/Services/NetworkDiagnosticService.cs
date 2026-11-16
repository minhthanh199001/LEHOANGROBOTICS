using System;
using System.Collections.Generic;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;

namespace LeHoangControlCenter.Services
{
    public class DiagnosticResult
    {
        public bool PingSuccess { get; set; }
        public long PingRoundtripMs { get; set; }
        public bool PortOpen { get; set; }
        public string Message { get; set; } = string.Empty;
    }

    public class DiscoveredRobot
    {
        public string IpAddress { get; set; } = string.Empty;
        public int Port { get; set; } = 8899;
        public string HostName { get; set; } = string.Empty;
        public bool IsAvailable { get; set; }
    }

    public static class NetworkDiagnosticService
    {
        public static async Task<DiagnosticResult> TestRobotConnectionAsync(string ip, int port = 8899, int timeoutMs = 2500)
        {
            var result = new DiagnosticResult();

            // Step 1: Ping Test
            try
            {
                using var ping = new Ping();
                var reply = await ping.SendPingAsync(ip, timeoutMs);
                result.PingSuccess = (reply.Status == IPStatus.Success);
                result.PingRoundtripMs = reply.RoundtripTime;
            }
            catch (Exception)
            {
                result.PingSuccess = false;
            }

            // Step 2: TCP Port Test
            try
            {
                using var client = new TcpClient();
                var connectTask = client.ConnectAsync(ip, port);
                var timeoutTask = Task.Delay(timeoutMs);

                var completedTask = await Task.WhenAny(connectTask, timeoutTask);
                if (completedTask == connectTask && client.Connected)
                {
                    result.PortOpen = true;
                }
                else
                {
                    result.PortOpen = false;
                }
            }
            catch (Exception)
            {
                result.PortOpen = false;
            }

            // Diagnostic summary message
            if (result.PingSuccess && result.PortOpen)
            {
                result.Message = $"✅ KẾT NỐI TỐT: Đã ping Robot ({result.PingRoundtripMs}ms) và Cổng {port} đang mở sẵn sàng nhận lệnh!";
            }
            else if (result.PingSuccess && !result.PortOpen)
            {
                result.Message = $"⚠️ ĐÃ THÔNG MẠNG (Ping {result.PingRoundtripMs}ms), nhưng CỔNG {port} CHƯA MỞ! " +
                                 "Vui lòng đảm bảo ứng dụng APK LeHoang đã được mở trên màn hình Robot OrionStar.";
            }
            else if (!result.PingSuccess && result.PortOpen)
            {
                result.Message = $"✅ CỔNG {port} ĐANG MỞ (Router chặn ICMP ping). Sẵn sàng kết nối WebSocket!";
                result.PingSuccess = true;
            }
            else
            {
                result.Message = $"❌ KHÔNG TÌM THẤY ROBOT: Không ping được {ip} và cổng {port} không phản hồi. " +
                                 "Vui lòng kiểm tra lại mạng Wi-Fi và đảm bảo Robot và PC chung lớp mạng.";
            }

            return result;
        }

        public static async Task<bool> QuickCheckRobotOnlineAsync(string ip, int port = 8899, int timeoutMs = 400)
        {
            if (string.IsNullOrWhiteSpace(ip)) return false;
            try
            {
                using var client = new TcpClient();
                var connectTask = client.ConnectAsync(ip, port);
                var timeoutTask = Task.Delay(timeoutMs);

                var completed = await Task.WhenAny(connectTask, timeoutTask);
                if (completed == connectTask && client.Connected)
                {
                    return true;
                }
            }
            catch { }

            // Fallback quick ping
            try
            {
                using var ping = new Ping();
                var reply = await ping.SendPingAsync(ip, timeoutMs);
                return reply.Status == IPStatus.Success;
            }
            catch
            {
                return false;
            }
        }

        public static async Task<List<DiscoveredRobot>> ScanSubnetForRobotsAsync(string localIpBase, int port = 8088, Action<int>? progressCallback = null)
        {
            var discovered = new List<DiscoveredRobot>();
            var tasks = new List<Task>();

            // localIpBase e.g. "192.168.1"
            for (int i = 1; i <= 254; i++)
            {
                int hostId = i;
                string targetIp = $"{localIpBase}.{hostId}";

                tasks.Add(Task.Run(async () =>
                {
                    try
                    {
                        using var client = new TcpClient();
                        var connectTask = client.ConnectAsync(targetIp, port);
                        var timeoutTask = Task.Delay(400);

                        if (await Task.WhenAny(connectTask, timeoutTask) == connectTask && client.Connected)
                        {
                            lock (discovered)
                            {
                                discovered.Add(new DiscoveredRobot
                                {
                                    IpAddress = targetIp,
                                    Port = port,
                                    HostName = $"OrionStar Robot ({targetIp})",
                                    IsAvailable = true
                                });
                            }
                        }
                    }
                    catch
                    {
                    }
                }));
            }

            await Task.WhenAll(tasks);
            return discovered;
        }
    }
}

