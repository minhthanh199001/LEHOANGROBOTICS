using System;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using Microsoft.AspNetCore.SignalR.Client;
using LeHoangControlCenter.Models;
using LeHoangControlCenter.ViewModels;

namespace LeHoangControlCenter.Services
{
    public class CloudBridgeService : IAsyncDisposable
    {
        private readonly MainViewModel _mainVm;
        private HubConnection? _hubConnection;
        private readonly string _cloudHubUrl;
        private CancellationTokenSource? _heartbeatCts;
        private bool _isDisposed;

        public bool IsConnected => _hubConnection?.State == HubConnectionState.Connected;
        public string CloudHubUrl => _cloudHubUrl;

        public event Action<bool>? ConnectionStatusChanged;

        public static string LoadConfiguredCloudUrl()
        {
            try
            {
                string path = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "cloud_config.json");
                if (System.IO.File.Exists(path))
                {
                    var json = System.IO.File.ReadAllText(path);
                    using var doc = System.Text.Json.JsonDocument.Parse(json);
                    if (doc.RootElement.TryGetProperty("CloudServerUrl", out var prop))
                    {
                        var val = prop.GetString();
                        if (!string.IsNullOrWhiteSpace(val)) return val;
                    }
                }
                else
                {
                    // Tự động sinh file cấu hình mẫu khi chưa có
                    var defaultObj = new
                    {
                        CloudServerUrl = "http://localhost:5050/fleetHub",
                        Description = "Cấu hình địa chỉ Cloud Server trung tâm để đồng bộ chuỗi chi nhánh (VD: http://cloud.lehoangrobotics.com:5050/fleetHub hoặc https://api.domain.com/fleetHub)"
                    };
                    System.IO.File.WriteAllText(path, System.Text.Json.JsonSerializer.Serialize(defaultObj, new System.Text.Json.JsonSerializerOptions { WriteIndented = true }));
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] Error loading cloud_config.json: {ex.Message}");
            }
            return "http://localhost:5050/fleetHub";
        }

        public CloudBridgeService(MainViewModel mainVm, string? cloudHubUrl = null)
        {
            _mainVm = mainVm;
            _cloudHubUrl = !string.IsNullOrWhiteSpace(cloudHubUrl) ? cloudHubUrl : LoadConfiguredCloudUrl();
        }

        public async Task StartAsync()
        {
            try
            {
                _hubConnection = new HubConnectionBuilder()
                    .WithUrl(_cloudHubUrl)
                    .WithAutomaticReconnect(new[]
                    {
                        TimeSpan.Zero,
                        TimeSpan.FromSeconds(2),
                        TimeSpan.FromSeconds(5),
                        TimeSpan.FromSeconds(10),
                        TimeSpan.FromSeconds(20)
                    })
                    .Build();

                _hubConnection.On<CloudCommandPacket>("OnCloudCommandReceived", async (cmd) =>
                {
                    await HandleIncomingCloudCommandAsync(cmd);
                });

                _hubConnection.Reconnecting += (err) =>
                {
                    Debug.WriteLine($"[CloudBridge] Reconnecting to Cloud Server: {err?.Message}");
                    Application.Current?.Dispatcher?.Invoke(() => ConnectionStatusChanged?.Invoke(false));
                    return Task.CompletedTask;
                };

                _hubConnection.Reconnected += async (connectionId) =>
                {
                    Debug.WriteLine($"[CloudBridge] Reconnected to Cloud Server. ID: {connectionId}");
                    await RegisterEdgeNodeAsync();
                    Application.Current?.Dispatcher?.Invoke(() => ConnectionStatusChanged?.Invoke(true));
                };

                _hubConnection.Closed += (err) =>
                {
                    Debug.WriteLine($"[CloudBridge] Cloud connection closed: {err?.Message}");
                    Application.Current?.Dispatcher?.Invoke(() => ConnectionStatusChanged?.Invoke(false));
                    return Task.CompletedTask;
                };

                await _hubConnection.StartAsync();
                Debug.WriteLine("[CloudBridge] Successfully connected to LeHoangCloudServer!");
                await RegisterEdgeNodeAsync();
                Application.Current?.Dispatcher?.Invoke(() => ConnectionStatusChanged?.Invoke(true));

                StartHeartbeat();
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] Connection failed (server may be offline): {ex.Message}");
                Application.Current?.Dispatcher?.Invoke(() => ConnectionStatusChanged?.Invoke(false));
                // Retry in background after 5s
                _ = Task.Run(async () =>
                {
                    await Task.Delay(5000);
                    if (!_isDisposed && !IsConnected)
                    {
                        await StartAsync();
                    }
                });
            }
        }

        private async Task RegisterEdgeNodeAsync()
        {
            if (_hubConnection == null || _hubConnection.State != HubConnectionState.Connected)
                return;

            try
            {
                string robotId = _mainVm.RobotId ?? "LH-ORIONSTAR-02";
                string orgId = _mainVm.SelectedOrganization?.Id ?? "ORG-01";
                string siteId = _mainVm.SelectedSite?.Id ?? "SITE-01";
                await _hubConnection.InvokeAsync("RegisterEdgeNode", robotId, orgId, siteId);

                // 1. Đồng bộ tài khoản người dùng đang đăng nhập từ Desktop lên Cloud
                await SyncCurrentUserAsync();

                // 2. Kéo danh mục Tổ chức & Chi nhánh mới nhất từ Cloud về máy trạm
                await SyncCatalogFromCloudAsync();

                // 3. Tự động đẩy tất cả các Robot đang có ở máy trạm lên Cloud Server
                await SyncAllLocalRobotsToCloudAsync();
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] RegisterEdgeNode error: {ex.Message}");
            }
        }

        public async Task PushRobotToCloudAsync(EnterpriseRobotItem robot)
        {
            if (_hubConnection == null || _hubConnection.State != HubConnectionState.Connected || robot == null)
                return;

            try
            {
                var rItem = new
                {
                    id = robot.Id,
                    name = robot.Name,
                    model = robot.Model ?? "OrionStar Mini",
                    orgId = _mainVm.SelectedOrganization?.Id ?? robot.OrganizationId ?? "",
                    orgName = _mainVm.SelectedOrganization?.Name ?? robot.OrganizationName ?? "",
                    siteId = _mainVm.SelectedSite?.Id ?? robot.SiteId ?? "",
                    siteName = _mainVm.SelectedSite?.Name ?? robot.SiteName ?? "",
                    ip = robot.Ip,
                    port = robot.Port,
                    status = robot.Status,
                    battery = robot.BatteryLevel,
                    isCharging = robot.IsCharging,
                    assignedMap = robot.AssignedMap ?? _mainVm.CurrentMap,
                    poseX = (string.Equals(robot.Ip, _mainVm.RobotIp, StringComparison.OrdinalIgnoreCase)) ? _mainVm.PoseX : 0.0,
                    poseY = (string.Equals(robot.Ip, _mainVm.RobotIp, StringComparison.OrdinalIgnoreCase)) ? _mainVm.PoseY : 0.0,
                    poseAngle = (string.Equals(robot.Ip, _mainVm.RobotIp, StringComparison.OrdinalIgnoreCase)) ? _mainVm.RobotHeadingDegrees : 0.0,
                    lastSeen = DateTime.UtcNow
                };
                await _hubConnection.InvokeAsync("UpsertRobot", rItem);
                Debug.WriteLine($"[CloudBridge] Pushed robot {robot.Name} ({robot.Id}) to Cloud successfully!");
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] PushRobotToCloud error: {ex.Message}");
            }
        }

        public async Task DeleteRobotFromCloudAsync(string robotId)
        {
            if (string.IsNullOrWhiteSpace(robotId)) return;
            try
            {
                using var http = new System.Net.Http.HttpClient();
                var uri = new Uri(_cloudHubUrl);
                var baseUrl = $"{uri.Scheme}://{uri.Authority}";
                await http.DeleteAsync($"{baseUrl}/api/fleet/robots/{robotId}");
                Debug.WriteLine($"[CloudBridge] Deleted robot {robotId} from Cloud successfully!");
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] DeleteRobotFromCloud error: {ex.Message}");
            }
        }

        public async Task SyncAllLocalRobotsToCloudAsync()
        {
            if (_hubConnection == null || _hubConnection.State != HubConnectionState.Connected)
                return;

            try
            {
                var list = new System.Collections.Generic.List<object>();
                foreach (var robot in _mainVm.EnterpriseRobots)
                {
                    list.Add(new
                    {
                        id = robot.Id,
                        name = robot.Name,
                        model = robot.Model ?? "OrionStar Mini",
                        orgId = _mainVm.SelectedOrganization?.Id ?? robot.OrganizationId ?? "",
                        orgName = _mainVm.SelectedOrganization?.Name ?? robot.OrganizationName ?? "",
                        siteId = _mainVm.SelectedSite?.Id ?? robot.SiteId ?? "",
                        siteName = _mainVm.SelectedSite?.Name ?? robot.SiteName ?? "",
                        ip = robot.Ip,
                        port = robot.Port,
                        status = robot.Status,
                        battery = robot.BatteryLevel,
                        isCharging = robot.IsCharging,
                        assignedMap = robot.AssignedMap ?? _mainVm.CurrentMap,
                        poseX = (string.Equals(robot.Ip, _mainVm.RobotIp, StringComparison.OrdinalIgnoreCase)) ? _mainVm.PoseX : 0.0,
                        poseY = (string.Equals(robot.Ip, _mainVm.RobotIp, StringComparison.OrdinalIgnoreCase)) ? _mainVm.PoseY : 0.0,
                        poseAngle = (string.Equals(robot.Ip, _mainVm.RobotIp, StringComparison.OrdinalIgnoreCase)) ? _mainVm.RobotHeadingDegrees : 0.0,
                        lastSeen = DateTime.UtcNow
                    });
                }
                await _hubConnection.InvokeAsync("SyncEdgeFleet", list);
                Debug.WriteLine($"[CloudBridge] Synced {list.Count} local robots to Cloud Server.");
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] SyncAllLocalRobots error: {ex.Message}");
            }
        }

        public async Task SyncCatalogFromCloudAsync()
        {
            try
            {
                using var http = new System.Net.Http.HttpClient();
                var uri = new Uri(_cloudHubUrl);
                var baseUrl = $"{uri.Scheme}://{uri.Authority}";

                // 1. Fetch Organizations
                var orgsJson = await http.GetStringAsync($"{baseUrl}/api/fleet/organizations");
                var orgs = System.Text.Json.JsonSerializer.Deserialize<System.Collections.Generic.List<OrganizationItem>>(orgsJson, new System.Text.Json.JsonSerializerOptions { PropertyNameCaseInsensitive = true });

                // 2. Fetch Sites
                var sitesJson = await http.GetStringAsync($"{baseUrl}/api/fleet/sites");
                var sites = System.Text.Json.JsonSerializer.Deserialize<System.Collections.Generic.List<SiteItem>>(sitesJson, new System.Text.Json.JsonSerializerOptions { PropertyNameCaseInsensitive = true });

                if (orgs != null && orgs.Count > 0 && sites != null && sites.Count > 0)
                {
                    Application.Current?.Dispatcher?.Invoke(() =>
                    {
                        _mainVm.ApplyCloudCatalog(orgs, sites);
                    });
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] SyncCatalogFromCloud error: {ex.Message}");
            }
        }

        public async Task SyncCurrentUserAsync()
        {
            if (_hubConnection == null || _hubConnection.State != HubConnectionState.Connected || _mainVm.CurrentUser == null)
                return;

            try
            {
                var u = _mainVm.CurrentUser;
                var userPacket = new
                {
                    id = u.Id,
                    username = u.Username,
                    fullName = u.FullName,
                    email = u.Email,
                    role = u.Role,
                    organizationId = u.OrganizationId,
                    organizationName = u.OrganizationName,
                    siteId = u.SiteId,
                    siteName = u.SiteName,
                    isActive = u.IsActive
                };
                await _hubConnection.InvokeAsync("ReportCurrentUser", userPacket);
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] SyncCurrentUser error: {ex.Message}");
            }
        }

        public async Task SendTelemetryAsync(RobotTelemetryPacket packet)
        {
            if (_hubConnection == null || _hubConnection.State != HubConnectionState.Connected)
                return;

            try
            {
                await _hubConnection.InvokeAsync("ReportTelemetry", packet);
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] SendTelemetry error: {ex.Message}");
            }
        }

        public async Task SendAlertAsync(string alertType, string message)
        {
            if (_hubConnection == null || _hubConnection.State != HubConnectionState.Connected)
                return;

            try
            {
                string robotId = _mainVm.RobotId ?? "LH-ORIONSTAR-02";
                await _hubConnection.InvokeAsync("ReportAlert", robotId, alertType, message);
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[CloudBridge] SendAlert error: {ex.Message}");
            }
        }

        private async Task HandleIncomingCloudCommandAsync(CloudCommandPacket cmd)
        {
            if (cmd == null) return;
            string currentRobotId = _mainVm.RobotId ?? "LH-ORIONSTAR-02";

            // If command is targeted to another robot, ignore unless it's a broadcast
            if (!string.IsNullOrEmpty(cmd.TargetRobotId) &&
                !string.Equals(cmd.TargetRobotId, currentRobotId, StringComparison.OrdinalIgnoreCase) &&
                !string.Equals(cmd.TargetRobotId, "ALL", StringComparison.OrdinalIgnoreCase))
            {
                return;
            }

            Debug.WriteLine($"[CloudBridge] Received Cloud Command: {cmd.CommandType} - Param: {cmd.Parameter}");

            await Application.Current.Dispatcher.InvokeAsync(async () =>
            {
                _mainVm.AddLog($"☁️ [LỆNH CLOUD NHẬN ĐƯỢC] {cmd.CommandType}: {cmd.Parameter}");

                switch (cmd.CommandType?.ToUpperInvariant())
                {
                    case "EMERGENCY_STOP":
                        await _mainVm.EmergencyStopAsync();
                        break;

                    case "RETURN_HOME":
                    case "CHARGE":
                        await _mainVm.ChargeAsync("START");
                        break;

                    case "NAVIGATE":
                        if (!string.IsNullOrEmpty(cmd.Parameter))
                        {
                            await _mainVm.NavigateAsync(cmd.Parameter);
                        }
                        break;

                    case "SPEAK":
                        if (!string.IsNullOrEmpty(cmd.Parameter))
                        {
                            await _mainVm.SpeakAsync(cmd.Parameter);
                        }
                        break;
                }
            });
        }

        private void StartHeartbeat()
        {
            _heartbeatCts?.Cancel();
            _heartbeatCts = new CancellationTokenSource();
            var token = _heartbeatCts.Token;

            _ = Task.Run(async () =>
            {
                while (!token.IsCancellationRequested && !_isDisposed)
                {
                    try
                    {
                        if (IsConnected)
                        {
                            var packet = new RobotTelemetryPacket
                            {
                                RobotId = string.IsNullOrEmpty(_mainVm.RobotId) ? "LH-ORIONSTAR-02" : _mainVm.RobotId,
                                OrgId = _mainVm.SelectedOrganization?.Id ?? "ORG-01",
                                SiteId = _mainVm.SelectedSite?.Id ?? "SITE-01",
                                Status = _mainVm.IsConnected ? (_mainVm.IsCharging ? "Charging" : "Online") : "Offline",
                                Battery = _mainVm.BatteryLevel,
                                IsCharging = _mainVm.IsCharging,
                                MapName = _mainVm.CurrentMap,
                                PoseX = _mainVm.PoseX,
                                PoseY = _mainVm.PoseY,
                                PoseAngle = _mainVm.RobotHeadingDegrees,
                                ActiveMode = _mainVm.RobotState,
                                Timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                            };

                            await SendTelemetryAsync(packet);
                        }
                    }
                    catch { }

                    await Task.Delay(1500, token);
                }
            }, token);
        }

        public async ValueTask DisposeAsync()
        {
            _isDisposed = true;
            _heartbeatCts?.Cancel();
            _heartbeatCts?.Dispose();

            if (_hubConnection != null)
            {
                await _hubConnection.DisposeAsync();
                _hubConnection = null;
            }
        }
    }
}

