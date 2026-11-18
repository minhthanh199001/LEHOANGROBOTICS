using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LeHoangControlCenter.Models;
using LeHoangControlCenter.Services;

namespace LeHoangControlCenter.ViewModels
{
    public partial class MainViewModel : ObservableObject
    {
        private readonly IRobotWebSocketService _robotService;
        private readonly DispatcherTimer _simulationTimer;
        private readonly DispatcherTimer _robotFleetStatusTimer;
        private CloudBridgeService? _cloudBridge;
        private DateTime _lastRobotDbSave = DateTime.MinValue;
        private int _lastSavedBattery = -1;

        // --- Cloud Central Sync (Option B) ---
        [ObservableProperty]
        private bool _isCloudConnected;

        [ObservableProperty]
        private string _cloudStatusText = "☁️ CLOUD SYNC: CHƯA KẾT NỐI";

        // --- Active View Navigation ---
        [ObservableProperty]
        private string _activeView = "DASHBOARD"; // DASHBOARD, ROBOTS, ORDERS, TASKS, MAPS, ALERTS, DIAGNOSTICS

        // --- Top KPI Metrics (from AgniX Mockup) ---
        [ObservableProperty]
        private int _availableFleetCount = 8;

        [ObservableProperty]
        private int _onlineRobotsPercentage = 99;

        [ObservableProperty]
        private int _ordersInProgressCount = 19;

        [ObservableProperty]
        private int _avgUtilizationPercentage = 87;

        // --- Connection & Diagnostics ---
        [ObservableProperty]
        private string _robotIp = "192.168.0.44";

        [ObservableProperty]
        private int _robotPort = 8899;

        [ObservableProperty]
        private bool _isConnected;

        partial void OnIsConnectedChanged(bool value)
        {
            OnPropertyChanged(nameof(ConnectionStatusColor));
        }

        [ObservableProperty]
        private string _connectionStatusText = "CHƯA KẾT NỐI";

        partial void OnConnectionStatusTextChanged(string value)
        {
            OnPropertyChanged(nameof(ConnectionStatusColor));
        }

        public string ConnectionStatusColor
        {
            get
            {
                if (IsConnected) return "#10B981"; // Emerald Green
                if (IsSimulationMode) return "#F59E0B"; // Amber
                return "#EF4444"; // Red
            }
        }

        [ObservableProperty]
        private string _diagnosticMessage = "Chưa kiểm tra kết nối";

        [ObservableProperty]
        private bool _isDiagnosing;

        [ObservableProperty]
        private bool _isSimulationMode = false;

        partial void OnIsSimulationModeChanged(bool value)
        {
            OnPropertyChanged(nameof(ConnectionStatusColor));
        }

        // --- Selected Active Robot Details ---
        [ObservableProperty]
        private string _robotId = "LH-ORIONSTAR-01";

        [ObservableProperty]
        private string _robotState = "AVAILABLE"; // AVAILABLE, ACTIVE, CHARGING, OFFLINE

        [ObservableProperty]
        private int _batteryLevel = 89;

        [ObservableProperty]
        private bool _isCharging = false;

        [ObservableProperty]
        private bool _isEstimated = true;

        [ObservableProperty]
        private string _localizationStatusText = "🟢 Đã định vị";

        [ObservableProperty]
        private bool _isRelocalizeMode = false;

        public string RelocalizeModeText => IsRelocalizeMode ? "🎯 HỦY ĐẶT LẠI" : "📍 Đặt lại vị trí";

        partial void OnIsRelocalizeModeChanged(bool value)
        {
            OnPropertyChanged(nameof(RelocalizeModeText));
            if (value)
            {
                IsAddPointMode = false;
            }
        }

        [ObservableProperty]
        private double _poseX = 1.93;

        [ObservableProperty]
        private double _poseY = 5.99;

        [ObservableProperty]
        private double _poseTheta = 0.0;

        [ObservableProperty]
        private double _robotHeadingDegrees = 45.0;

        [ObservableProperty]
        private string _currentMap = "tăng 2-1110154527";

        [ObservableProperty]
        private string _currentPlace = "Khu vực Bàn C";

        [ObservableProperty]
        private string _doorStatus = "CLOSED";

        [ObservableProperty]
        private bool _isEmergencyStopActive;

        // Canvas mapping coordinates (pixels)
        [ObservableProperty]
        private double _canvasRobotLeft = 320.0;

        [ObservableProperty]
        private double _canvasRobotTop = 210.0;

        // Controls
        [ObservableProperty]
        private bool _isFloorConfigExpanded = false;

        [ObservableProperty]
        private string _targetFloorInput = "1";

        public string FloorConfigToggleText => IsFloorConfigExpanded ? "▲ Đóng" : "⚙️ Cài đặt tầng";

        partial void OnIsFloorConfigExpandedChanged(bool value)
        {
            OnPropertyChanged(nameof(FloorConfigToggleText));
        }

        // Station Maker / Tạo điểm mới
        [ObservableProperty]
        private bool _isStationMakerExpanded = true;

        [ObservableProperty]
        private string _newPlaceName = "";

        [ObservableProperty]
        private double _newPlaceX = 0.0;

        [ObservableProperty]
        private double _newPlaceY = 0.0;

        [ObservableProperty]
        private bool _isAddPointMode = false;

        public string StationMakerToggleText => IsStationMakerExpanded ? "▲ Thu gọn" : "➕ Mở tạo điểm";

        public string AddPointModeText => IsAddPointMode ? "🎯 HỦY CHẤM MAP" : "🎯 Chấm trên map";

        partial void OnIsStationMakerExpandedChanged(bool value)
        {
            OnPropertyChanged(nameof(StationMakerToggleText));
        }

        partial void OnIsAddPointModeChanged(bool value)
        {
            OnPropertyChanged(nameof(AddPointModeText));
            if (value)
            {
                IsRelocalizeMode = false;
            }
        }

        [ObservableProperty]
        private double _linearSpeed = 0.5;

        [ObservableProperty]
        private double _angularSpeed = 25.0;

        [ObservableProperty]
        private string _targetDestination = "Bàn 1";

        [ObservableProperty]
        private string _speechText = "Xin chào quý khách, Lê Hoàng Robotics kính chúc quý khách một ngày tốt lành!";

        [ObservableProperty]
        private string _selectedMap = "";

        partial void OnSelectedMapChanged(string value)
        {
            if (string.IsNullOrWhiteSpace(value) || value == CurrentMap) return;
            SwitchToMapLocal(value);
        }

        [ObservableProperty]
        private ImageSource? _currentMapImage;

        [ObservableProperty]
        private Visibility _mockMapVisibility = Visibility.Visible;

        // Map Theme & Styling (Default: Clean White Floor on Deep Cyber Dark Slate background)
        [ObservableProperty]
        private string _mapViewportBackground = "#080C14";

        [ObservableProperty]
        private bool _isWhiteMapTheme = true;

        [ObservableProperty]
        private string _mapThemeButtonText = "⚡ Nền Cyber";

        [ObservableProperty]
        private string _stationTextForeground = "#0369A1";

        [ObservableProperty]
        private string _stationTextHaloColor = "#FFFFFF";

        private BitmapSource? _rawMapSource;

        // Map Viewport Zoom & Pan
        [ObservableProperty]
        private double _mapZoom = 1.0;

        [ObservableProperty]
        private double _mapPanX = 0.0;

        [ObservableProperty]
        private double _mapPanY = 0.0;

        [ObservableProperty]
        private string _mapZoomText = "100%";

        [ObservableProperty]
        private double _mapDisplayWidth = 300.0;

        [ObservableProperty]
        private double _mapDisplayHeight = 440.0;

        // Navigation View Switching
        [ObservableProperty]
        private string _selectedNavMenu = "Dashboard";

        [ObservableProperty]
        private Visibility _dashboardVisibility = Visibility.Visible;

        [ObservableProperty]
        private Visibility _mapsVisibility = Visibility.Collapsed;

        [ObservableProperty]
        private Visibility _tasksVisibility = Visibility.Collapsed;

        [ObservableProperty]
        private string _dashboardNavBg = "#162034";

        [ObservableProperty]
        private string _dashboardNavFg = "#00E5FF";

        [ObservableProperty]
        private string _mapsNavBg = "Transparent";

        [ObservableProperty]
        private string _mapsNavFg = "#94A3B8";

        [ObservableProperty]
        private string _tasksNavBg = "Transparent";

        [ObservableProperty]
        private string _tasksNavFg = "#94A3B8";

        // --- 3-Tray Food Delivery Tasks System ---
        public ObservableCollection<FoodDeliveryTask> DeliveryTasks { get; } = new();
        public ObservableCollection<string> AvailableStationNames { get; } = new();

        [ObservableProperty]
        private FoodDeliveryTask? _selectedDeliveryTask;

        [ObservableProperty]
        private bool _isEditingExistingTask = false;

        [ObservableProperty]
        private string _taskEditorHeader = "➕ TẠO NHIỆM VỤ GIAO 3 KHAY";

        [ObservableProperty]
        private string _formTaskId = "";

        [ObservableProperty]
        private string _formTaskName = "Giao món ăn trưa";

        [ObservableProperty]
        private string _formReturnStation = "Stand-by Point";

        [ObservableProperty]
        private int _formWaitTimeSeconds = 30;

        [ObservableProperty]
        private string _formGreetingSpeech = "Kính chào quý khách! Món ăn đã sẵn sàng trên khay của robot, chúc quý khách ngon miệng!";

        // Tray 1
        [ObservableProperty]
        private bool _formTray1HasDish = true;

        [ObservableProperty]
        private string _formTray1Destination = "phòng họp";

        [ObservableProperty]
        private string _formTray1DishName = "Cơm sườn đặc biệt x2";

        [ObservableProperty]
        private string _formTray1Note = "Ít ớt";

        // Tray 2
        [ObservableProperty]
        private bool _formTray2HasDish = true;

        [ObservableProperty]
        private string _formTray2Destination = "cafe";

        [ObservableProperty]
        private string _formTray2DishName = "Cà phê sữa đá & Nước cam";

        [ObservableProperty]
        private string _formTray2Note = "Ít ngọt";

        // Tray 3
        [ObservableProperty]
        private bool _formTray3HasDish = false;

        [ObservableProperty]
        private string _formTray3Destination = "tivi";

        [ObservableProperty]
        private string _formTray3DishName = "Set Trà Đào Cam Sả";

        [ObservableProperty]
        private string _formTray3Note = "";

        // Live Delivery Execution State
        [ObservableProperty]
        private bool _isDeliveryRunning = false;

        [ObservableProperty]
        private FoodDeliveryTask? _activeRunningTask;

        [ObservableProperty]
        private string _deliveryProgressStatus = "Sẵn sàng";

        [ObservableProperty]
        private int _deliveryCurrentTrayNumber = 0; // 1, 2, 3 or 4 (returning)

        [ObservableProperty]
        private string _deliveryCurrentDestination = "";

        private CancellationTokenSource? _deliveryCts;

        [ObservableProperty]
        private double _manualTargetX = 0.0;

        [ObservableProperty]
        private double _manualTargetY = 0.0;

        // Dynamic Map World Bounds in meters (Calibrated to OrionStar SLAM coordinates)
        public double MapMinX { get; set; } = -5.0;
        public double MapMaxX { get; set; } = 8.0;
        public double MapMinY { get; set; } = -6.0;
        public double MapMaxY { get; set; } = 13.0;

        // Collections
        public ObservableCollection<FloorMapConfig> FloorConfigs { get; } = new();
        public ObservableCollection<string> AvailableMaps { get; } = new();
        public ObservableCollection<MapPlaceItem> MapStations { get; } = new();
        public ObservableCollection<string> LogHistory { get; } = new();
        public ObservableCollection<FleetRobotItem> FleetRobots { get; } = new();
        public ObservableCollection<RobotMissionTask> TaskQueue { get; } = new();
        public ObservableCollection<AlertItem> RecentAlerts { get; } = new();
        public PointCollection TrajectoryPath { get; } = new();

        public MainViewModel() : this(new RobotWebSocketService())
        {
        }

        public MainViewModel(IRobotWebSocketService robotService)
        {
            _robotService = robotService;
            _robotService.ConnectionStateChanged += OnConnectionStateChanged;
            _robotService.TelemetryReceived += OnTelemetryReceived;
            _robotService.MapDataReceived += OnMapDataReceived;
            _robotService.CommandResultReceived += OnCommandResultReceived;
            _robotService.LogOccurred += OnLogOccurred;

            InitDefaultFleet();
            InitDefaultAlerts();
            InitDefaultTasks();
            InitDefaultMapAndStations();
            LoadFloorConfigs();
            LoadDeliveryTasksFromDisk();
            ResetTaskForm();
            UpdateAvailableStationNames();

            // Khởi tạo cơ sở dữ liệu SQLite & Dữ liệu Phân quyền Doanh nghiệp (Organization / Site / Robots)
            _ = InitializeEnterpriseDataAsync();

            // Setup Simulation Timer (for realistic dashboard animations when robot is in test mode)
            _simulationTimer = new DispatcherTimer
            {
                Interval = TimeSpan.FromMilliseconds(400)
            };
            _simulationTimer.Tick += SimulationTimer_Tick;

            // Setup Robot Fleet Online/Status Checker Timer (every 8 seconds)
            _robotFleetStatusTimer = new DispatcherTimer
            {
                Interval = TimeSpan.FromSeconds(8)
            };
            _robotFleetStatusTimer.Tick += async (s, e) =>
            {
                await RefreshAllRobotsOnlineStatusAsync();
            };
            _robotFleetStatusTimer.Start();

            AddLog("Hệ thống LÊ HOÀNG ROBOTICS CONTROL CENTER đã sẵn sàng.");

            // Tự động kết nối tới Robot vật lý
            _ = Task.Run(async () =>
            {
                await Task.Delay(1200);
                try
                {
                    await _robotService.ConnectAsync(RobotIp, RobotPort);
                }
                catch (Exception ex)
                {
                    AddLog($"[TỰ ĐỘNG KẾT NỐI] {ex.Message}");
                }
            });

            // Khởi động Cloud Bridge kết nối tới LeHoangCloudServer (Option B Centralized Cloud Hub)
            _cloudBridge = new CloudBridgeService(this);
            _cloudBridge.ConnectionStatusChanged += OnCloudConnectionChanged;
            _ = Task.Run(async () =>
            {
                await Task.Delay(1800);
                await _cloudBridge.StartAsync();
            });
        }

        private void OnCloudConnectionChanged(bool connected)
        {
            Application.Current?.Dispatcher?.Invoke(() =>
            {
                IsCloudConnected = connected;
                CloudStatusText = connected ? "☁️ CLOUD SYNC: ĐÃ KẾT NỐI" : "☁️ CLOUD SYNC: ĐANG KẾT NỐI LẠI";
                AddLog(connected ? $"☁️ [CLOUD ENGINE] Đã kết nối & đồng bộ dữ liệu tới {_cloudBridge?.CloudHubUrl}."
                                 : $"☁️ [CLOUD ENGINE] Mất kết nối tới {_cloudBridge?.CloudHubUrl}. Đang thử kết nối lại...");
            });
        }

        private void InitDefaultFleet()
        {
            FleetRobots.Add(new FleetRobotItem
            {
                Id = "ROB01",
                Name = "Lê Hoàng 01 (OrionStar)",
                Ip = "192.168.0.134",
                Port = 8899,
                BatteryLevel = 55,
                IsOnline = true,
                State = "Available",
                CurrentTask = "Sẵn sàng đón khách"
            });
            FleetRobots.Add(new FleetRobotItem
            {
                Id = "ROB02",
                Name = "Lê Hoàng 02",
                Ip = "192.168.1.101",
                Port = 8088,
                BatteryLevel = 96,
                IsOnline = true,
                State = "Active",
                CurrentTask = "Giao món đến Bàn 5"
            });
            FleetRobots.Add(new FleetRobotItem
            {
                Id = "ROB03",
                Name = "Lê Hoàng 03",
                Ip = "192.168.1.102",
                Port = 8088,
                BatteryLevel = 72,
                IsOnline = true,
                State = "Active",
                CurrentTask = "Vận chuyển khay hàng tới Khu vực C"
            });
            FleetRobots.Add(new FleetRobotItem
            {
                Id = "ROB04",
                Name = "Lê Hoàng 04",
                Ip = "192.168.1.103",
                Port = 8088,
                BatteryLevel = 45,
                IsOnline = true,
                State = "Idle",
                CurrentTask = "Nghỉ tại trạm chờ"
            });
            FleetRobots.Add(new FleetRobotItem
            {
                Id = "ROB05",
                Name = "Lê Hoàng 05",
                Ip = "192.168.1.104",
                Port = 8088,
                BatteryLevel = 80,
                IsOnline = true,
                State = "Available",
                CurrentTask = "Sẵn sàng phục vụ"
            });
            FleetRobots.Add(new FleetRobotItem
            {
                Id = "ROB08",
                Name = "Lê Hoàng 08",
                Ip = "192.168.1.108",
                Port = 8088,
                BatteryLevel = 91,
                IsOnline = true,
                State = "Available",
                CurrentTask = "Tuần tra sảnh chính"
            });
        }

        private void InitDefaultAlerts()
        {
            RecentAlerts.Add(new AlertItem
            {
                Title = "Low Battery Warning",
                Description = "Robot ROB04 dung lượng pin dưới 50% (đang ở mức 45%)",
                Severity = AlertSeverity.Warning,
                Timestamp = "1 phút trước"
            });
            RecentAlerts.Add(new AlertItem
            {
                Title = "Route Obstruction",
                Description = "Phát hiện vật cản tại lối đi Bàn 3 - Robot tự động giảm tốc",
                Severity = AlertSeverity.Info,
                Timestamp = "5 phút trước"
            });
            RecentAlerts.Add(new AlertItem
            {
                Title = "Route Obstruction Cleared",
                Description = "Lối đi thông thoáng - Tiếp tục lộ trình dẫn khách",
                Severity = AlertSeverity.Success,
                Timestamp = "12 phút trước"
            });
            RecentAlerts.Add(new AlertItem
            {
                Title = "CoreService Status",
                Description = "OrionStar CoreService & SkillApi hoạt động ổn định",
                Severity = AlertSeverity.Success,
                Timestamp = "Vừa xong"
            });
        }

        private void InitDefaultTasks()
        {
            TaskQueue.Add(new RobotMissionTask
            {
                Title = "Giao món đến Bàn 3",
                Type = TaskType.Delivery,
                Destination = "Bàn 3",
                SpeechAnnouncement = "Món ăn đã đến bàn 3, chúc quý khách ngon miệng!",
                Status = MissionTaskStatus.Pending
            });
            TaskQueue.Add(new RobotMissionTask
            {
                Title = "Tuần tra Sảnh tiếp đón",
                Type = TaskType.Patrol,
                Destination = "Sảnh tiếp đón",
                SpeechAnnouncement = "Kính chào quý khách ghé thăm Lê Hoàng Robotics.",
                Status = MissionTaskStatus.Pending
            });
        }

        private const string DEFAULT_MAP_BASE64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCABMADQDASIAAhEBAxEB/8QAGwABAAIDAQEAAAAAAAAAAAAAAAkKAgcIBgH/xAAyEAAABQMCBAYCAgICAwAAAAAEBQYHEQECAxITCCExMiIkQkNiYwAJFDMWFxVBJSiB/8QAGgEAAgMBAQAAAAAAAAAAAAAAAgUDBgcECP/EACwRAAIBAgQEBgIDAQAAAAAAAAECAwQRABIhMQUTQYEGInGxwcMUIxYkUTL/2gAMAwEAAhEDEQA/ANA0pEcojT6YiNr6rYjT8I0e3t+TUpEcojT6YiNr6rYjT8I0e3t+TUpEcojT6YiNr6rYjT8I0e3t+TUpEcojT6YiNr6rYjT8I0e3t+TtGPNY6dvrxutr+Hx0HYojR6cIv4KUWr1t9w+la7Pq5S9I4HQcfdzEJHmF4AQszG/wS0BnOVJeQFJvlTZZcVZDQOEEHybDDJElV+pg7a3hYWPE68r2BG9DkaDSSzTre1atSminFmauQiDzlqEXtgozIBrdq616VGbtTnw4SdWFZYRFRE4h+ZEGc4O0Mgd5fqYXqB4hmqe39cvEoCRS6ZpyEsohicQixUauBKU/L1AGxWrxEojCHw4isEXFOIHncgvzpNTodwUSrMh6vUoCUOfKfKJspL/2AmoRScPy9Z5h+ENZPq7vF2s14SZCm3h1GEaOsXTDF5eSHL3PY4rp4GubxEnyeJ2YRqf4U3VcRaBxDjLQlYNQMtnWbXkFirTq2eqlSdY9I1DrdtCGQka3ZTl0uCQNCDqbY0PgnhzhlZwaXiBz1s7Uk2SI81WgrY0PkWOCaMzWZY2jRmvKsguEuuKcNKRHKI0+mIja+q2I0/CNHt7fk1KRHKI0+mIja+q2I0/CNHt7fk+qFHwT8WJKoj0nB8LfEzmCFJ0ZFoXMOZQ9EjsocAOqFwZRglvc7jIHOLvxYcd4jOh3EXaPyZtWRNLRSktoE8v56VqNVzfqEekl2lFIiVWU/wAL/lUyriEzTShLP5wEAZgf+QJTkAAMgX8wtFgzAJ/JDYN8CIDC8MB78WQMwV0b/llbS9gQTb/bb9cUSakqaYkT08sWV+WS8bBc4KgqHtkY+VrZWIIBIJAvjz9nKkdvby7elltOk4OkR21iNPg07OJ+LOVI7e3l29LLadJwdIjtrEafBp2cT8lXYevymOQ79l9hjClIjlEafTERtfVbEafhGj29vyalIjlEafTERtfVbEafhGj29vyalIjlEafTERtfVbEafhGj29vyalIjlEafTERtfVbEafhGj29vyYYMdO314tYfq3JThVfroGjWpKOFdRP82i0fM5YYYpgAo4AIB/hzdnKdRRtxHZk3gyrRJrUYSr/OQn41G1LloI4T1okCwpvoEUl4UVNSqFQmUOmVEtFooiJII5IERuqFYrFQbl6fTKXTKfLxBsfKJRHxsICFZIREhWEFGRubmQoMXlpeGEDBgjCGw5MltWD9aSg4eW0Y4e57usA7iqMsfGOwaHHvmk8uBPN625Ma5SpQIFROYrzhz25JA7RNK6CaLlc8ZPeEUWKp4qmDNTRGLUwLUhgRtlhr3SZjipaAMvW6OCN1WccLAskxUSMJBlSNRhiJQKBu10nTlOqorBCr8INQEaiSx8TnRTjsyZgQ4IJDZcF1daCqQrNIfNYyG5K2FyA1gQSDoetjboNQNy8NVSy8I4fFeBZEoYskS1OeZkivA0skRhiaJGkQWIEqjNlEjlQzwCftfw8Z6USJe64jiNUmThrcRSmaOTaBACD9qFfnK3dLFSssCVcFsyxsm/GWJxNIIiDN2OI3VVzgK/KZlZwozIOlci+PESjK9FKRHKI0+mIja+q2I0/CNHt7fk512/z/AKNzV2+KhQLIaa5kcYvplwNFjBqN8VK1BwlsaDRBysl2x3+o0GnRKdbA/ec/cxOJghPls5iREEiCKVowZwneHxVNslymHB46N7/t50/9R2w1H+yFv/rGmg8thvaKcf8A4XyU2P8AyS2E3aW8lBdQ8t0f+WusH4xFQjWkbyBMjrYA5miEatm18tiQxF9TYX3sNsZr4opytY1Sa2jn5krRGnh4m/EKiBoyoJlEkaNAhygLEWdo7BGdmBONdWcqR29vLt6WW06Tg6RHbWI0+DTs4n4s5Ujt7eXb0stp0nB0ie2sRp8GnZxPztXYevymKod+y+wxhSkRyiNPpiI2vqtiNPwjR7e35NSkRyiNPpiI2vqtiNPwjR7e35NSkRyiNPpiI2vqtiNPwjR7e35NSkRyiNPpiI2vqtiNPwjR7e35MMGOnb68TffpaLEe4LtZEiokmWmimacwHvSg1Xa7z6IhVo4KalZMjFvmTqHSGLM0a0LDwwL21TazLlWOQpwbps2EgTSropotsSaPsTcX3D0Q8VHDY7LFHxcWmdyyT4YclbDk2PyQoLXMRB2Vr9o1GamCYvoc2lyUdJLI9TjQmHAYhDIMUZC02JjwpFjiYdBf+kNhhR2PFvgYiVQPSpGtljYXlBcHKSpJpx12/bxPpNIqNTHgdwCdZqIyPWy4oXwIU0g6Noq2wxULjtZLU/TjgJplg2ezF+I6x/7LFSTkK2vcgMNSADsM2thoTc9TjafCVMf4/Cs0ar+UJSxVYkaSF7xxszx3Z25YCq0v7VUKlgiJij+/X62eJHhYJzVVP6csemkRuDAqMWqQWy6WxQs1F/GNDFOttjK7GiIVgn3KUJOmzk3pU4TGBnygKGCBx7zYTITjAAOCaUiOURp9MRG19VsRp+EaPb2/J3HHy/XCI4rHrdZWPybNmBbMcqE7VoRyBLXkH8R5OkgjVNoXKLCbOOuHjNWWSJaNdFNqKpK1CX4cjtBEaYEKNwwA8K+D4uKrSaD39trFsTw2PY2zTsWzwNvCzK1uBxD9U43FXauHKwcqFeoU2DIBKaWWY6DpoIjAyAvMQJ4UqYRmVORaDgBkRkmNGlhoc91NVCTJGxZpGBLEIFVbAnU3vr/oBFzsMUnxF4Ybh35NZTrDBw6FqdIleollqJ2mMSnIpjIURtmusjr5FLK8hIAios5Ujt7eXb0stp0nB0iO2sRp8GnZxPxZypHb28u3pZbTpODpEdtYjT4NOzifjFdh6/KYpJ37L7DGFKRHKI0+mIja+q2I0/CNHt7fk1KRHKI0+mIja+q2I0/CNHt7fk1KRHKI0+mIja+q2I0/CNHt7fk1KRHKI0+mIja+q2I0/CNHt7fkwwY6dvrxbJ/RBSOERxuUf+xyt/6jo17PU6bePpEdOUaYxxtY5rfyIj9LadSiU4SLwSeX5avTRWLQc5arxkKfUgAob84UBQUJDG2pgenJWWgzZakQJvcB0qSwLiDCicMpiAxDghyGUaAWy23dxT8Yrm8N5/w0nedlCIOz7sP0csQ7amdV00w3pg1QQQugBOiXjKx6VudMjUCUULfkDhucXpM9tSIy5M3pjE5CvZRWATpGCa/OpeplCgkl2sCCCbC+xsdQNNNem+N04JLHR+HuGyVDqkaU0Ad1ZZEUSyBFYtGWXKDIpdr2jGYvlytbpBasUCW7nYl2OWDgFhKOao2bRTJdMu/xEInENzY12kFwjlMmACAfBItqjj0oyFavJ1YrgrVmTtLciUJAlLHUTTepw/Q68q9ft9aVZNi7TZ3qAoMjEgMEVaTp91D5aOWrjdeHCfApcxXRUX4XT4i3+UpIikqpVNmM0uBFg28EhBKxPyXPjXuFPg3FNJDv2icD6GTVeI7j0IVcERR6p2uQyKcZPg2sBLk0XppnGAmzx/yTc8cEhIC0mW9lnDviVAkzSCqM0AY8M7TOk1lpI4CcHXqKuC5Lvug8g5OmrrLpSuGdJZOWpImPleYZjxQ409aojxVYy8wUA/HkOTr+OdKg8zBRZ2OHDgYPOHJwwkMTlBWAJ+2hiOkqyXXVXQqws2UHQ3sSNNbG69AbWqvjTiKZX4dUUJiqTypqWrSaGQSQLUsv7F5fNjR1VyEurLLcBmjDGXXVnKkdvby7elltOk4OkR21iNPg07OJ+LOVI7e3l29LLadJwdIjtrEafBp2cT8bLsPX5TGZHfsvsMYUpEcojT6YiNr6rYjT8I0e3t+TUpEcojT6YiNr6rYjT8I0e3t+Tz0Up09PTw2U7ZjpbSP6LekRPh06MO00Up09PTw2U7ZjpbSP6LekRPh06MO0GDHTt9eO3m8/ZTxnNOxYLhmbhRs+jmlLi1QlJapSZuF5g4gSsKqzw2VB7kKnQveO5FFRjmOj4zCATgvZ0IbkacyWYCQcVq8GFXBZ5gx48OLc/as7ZZWvQer5uVIRkqePCZyyVJuWZDwBAnU0mioTkVi+TShWGI9C4EsUH16owH4dSiV9jMXOGG+Rxjg8VWbkjRSnT09PDZTtmOltI/ot6RE+HTow7TRSnT09PDZTtmOltI/ot6RE+HTow7UQhiBLctSSxckjMcxIJILXI1ANhYAi4F8Mn4xxSWOGFq6pWGGmio0iikMEf48aCNI3SHlrKckjozyh5HRiruw0xvpd8U3ES56UCotwHfW6rIMZdgKDOw3NLsxurSkCpRCxJCtxVNaGwqZzC5JKYxNDhDAXDN1IFQIszNf8MxJ7EPH48egqUiOURp9MRG19VsRp+EaPb2/J56KU6enp4bKdsx0tpH9FvSInw6dGHaaKU6enp4bKdsx0tpH9FvSInw6dGHakACiygAXJsAALnc6dT1xwyTSzMrzSySsFRQ0rtIwVcmVQWJIVegvYdBhZypHb28u3pZbTpODpEdtYjT4NOzifnytdvlTp/wDbe2tcdOWPRTpZT/rl22xZS221+SLsPX5TEB37L7DH/9k=";

        private void InitDefaultMapAndStations()
        {
            CurrentMap = "tăng 2-1110154527";
            SelectedMap = "tăng 2-1110154527";
            if (!AvailableMaps.Contains("tăng 2-1110154527"))
            {
                AvailableMaps.Add("tăng 2-1110154527");
            }

            try
            {
                BitmapImage bmp;
                string hdPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "default_map_hd.png");
                if (!System.IO.File.Exists(hdPath))
                {
                    hdPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "default_map_hd.png");
                }

                if (System.IO.File.Exists(hdPath))
                {
                    bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.UriSource = new Uri(System.IO.Path.GetFullPath(hdPath));
                    bmp.EndInit();
                    bmp.Freeze();
                }
                else
                {
                    byte[] bytes = Convert.FromBase64String(DEFAULT_MAP_BASE64);
                    using var ms = new System.IO.MemoryStream(bytes);
                    bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.StreamSource = ms;
                    bmp.EndInit();
                    bmp.Freeze();
                }

                _rawMapSource = bmp;
                CurrentMapImage = IsWhiteMapTheme ? ConvertToCleanWhiteMap(bmp) : bmp;
                MockMapVisibility = Visibility.Collapsed;

                if (bmp.PixelHeight > 0 && bmp.PixelWidth > 0)
                {
                    double aspect = (double)bmp.PixelWidth / bmp.PixelHeight;
                    MapDisplayHeight = 440.0;
                    MapDisplayWidth = Math.Max(200.0, Math.Min(600.0, Math.Round(440.0 * aspect)));
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Default map image error: {ex.Message}");
            }

            SwitchToMapLocal(CurrentMap);
        }

        private void OnConnectionStateChanged(bool connected)
        {
            Application.Current.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Normal, () =>
            {
                IsConnected = connected;
                ConnectionStatusText = connected ? "ĐÃ KẾT NỐI" : "CHƯA KẾT NỐI";
                RobotState = connected ? "ONLINE" : "OFFLINE";
                var robotInFleet = FleetRobots.FirstOrDefault(r => r.Ip == RobotIp);
                if (robotInFleet != null)
                {
                    robotInFleet.IsOnline = connected;
                    robotInFleet.State = connected ? "Online" : "Offline";
                }

                // Cập nhật trạng thái cho robot trong EnterpriseRobots
                var currentBot = EnterpriseRobots.FirstOrDefault(r => string.Equals(r.Ip?.Trim(), RobotIp?.Trim(), StringComparison.OrdinalIgnoreCase));
                if (currentBot != null)
                {
                    currentBot.Status = connected ? (IsCharging ? "Charging" : "Online") : "Offline";
                    if (connected)
                    {
                        currentBot.BatteryLevel = BatteryLevel;
                        currentBot.LastSeen = DateTime.Now;
                    }
                }

                if (connected)
                {
                    _ = Task.Run(async () =>
                    {
                        await Task.Delay(400);
                        await RefreshMapAsync();
                    });
                }
            });
        }

        private void OnTelemetryReceived(TelemetryMessage telemetry)
        {
            Application.Current.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Normal, () =>
            {
                RobotId = telemetry.RobotId;
                if (telemetry.Battery != null)
                {
                    BatteryLevel = telemetry.Battery.Level;
                    IsCharging = telemetry.Battery.IsCharging;
                }

                // Cập nhật mức Pin và Trạng thái thời gian thực cho Robot trong EnterpriseRobots
                var connectedBot = EnterpriseRobots.FirstOrDefault(r => string.Equals(r.Ip?.Trim(), RobotIp?.Trim(), StringComparison.OrdinalIgnoreCase));
                if (connectedBot == null && !string.IsNullOrEmpty(RobotId))
                {
                    connectedBot = EnterpriseRobots.FirstOrDefault(r => r.Id == RobotId || (!string.IsNullOrEmpty(r.Name) && r.Name.Contains(RobotId)));
                }

                if (connectedBot != null)
                {
                    if (telemetry.Battery != null)
                    {
                        connectedBot.BatteryLevel = telemetry.Battery.Level;
                        connectedBot.IsCharging = telemetry.Battery.IsCharging;
                    }
                    connectedBot.Status = connectedBot.IsCharging ? "Charging" : "Online";
                    connectedBot.LastSeen = DateTime.Now;

                    // Lưu định kỳ vào SQLite (nếu mức pin đổi và cách nhau >= 5 giây)
                    if ((DateTime.Now - _lastRobotDbSave > TimeSpan.FromSeconds(5)) && _lastSavedBattery != connectedBot.BatteryLevel)
                    {
                        _lastRobotDbSave = DateTime.Now;
                        _lastSavedBattery = connectedBot.BatteryLevel;
                        var botToSave = connectedBot;
                        _ = Task.Run(async () =>
                        {
                            try
                            {
                                await DatabaseService.Instance.UpdateRobotAsync(botToSave);
                            }
                            catch { }
                        });
                    }
                }

                if (telemetry.Pose != null)
                {
                    IsEstimated = telemetry.Pose.IsEstimated;
                    LocalizationStatusText = IsEstimated ? "🟢 Đã định vị" : "⚠️ Mất định vị!";

                    PoseX = Math.Round(telemetry.Pose.X, 2);
                    PoseY = Math.Round(telemetry.Pose.Y, 2);

                    // Convert radians to real degrees (-180 to +180)
                    double deg = telemetry.Pose.Theta * 180.0 / Math.PI;
                    while (deg > 180.0) deg -= 360.0;
                    while (deg <= -180.0) deg += 360.0;
                    PoseTheta = Math.Round(deg, 1);

                    // Calibrate Robot Heading degrees matching OrionStar MapView:
                    // angle = 0 - theta; degrees = toDegrees(angle) + 90
                    double heading = (-telemetry.Pose.Theta * 180.0 / Math.PI) + 90.0;
                    while (heading > 180.0) heading -= 360.0;
                    while (heading <= -180.0) heading += 360.0;
                    RobotHeadingDegrees = Math.Round(heading, 1);

                    CanvasRobotLeft = WorldToCanvasX(telemetry.Pose.X);
                    CanvasRobotTop = WorldToCanvasY(telemetry.Pose.Y);

                    var currentPoint = new Point(CanvasRobotLeft, CanvasRobotTop);
                    if (TrajectoryPath.Count == 0 ||
                        (Math.Abs(TrajectoryPath.Last().X - currentPoint.X) > 6 ||
                         Math.Abs(TrajectoryPath.Last().Y - currentPoint.Y) > 6))
                    {
                        TrajectoryPath.Add(currentPoint);
                        if (TrajectoryPath.Count > 100)
                        {
                            TrajectoryPath.RemoveAt(0);
                        }
                    }
                }

                if (telemetry.Status != null)
                {
                    RobotState = telemetry.Status.State;
                    string incomingMap = string.IsNullOrEmpty(telemetry.Status.CurrentMap) ? "--" : telemetry.Status.CurrentMap;
                    if (incomingMap != "--" && incomingMap != "Bản đồ Mặc định" && !string.Equals(incomingMap, CurrentMap, StringComparison.OrdinalIgnoreCase))
                    {
                        SwitchToMapLocal(incomingMap);
                    }
                    else if (incomingMap != "--" && incomingMap != "Bản đồ Mặc định")
                    {
                        CurrentMap = incomingMap;
                    }
                    CurrentPlace = string.IsNullOrEmpty(telemetry.Status.CurrentPlace) ? "--" : telemetry.Status.CurrentPlace;
                    IsEmergencyStopActive = telemetry.Status.EmergencyStop;
                    DoorStatus = telemetry.Status.DoorStatus;
                }

                // Đồng bộ Telemetry trực tiếp lên Cloud Server (Option B Centralized Cloud Hub)
                if (_cloudBridge != null && _cloudBridge.IsConnected)
                {
                    var packet = new RobotTelemetryPacket
                    {
                        RobotId = string.IsNullOrEmpty(RobotId) ? "LH-ORIONSTAR-02" : RobotId,
                        OrgId = SelectedOrganization?.Id ?? "ORG-01",
                        SiteId = SelectedSite?.Id ?? "SITE-01",
                        Status = IsConnected ? (IsCharging ? "Charging" : "Online") : "Offline",
                        Battery = BatteryLevel,
                        IsCharging = IsCharging,
                        MapName = CurrentMap,
                        PoseX = PoseX,
                        PoseY = PoseY,
                        PoseAngle = RobotHeadingDegrees,
                        ActiveMode = RobotState,
                        Timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                    };
                    _ = _cloudBridge.SendTelemetryAsync(packet);
                }
            });
        }

        private void OnMapDataReceived(MapDataMessage data)
        {
            Application.Current.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Normal, () =>
            {
                CurrentMap = data.CurrentMap;
                SelectedMap = data.CurrentMap;

                // 1. Snapshot all currently configured map names before updating AvailableMaps
                var savedMapSelections = new Dictionary<string, string>();
                foreach (var cfg in FloorConfigs)
                {
                    if (!string.IsNullOrEmpty(cfg.MapName))
                    {
                        savedMapSelections[cfg.FloorLabel] = cfg.MapName;
                    }
                }

                // 2. Only modify AvailableMaps if elements changed to avoid ComboBox selection reset
                bool listsMatch = AvailableMaps.Count == data.MapList.Count && AvailableMaps.SequenceEqual(data.MapList);
                if (!listsMatch)
                {
                    AvailableMaps.Clear();
                    foreach (var m in data.MapList)
                    {
                        AvailableMaps.Add(m);
                    }
                }

                // 3. Re-affirm each floor's MapName so ComboBox doesn't wipe them
                foreach (var cfg in FloorConfigs)
                {
                    if (savedMapSelections.TryGetValue(cfg.FloorLabel, out var priorMap) && !string.IsNullOrEmpty(priorMap))
                    {
                        cfg.MapName = priorMap;
                    }
                    else if (string.IsNullOrEmpty(cfg.MapName) || !AvailableMaps.Contains(cfg.MapName))
                    {
                        var match = AvailableMaps.FirstOrDefault(m => 
                            m.Contains($"tang {cfg.FloorNumber}", StringComparison.OrdinalIgnoreCase) ||
                            m.Contains($"tăng {cfg.FloorNumber}", StringComparison.OrdinalIgnoreCase) ||
                            m.Contains($"t{cfg.FloorNumber}", StringComparison.OrdinalIgnoreCase) ||
                            m.StartsWith($"{cfg.FloorNumber}-", StringComparison.OrdinalIgnoreCase) ||
                            m.Contains($"floor {cfg.FloorNumber}", StringComparison.OrdinalIgnoreCase));
                        if (match != null)
                        {
                            cfg.MapName = match;
                        }
                    }
                }

                int bmpW = 52;
                int bmpH = 76;

                bool needReloadImage = _rawMapSource == null || !string.Equals(data.CurrentMap, CurrentMap, StringComparison.OrdinalIgnoreCase);
                if (!string.IsNullOrEmpty(data.ImageBase64) && needReloadImage)
                {
                    try
                    {
                        byte[] bytes = Convert.FromBase64String(data.ImageBase64);
                        using var ms = new System.IO.MemoryStream(bytes);
                        var bmp = new BitmapImage();
                        bmp.BeginInit();
                        bmp.CacheOption = BitmapCacheOption.OnLoad;
                        bmp.StreamSource = ms;
                        bmp.EndInit();
                        bmp.Freeze();

                        _rawMapSource = bmp;
                        bmpW = bmp.PixelWidth;
                        bmpH = bmp.PixelHeight;

                        CurrentMapImage = IsWhiteMapTheme ? ConvertToCleanWhiteMap(bmp) : bmp;
                        MockMapVisibility = Visibility.Collapsed;

                        if (bmp.PixelHeight > 0 && bmp.PixelWidth > 0)
                        {
                            double aspect = (double)bmp.PixelWidth / bmp.PixelHeight;
                            MapDisplayHeight = 440.0;
                            MapDisplayWidth = Math.Max(200.0, Math.Min(600.0, Math.Round(440.0 * aspect)));
                        }

                        AddLog($"[BẢN ĐỒ] Đã tải bản đồ '{data.CurrentMap}' ({bytes.Length} bytes, {data.Places.Count} vị trí)");
                    }
                    catch (Exception ex)
                    {
                        AddLog($"[LỖI TẢI ẢNH BẢN ĐỒ] {ex.Message}");
                    }
                }
                else if (_rawMapSource != null)
                {
                    bmpW = _rawMapSource.PixelWidth;
                    bmpH = _rawMapSource.PixelHeight;
                    MockMapVisibility = Visibility.Collapsed;
                }
                else
                {
                    MockMapVisibility = Visibility.Visible;
                }

                UpdateMapBounds(data.Places, data.CurrentMap, data.Resolution, data.OriginX, data.OriginY, bmpW, bmpH);

                // Strict floor isolation with safe point preservation:
                // Merge data.Places (from robot SLAM) with local saved stations for data.CurrentMap
                var placesDict = new Dictionary<string, MapPlaceItem>(StringComparer.OrdinalIgnoreCase);

                // 1. Preload locally saved stations for this map so user-created points are never wiped out
                var allSaved = LoadAllSavedStations();
                var matchedLocal = FindStationsForMap(allSaved, data.CurrentMap);
                if (matchedLocal != null)
                {
                    foreach (var s in matchedLocal)
                    {
                        placesDict[s.Name] = new MapPlaceItem
                        {
                            Name = s.Name,
                            X = s.X,
                            Y = s.Y,
                            Icon = (s.Name.ToLowerInvariant().Contains("charge") || s.Name.ToLowerInvariant().Contains("sạc")) ? "⚡" : "📍"
                        };
                    }
                }

                // 2. Merge places from robot (authoritative)
                if (data.Places != null && data.Places.Count > 0)
                {
                    foreach (var p in data.Places)
                    {
                        placesDict[p.Name] = p;
                        p.Icon = (p.Name.ToLowerInvariant().Contains("charge") || p.Name.ToLowerInvariant().Contains("sạc")) ? "⚡" : "📍";
                    }
                }

                // 3. Render strictly for this floor
                MapStations.Clear();
                foreach (var p in placesDict.Values)
                {
                    p.CanvasX = WorldToCanvasX(p.X);
                    p.CanvasY = WorldToCanvasY(p.Y);
                    MapStations.Add(p);
                }

                SaveCurrentMapStationsToDisk();
            UpdateAvailableStationNames();
                UpdateAvailableStationNames();

                CanvasRobotLeft = WorldToCanvasX(PoseX);
                CanvasRobotTop = WorldToCanvasY(PoseY);
            });
        }

        private void OnCommandResultReceived(CommandResultMessage result)
        {
            Application.Current.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Normal, () =>
            {
                if (result.Success)
                {
                    AddLog($"[ROBOT THÀNH CÔNG] {result.Command}: {result.Message}");
                }
                else
                {
                    AddLog($"[ROBOT PHẢN HỒI] {result.Command} (Mã {result.ResultCode}): {result.Message}");
                }
            });
        }

        private void OnLogOccurred(string log)
        {
            Application.Current.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, () =>
            {
                AddLog(log);
            });
        }

        public void AddLog(string log)
        {
            LogHistory.Insert(0, log);
            if (LogHistory.Count > 300)
            {
                LogHistory.RemoveAt(LogHistory.Count - 1);
            }
        }

        // --- Diagnostics & Connection Commands ---

        [RelayCommand]
        public async Task RunDiagnosticAsync()
        {
            IsDiagnosing = true;
            DiagnosticMessage = $"Đang kiểm tra kết nối tới {RobotIp}:{RobotPort}...";
            AddLog($"[CHẨN ĐOÁN MẠNG] Bắt đầu kiểm tra tới {RobotIp}:{RobotPort}...");

            var diag = await NetworkDiagnosticService.TestRobotConnectionAsync(RobotIp, RobotPort);
            DiagnosticMessage = diag.Message;
            AddLog($"[KẾT QUẢ CHẨN ĐOÁN] {diag.Message}");
            IsDiagnosing = false;

            if (diag.PortOpen)
            {
                // Auto connect if port is open
                await ToggleConnectionAsync();
            }
        }

        [RelayCommand]
        public async Task ScanSubnetAsync()
        {
            IsDiagnosing = true;
            DiagnosticMessage = "Đang quét mạng LAN để tìm Robot OrionStar (cổng 8088)...";
            AddLog("[QUÉT MẠNG LAN] Đang tìm kiếm thiết bị trên dải mạng nội bộ...");

            string baseIp = "192.168.1";
            var parts = RobotIp.Split('.');
            if (parts.Length == 4)
            {
                baseIp = $"{parts[0]}.{parts[1]}.{parts[2]}";
            }

            var discovered = await NetworkDiagnosticService.ScanSubnetForRobotsAsync(baseIp, RobotPort);
            IsDiagnosing = false;

            if (discovered.Count > 0)
            {
                var first = discovered.First();
                RobotIp = first.IpAddress;
                DiagnosticMessage = $"✅ ĐÃ TÌM THẤY {discovered.Count} ROBOT! Đã tự động điền IP: {first.IpAddress}";
                AddLog($"[TÌM THẤY ROBOT] IP: {first.IpAddress}:{first.Port}");
            }
            else
            {
                DiagnosticMessage = $"⚠️ Không tìm thấy robot nào trên dải {baseIp}.x mở cổng {RobotPort}. Vui lòng mở APK trên robot!";
                AddLog($"[QUÉT MẠNG] Không phát hiện thiết bị trên dải {baseIp}.1-254.");
            }
        }

        [RelayCommand]
        public void ToggleSimulation()
        {
            IsSimulationMode = !IsSimulationMode;
            if (IsSimulationMode)
            {
                _simulationTimer.Start();
                ConnectionStatusText = "MÔ PHỎNG (DEMO)";
                RobotState = "ACTIVE";
                AddLog("[CHẾ ĐỘ MÔ PHỎNG] Đã bật chế độ giả lập hoạt động robot.");
            }
            else
            {
                _simulationTimer.Stop();
                ConnectionStatusText = IsConnected ? "ĐÃ KẾT NỐI" : "CHƯA KẾT NỐI";
                RobotState = IsConnected ? "AVAILABLE" : "OFFLINE";
                AddLog("[CHẾ ĐỘ MÔ PHỎNG] Đã tắt chế độ giả lập.");
            }
        }

        private double _simAngle = 0;
        private void SimulationTimer_Tick(object? sender, EventArgs e)
        {
            if (!IsSimulationMode) return;

            _simAngle += 0.04;
            if (_simAngle > Math.PI * 2) _simAngle = 0;

            // Move simulated robot smoothly along the actual building hallway
            PoseX = Math.Round(1.8 + Math.Cos(_simAngle * 2) * 0.4, 2);
            PoseY = Math.Round(3.6 + Math.Sin(_simAngle) * 4.2, 2);
            RobotHeadingDegrees = Math.Round(Math.Cos(_simAngle) >= 0 ? 0.0 : 180.0, 1);
            PoseTheta = RobotHeadingDegrees;

            CanvasRobotLeft = WorldToCanvasX(PoseX);
            CanvasRobotTop = WorldToCanvasY(PoseY);

            var pt = new Point(CanvasRobotLeft, CanvasRobotTop);
            TrajectoryPath.Add(pt);
            if (TrajectoryPath.Count > 100) TrajectoryPath.RemoveAt(0);
        }

        // --- Navigation Tabs ---
        [RelayCommand]
        public void SwitchView(string viewName)
        {
            ActiveView = viewName;
        }

        // --- Commands to Robot ---
        [RelayCommand]
        public async Task ToggleConnectionAsync()
        {
            if (IsConnected)
            {
                await _robotService.DisconnectAsync();
            }
            else
            {
                try
                {
                    await _robotService.ConnectAsync(RobotIp, RobotPort);
                }
                catch (Exception ex)
                {
                    AddLog($"[KẾT NỐI THẤT BẠI] {ex.Message}");
                }
            }
        }

        [RelayCommand]
        public async Task MoveAsync(string direction)
        {
            var cmd = new MoveCommand
            {
                Action = direction.ToUpperInvariant(),
                LinearSpeed = LinearSpeed,
                AngularSpeed = AngularSpeed
            };
            await _robotService.SendCommandAsync(cmd);
        }

        private DispatcherTimer? _movePulseTimer;
        private string _activeDirection = "";

        [RelayCommand]
        public void StartHoldMove(string direction)
        {
            _activeDirection = direction.ToUpperInvariant();
            _ = MoveAsync(_activeDirection);

            _movePulseTimer?.Stop();
            _movePulseTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(300) };
            _movePulseTimer.Tick += (s, e) =>
            {
                if (IsConnected && !string.IsNullOrEmpty(_activeDirection))
                {
                    _ = MoveAsync(_activeDirection);
                }
            };
            _movePulseTimer.Start();
        }

        [RelayCommand]
        public void StopHoldMove()
        {
            _movePulseTimer?.Stop();
            _activeDirection = "";
            _ = MoveAsync("STOP");
        }

        [RelayCommand]
        public async Task SwitchMapAsync()
        {
            if (string.IsNullOrEmpty(SelectedMap)) return;
            AddLog($"[CHUYỂN BẢN ĐỒ] Đang đổi sang bản đồ: {SelectedMap}...");
            await _robotService.SendCommandAsync(new SwitchMapCommand { MapName = SelectedMap });
        }

        [RelayCommand]
        public async Task RefreshMapAsync()
        {
            AddLog("[TẢI BẢN ĐỒ] Yêu cầu cập nhật dữ liệu bản đồ từ robot...");
            await _robotService.SendCommandAsync(new GetMapDataCommand());
        }

        [RelayCommand]
        public void ToggleRelocalizeMode()
        {
            if (!IsConnected)
            {
                System.Windows.MessageBox.Show($"Robot hiện CHƯA KẾT NỐI!\n\nVui lòng kiểm tra địa chỉ IP Robot ({RobotIp}:{RobotPort}) và bấm nút 'KẾT NỐI' ở góc trên bên phải trước khi đặt lại vị trí.", "Chưa kết nối Robot", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Warning);
                AddLog("[CẢNH BÁO] Không thể đặt lại vị trí vì chưa kết nối tới Robot.");
                return;
            }

            IsRelocalizeMode = !IsRelocalizeMode;
            if (IsRelocalizeMode)
            {
                AddLog("📍 [CHẾ ĐỘ ĐỊNH VỊ] Đã bật chế độ Đặt lại vị trí. Hãy click chuột lên bản đồ tại chỗ robot đang đứng!");
            }
            else
            {
                AddLog("📍 [CHẾ ĐỘ ĐIỀU HƯỚNG] Đã chuyển về chế độ Click để Robot chạy tới điểm.");
            }
        }

        [RelayCommand]
        public async Task SetPoseEstimateAsync(object? param = null)
        {
            if (!IsConnected)
            {
                System.Windows.MessageBox.Show("Chưa kết nối tới Robot! Vui lòng bấm 'KẾT NỐI' ở góc trên bên phải.", "Chưa kết nối", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Warning);
                return;
            }

            double x = ManualTargetX;
            double y = ManualTargetY;
            double theta = PoseTheta * Math.PI / 180.0;
            AddLog($"[ĐỊNH VỊ] Đang đặt lại vị trí robot tại tọa độ ({x:F2}m, {y:F2}m)...");
            await _robotService.SendCommandAsync(new SetPoseEstimateCommand { X = x, Y = y, Theta = theta });
        }

        [RelayCommand]
        public async Task SetLocationAsync(object? param = null)
        {
            if (!IsConnected)
            {
                System.Windows.MessageBox.Show("Chưa kết nối tới Robot! Vui lòng bấm 'KẾT NỐI' ở góc trên bên phải.", "Chưa kết nối", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Warning);
                return;
            }

            string placeName = "";
            if (param is MapPlaceItem place) placeName = place.Name;
            else if (param is string s) placeName = s;

            if (string.IsNullOrEmpty(placeName)) return;
            AddLog($"[ĐỊNH VỊ] Đang đặt vị trí robot theo điểm trạm: '{placeName}'...");
            await _robotService.SendCommandAsync(new SetLocationCommand { PlaceName = placeName });
        }

        [RelayCommand]
        public async Task ResetEstimateAsync()
        {
            if (!IsConnected)
            {
                System.Windows.MessageBox.Show($"Robot hiện CHƯA KẾT NỐI!\n\nVui lòng kiểm tra địa chỉ IP Robot ({RobotIp}:{RobotPort}) và bấm nút 'KẾT NỐI' ở góc trên bên phải trước khi gửi lệnh quét định vị.", "Chưa kết nối Robot", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Warning);
                AddLog("[CẢNH BÁO] Chưa kết nối tới Robot. Không thể gửi lệnh quét định vị.");
                return;
            }

            AddLog("[ĐỊNH VỊ] Yêu cầu robot tự động xoay 360° quét Laser tìm lại vị trí trên bản đồ...");
            await _robotService.SendCommandAsync(new ResetEstimateCommand());
        }

        // ==========================================
        // STATION MAKER / TẠO ĐIỂM MỚI TRÊN BẢN ĐỒ
        // ==========================================
        [RelayCommand]
        public void ToggleStationMaker()
        {
            IsStationMakerExpanded = !IsStationMakerExpanded;
        }

        [RelayCommand]
        public void ToggleAddPointMode()
        {
            IsAddPointMode = !IsAddPointMode;
            if (IsAddPointMode)
            {
                IsRelocalizeMode = false;
                AddLog("🎯 [TẠO ĐIỂM] Đã bật chế độ 'Chấm trên map'. Hãy bấm vào vị trí bất kỳ trên bản đồ để lấy tọa độ tạo điểm.");
            }
            else
            {
                AddLog("🎯 [TẠO ĐIỂM] Đã tắt chế độ 'Chấm trên map'.");
            }
        }

        public void CreatePointAtCanvas(double canvasX, double canvasY)
        {
            if (MapDisplayWidth <= 0 || MapDisplayHeight <= 0) return;

            double normX = Math.Clamp(canvasX / MapDisplayWidth, 0.0, 1.0);
            double normY = Math.Clamp(canvasY / MapDisplayHeight, 0.0, 1.0);

            double spanX = Math.Max(0.1, MapMaxX - MapMinX);
            double spanY = Math.Max(0.1, MapMaxY - MapMinY);

            double worldX = Math.Round(MapMinX + normX * spanX, 2);
            double worldY = Math.Round(MapMaxY - normY * spanY, 2);

            NewPlaceX = worldX;
            NewPlaceY = worldY;

            if (string.IsNullOrWhiteSpace(NewPlaceName))
            {
                NewPlaceName = $"Diem_{MapStations.Count + 1}";
            }

            AddLog($"🎯 [TẠO ĐIỂM] Đã lấy tọa độ trên bản đồ: ({worldX:F2}m, {worldY:F2}m) cho điểm '{NewPlaceName}'. Bấm 'Lưu điểm' để xác nhận.");
            IsAddPointMode = false;
            IsStationMakerExpanded = true;
        }

        [RelayCommand]
        public void CaptureRobotPoseForNewPlace()
        {
            NewPlaceX = Math.Round(PoseX, 2);
            NewPlaceY = Math.Round(PoseY, 2);
            if (string.IsNullOrWhiteSpace(NewPlaceName))
            {
                NewPlaceName = $"Diem_{MapStations.Count + 1}";
            }
            AddLog($"📍 [TẠO ĐIỂM] Đã lấy tọa độ Robot: ({NewPlaceX:F2}m, {NewPlaceY:F2}m)");
            IsStationMakerExpanded = true;
        }

        private readonly string _stationsConfigFile = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "saved_stations.json");

        public static string NormalizeMapKey(string s)
        {
            if (string.IsNullOrWhiteSpace(s)) return "";
            return s.Replace("ă", "a").Replace("ầ", "a").Replace("â", "a").Replace("á", "a").Replace("à", "a")
                    .Replace("Ă", "A").Replace("Ầ", "A").Replace("Â", "A")
                    .Replace("ê", "e").Replace("ế", "e").Replace("ề", "e").Replace("ệ", "e")
                    .Replace("ô", "o").Replace("ồ", "o").Replace("ố", "o").Replace("ơ", "o")
                    .Replace("ư", "u").Replace("ừ", "u").Replace("ứ", "u")
                    .Replace("đ", "d").Replace("Đ", "D")
                    .Trim().ToLowerInvariant();
        }

        private List<SavedStationRecord>? FindStationsForMap(Dictionary<string, List<SavedStationRecord>> allSaved, string mapName)
        {
            if (allSaved == null || string.IsNullOrWhiteSpace(mapName)) return null;
            if (allSaved.TryGetValue(mapName, out var direct)) return direct;

            string norm = NormalizeMapKey(mapName);
            foreach (var kvp in allSaved)
            {
                if (NormalizeMapKey(kvp.Key) == norm) return kvp.Value;
            }

            var parts = mapName.Split('-');
            if (parts.Length > 1)
            {
                string suffix = parts[parts.Length - 1].Trim();
                if (!string.IsNullOrEmpty(suffix))
                {
                    foreach (var kvp in allSaved)
                    {
                        if (kvp.Key.EndsWith(suffix, StringComparison.OrdinalIgnoreCase)) return kvp.Value;
                    }
                }
            }
            return null;
        }

        public void SwitchToMapLocal(string mapName)
        {
            if (string.IsNullOrWhiteSpace(mapName)) return;
            CurrentMap = mapName;
            if (SelectedMap != mapName)
            {
                SelectedMap = mapName;
            }

            MapStations.Clear();
            var allSaved = LoadAllSavedStations();
            var matchedStations = FindStationsForMap(allSaved, mapName);

            var activePlaces = new List<MapPlaceItem>();
            if (matchedStations != null && matchedStations.Count > 0)
            {
                foreach (var s in matchedStations)
                {
                    activePlaces.Add(new MapPlaceItem
                    {
                        Name = s.Name,
                        X = s.X,
                        Y = s.Y,
                        Icon = (s.Name.ToLowerInvariant().Contains("charge") || s.Name.ToLowerInvariant().Contains("sạc")) ? "⚡" : "📍"
                    });
                }
            }

            UpdateMapBounds(activePlaces, mapName);

            foreach (var p in activePlaces)
            {
                p.CanvasX = WorldToCanvasX(p.X);
                p.CanvasY = WorldToCanvasY(p.Y);
                MapStations.Add(p);
            }

            CanvasRobotLeft = WorldToCanvasX(PoseX);
            CanvasRobotTop = WorldToCanvasY(PoseY);

            UpdateAvailableStationNames();
            AddLog($"🗺️ [CHUYỂN BẢN ĐỒ] Đang hiển thị bản đồ '{mapName}' ({MapStations.Count} điểm trạm)");
        }

        private Dictionary<string, List<SavedStationRecord>> LoadAllSavedStations()
        {
            try
            {
                string path = _stationsConfigFile;
                if (!System.IO.File.Exists(path))
                {
                    string projectPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "saved_stations.json");
                    if (System.IO.File.Exists(projectPath)) path = projectPath;
                }

                if (System.IO.File.Exists(path))
                {
                    string json = System.IO.File.ReadAllText(path);
                    var dict = System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, List<SavedStationRecord>>>(json, 
                        new System.Text.Json.JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                    return dict ?? new Dictionary<string, List<SavedStationRecord>>(StringComparer.OrdinalIgnoreCase);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine("LoadAllSavedStations error: " + ex.Message);
            }
            return new Dictionary<string, List<SavedStationRecord>>(StringComparer.OrdinalIgnoreCase);
        }

        private void SaveCurrentMapStationsToDisk()
        {
            try
            {
                if (string.IsNullOrEmpty(CurrentMap)) return;
                var all = LoadAllSavedStations();
                var currentList = MapStations.Select(s => new SavedStationRecord
                {
                    Name = s.Name,
                    X = s.X,
                    Y = s.Y,
                    Theta = 0
                }).ToList();

                all[CurrentMap] = currentList;

                // Also populate diacritic variant keys
                if (CurrentMap.Contains("tăng 2-1110154527"))
                {
                    all["tầng 2-1110154527"] = currentList;
                }
                else if (CurrentMap.Contains("tầng 2-1110154527"))
                {
                    all["tăng 2-1110154527"] = currentList;
                }

                var options = new System.Text.Json.JsonSerializerOptions { WriteIndented = true };
                string json = System.Text.Json.JsonSerializer.Serialize(all, options);
                System.IO.File.WriteAllText(_stationsConfigFile, json);

                string projectPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "saved_stations.json");
                if (System.IO.Directory.Exists(System.IO.Path.GetDirectoryName(projectPath)))
                {
                    try { System.IO.File.WriteAllText(projectPath, json); } catch { }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine("SaveCurrentMapStationsToDisk error: " + ex.Message);
            }
        }

        private void AddOrUpdateMapStation(string name, double x, double y)
        {
            var existing = MapStations.FirstOrDefault(s => s.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
            if (existing != null)
            {
                existing.X = x;
                existing.Y = y;
                existing.CanvasX = WorldToCanvasX(x);
                existing.CanvasY = WorldToCanvasY(y);
            }
            else
            {
                MapStations.Add(new MapPlaceItem
                {
                    Name = name,
                    X = x,
                    Y = y,
                    CanvasX = WorldToCanvasX(x),
                    CanvasY = WorldToCanvasY(y),
                    Icon = (name.ToLowerInvariant().Contains("charge") || name.ToLowerInvariant().Contains("sạc")) ? "⚡" : "📍"
                });
            }
            SaveCurrentMapStationsToDisk();
            UpdateAvailableStationNames();
            UpdateAvailableStationNames();
        }

        [RelayCommand]
        public async Task SaveNewPlaceAsync()
        {
            string name = NewPlaceName?.Trim() ?? "";
            if (string.IsNullOrWhiteSpace(name))
            {
                name = $"Diem_{MapStations.Count + 1}";
            }

            double x = NewPlaceX;
            double y = NewPlaceY;
            double theta = PoseTheta * Math.PI / 180.0;

            // 1. Instantly update MapStations locally
            AddOrUpdateMapStation(name, x, y);
            AddLog($"➕ [TẠO ĐIỂM] Đã thêm điểm '{name}' ({x:F2}m, {y:F2}m) vào danh sách.");

            // 2. Send to robot
            if (_robotService.IsConnected)
            {
                await _robotService.SendCommandAsync(new AddPlaceCommand
                {
                    MapName = CurrentMap,
                    Name = name,
                    X = x,
                    Y = y,
                    Theta = theta
                });
            }
            else
            {
                AddLog("ℹ️ [LƯU Ý] Chưa kết nối tới Robot. Điểm đã lưu cục bộ trên giao diện.");
            }

            NewPlaceName = "";
        }

        [RelayCommand]
        public async Task CreateWaitingElevatorPointAsync()
        {
            string floorNum = !string.IsNullOrWhiteSpace(TargetFloorInput) ? TargetFloorInput.Trim() : "1";
            var activeCfg = FloorConfigs.FirstOrDefault(f => f.MapName == CurrentMap);
            if (activeCfg != null && string.IsNullOrWhiteSpace(TargetFloorInput))
            {
                floorNum = activeCfg.FloorNumber;
            }

            string pointName = !string.IsNullOrWhiteSpace(NewPlaceName) ? NewPlaceName.Trim() : $"ChoThangMay_T{floorNum}";
            double x = (NewPlaceX != 0.0 || NewPlaceY != 0.0) ? NewPlaceX : Math.Round(PoseX, 2);
            double y = (NewPlaceX != 0.0 || NewPlaceY != 0.0) ? NewPlaceY : Math.Round(PoseY, 2);
            double theta = PoseTheta * Math.PI / 180.0;

            // 1. Instantly update MapStations
            AddOrUpdateMapStation(pointName, x, y);

            // 2. Update FloorConfigs
            var floorCfg = FloorConfigs.FirstOrDefault(f => f.FloorNumber == floorNum);
            if (floorCfg != null)
            {
                floorCfg.OutsideWaitingPoint = pointName;
                SaveFloorConfigs();
                AddLog($"⚙️ [CẤU HÌNH THANG MÁY] Đã tự động gán '{pointName}' vào ô 'Điểm chờ ngoài sảnh' của Tầng {floorNum}.");
            }

            AddLog($"🚪 [THANG MÁY] Đã tạo 'Điểm Chờ thang máy' Tầng {floorNum}: '{pointName}' tại ({x:F2}m, {y:F2}m).");

            if (_robotService.IsConnected)
            {
                await _robotService.SendCommandAsync(new AddPlaceCommand
                {
                    MapName = CurrentMap,
                    Name = pointName,
                    X = x,
                    Y = y,
                    Theta = theta
                });
            }
            else
            {
                AddLog("ℹ️ [LƯU Ý] Chưa kết nối tới Robot. Điểm đã lưu cục bộ trên giao diện.");
            }

            NewPlaceName = "";
        }

        [RelayCommand]
        public async Task CreateInsideElevatorPointAsync()
        {
            string floorNum = !string.IsNullOrWhiteSpace(TargetFloorInput) ? TargetFloorInput.Trim() : "1";
            var activeCfg = FloorConfigs.FirstOrDefault(f => f.MapName == CurrentMap);
            if (activeCfg != null && string.IsNullOrWhiteSpace(TargetFloorInput))
            {
                floorNum = activeCfg.FloorNumber;
            }

            string pointName = !string.IsNullOrWhiteSpace(NewPlaceName) ? NewPlaceName.Trim() : $"TrongThangMay_T{floorNum}";
            double x = (NewPlaceX != 0.0 || NewPlaceY != 0.0) ? NewPlaceX : Math.Round(PoseX, 2);
            double y = (NewPlaceX != 0.0 || NewPlaceY != 0.0) ? NewPlaceY : Math.Round(PoseY, 2);
            double theta = PoseTheta * Math.PI / 180.0;

            // 1. Instantly update MapStations
            AddOrUpdateMapStation(pointName, x, y);

            // 2. Update FloorConfigs
            var floorCfg = FloorConfigs.FirstOrDefault(f => f.FloorNumber == floorNum);
            if (floorCfg != null)
            {
                floorCfg.InsideCabinPoint = pointName;
                SaveFloorConfigs();
                AddLog($"⚙️ [CẤU HÌNH THANG MÁY] Đã tự động gán '{pointName}' vào ô 'Điểm trong cabin' của Tầng {floorNum}.");
            }

            AddLog($"🛗 [THANG MÁY] Đã tạo 'Điểm Trong thang máy' Tầng {floorNum}: '{pointName}' tại ({x:F2}m, {y:F2}m).");

            if (_robotService.IsConnected)
            {
                await _robotService.SendCommandAsync(new AddPlaceCommand
                {
                    MapName = CurrentMap,
                    Name = pointName,
                    X = x,
                    Y = y,
                    Theta = theta
                });
            }
            else
            {
                AddLog("ℹ️ [LƯU Ý] Chưa kết nối tới Robot. Điểm đã lưu cục bộ trên giao diện.");
            }

            NewPlaceName = "";
        }

        [RelayCommand]
        public async Task DeletePlaceAsync(object? param)
        {
            string placeName = "";
            MapPlaceItem? toRemove = null;
            if (param is MapPlaceItem place)
            {
                placeName = place.Name;
                toRemove = place;
            }
            else if (param is string s)
            {
                placeName = s;
                toRemove = MapStations.FirstOrDefault(p => p.Name.Equals(s, StringComparison.OrdinalIgnoreCase));
            }

            if (string.IsNullOrEmpty(placeName)) return;

            var result = MessageBox.Show($"Bạn có chắc chắn muốn xóa điểm '{placeName}' khỏi bản đồ không?", 
                                         "Xác nhận xóa điểm", 
                                         MessageBoxButton.YesNo, 
                                         MessageBoxImage.Question);
            if (result != MessageBoxResult.Yes) return;

            // 1. Immediately remove from local MapStations
            if (toRemove != null)
            {
                MapStations.Remove(toRemove);
            }
            else
            {
                var match = MapStations.FirstOrDefault(p => p.Name.Equals(placeName, StringComparison.OrdinalIgnoreCase));
                if (match != null) MapStations.Remove(match);
            }

            SaveCurrentMapStationsToDisk();
            UpdateAvailableStationNames();

            AddLog($"🗑️ [XÓA ĐIỂM] Đã xóa điểm '{placeName}' khỏi bản đồ.");

            // 2. Send to robot
            if (_robotService.IsConnected)
            {
                await _robotService.SendCommandAsync(new DeletePlaceCommand
                {
                    MapName = CurrentMap,
                    Name = placeName
                });
            }
        }

        private readonly string _floorConfigFile = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "floors_config.json");

        public void LoadFloorConfigs()
        {
            try
            {
                string path = _floorConfigFile;
                if (!System.IO.File.Exists(path))
                {
                    string projectPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "floors_config.json");
                    if (System.IO.File.Exists(projectPath)) path = projectPath;
                }

                if (System.IO.File.Exists(path))
                {
                    string json = System.IO.File.ReadAllText(path);
                    var options = new System.Text.Json.JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    };
                    var list = System.Text.Json.JsonSerializer.Deserialize<List<FloorMapConfig>>(json, options);
                    if (list != null && list.Count > 0)
                    {
                        FloorConfigs.Clear();
                        foreach (var item in list)
                        {
                            // Fix floor number if missing or mismatching
                            var digits = new string(item.FloorLabel.Where(char.IsDigit).ToArray());
                            if (!string.IsNullOrEmpty(digits))
                            {
                                item.FloorNumber = digits;
                            }

                            if (string.IsNullOrEmpty(item.InsideCabinPoint))
                            {
                                item.InsideCabinPoint = !string.IsNullOrEmpty(item.ElevatorPoint) ? item.ElevatorPoint : $"TrongThangMay_T{item.FloorNumber}";
                            }
                            if (string.IsNullOrEmpty(item.OutsideWaitingPoint))
                            {
                                item.OutsideWaitingPoint = $"ChoThangMay_T{item.FloorNumber}";
                            }
                            FloorConfigs.Add(item);
                        }
                        return;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine("LoadFloorConfigs ex: " + ex.Message);
            }

            // Defaults if config file not found
            FloorConfigs.Clear();
            FloorConfigs.Add(new FloorMapConfig
            {
                FloorNumber = "1",
                FloorLabel = "Tầng 1",
                MapName = "MT1map-0721090621",
                InsideCabinPoint = "TrongThangMay_T1",
                OutsideWaitingPoint = "ChoThangMay_T1"
            });
            FloorConfigs.Add(new FloorMapConfig
            {
                FloorNumber = "2",
                FloorLabel = "Tầng 2",
                MapName = "2-0916091752",
                InsideCabinPoint = "TrongThangMay_T2",
                OutsideWaitingPoint = "ChoThangMay_T2"
            });
        }

        [RelayCommand]
        public void ToggleFloorConfig()
        {
            IsFloorConfigExpanded = !IsFloorConfigExpanded;
        }

        [RelayCommand]
        public void AddFloorConfig()
        {
            int next = FloorConfigs.Count + 1;
            var cfg = new FloorMapConfig
            {
                FloorNumber = next.ToString(),
                FloorLabel = $"Tầng {next}",
                MapName = AvailableMaps.FirstOrDefault() ?? "",
                InsideCabinPoint = $"TrongThangMay_T{next}",
                OutsideWaitingPoint = $"ChoThangMay_T{next}"
            };
            FloorConfigs.Add(cfg);
            SaveFloorConfigs();
            AddLog($"➕ [CÀI ĐẶT TẦNG] Đã thêm Tầng {next}. Bạn có thể chọn map từ danh sách.");
        }

        [RelayCommand]
        public void RemoveFloorConfig(FloorMapConfig? config)
        {
            if (config != null && FloorConfigs.Contains(config))
            {
                string label = config.FloorLabel;
                FloorConfigs.Remove(config);
                SaveFloorConfigs();
                AddLog($"🗑️ [CÀI ĐẶT TẦNG] Đã xóa cấu hình {label}.");
            }
        }

        [RelayCommand]
        public void SaveFloorConfigs()
        {
            try
            {
                var options = new System.Text.Json.JsonSerializerOptions
                {
                    WriteIndented = true,
                    PropertyNameCaseInsensitive = true
                };
                string json = System.Text.Json.JsonSerializer.Serialize(FloorConfigs, options);
                System.IO.File.WriteAllText(_floorConfigFile, json);
                
                string projectPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "floors_config.json");
                if (System.IO.Directory.Exists(System.IO.Path.GetDirectoryName(projectPath)))
                {
                    try { System.IO.File.WriteAllText(projectPath, json); } catch { }
                }

                string summary = string.Join(" | ", FloorConfigs.Select(f => $"{f.FloorLabel}: '{f.MapName}'"));
                AddLog($"💾 [CÀI ĐẶT TẦNG] Đã lưu thành công: {summary}");
                UpdateAvailableStationNames();
            }
            catch (Exception ex)
            {
                AddLog("❌ [LỖI] Không thể lưu cấu hình tầng: " + ex.Message);
            }
        }

        [RelayCommand]
        public async Task EnterElevatorCabinAsync(FloorMapConfig? config = null)
        {
            var target = config ?? FloorConfigs.FirstOrDefault(f => string.Equals(f.MapName, CurrentMap, StringComparison.OrdinalIgnoreCase));
            string insidePoint = target?.InsideCabinPoint ?? "";
            if (string.IsNullOrEmpty(insidePoint))
            {
                insidePoint = CurrentMap.Contains("2") ? "TrongThangMay_T2" : "TrongThangMay_T1";
            }

            var station = MapStations.FirstOrDefault(s => s.Name.Equals(insidePoint, StringComparison.OrdinalIgnoreCase));

            AddLog($"🛗 [VÀO THANG MÁY] Robot đang di chuyển vào trong buồng thang máy: '{insidePoint}'" + (station != null ? $" (X={station.X:F2}m, Y={station.Y:F2}m)" : "") + "...");
            await _robotService.SendCommandAsync(new NavigateCommand
            {
                Destination = insidePoint,
                X = station?.X,
                Y = station?.Y,
                MaxDistance = 1.2,
                TimeoutSeconds = 45
            });
        }

        [RelayCommand]
        public async Task ExitElevatorToHallAsync(FloorMapConfig? config = null)
        {
            var target = config ?? FloorConfigs.FirstOrDefault(f => string.Equals(f.MapName, CurrentMap, StringComparison.OrdinalIgnoreCase));
            string exitPoint = target?.OutsideWaitingPoint ?? "";
            if (string.IsNullOrEmpty(exitPoint))
            {
                exitPoint = CurrentMap.Contains("2") ? "ChoThangMay_T2" : "ChoThangMay_T1";
            }

            var station = MapStations.FirstOrDefault(s => s.Name.Equals(exitPoint, StringComparison.OrdinalIgnoreCase));

            AddLog($"🚪 [THOÁT THANG MÁY] Robot đang lăn bánh ra điểm chờ ngoài sảnh: '{exitPoint}'" + (station != null ? $" (X={station.X:F2}m, Y={station.Y:F2}m)" : "") + "...");
            await _robotService.SendCommandAsync(new NavigateCommand
            {
                Destination = exitPoint,
                X = station?.X,
                Y = station?.Y,
                MaxDistance = 1.5,
                TimeoutSeconds = 45
            });
        }

        [RelayCommand]
        public async Task DispatchElevatorToFloorAsync(object? param = null)
        {
            FloorMapConfig? targetConfig = null;

            if (param is FloorMapConfig cfg)
            {
                targetConfig = cfg;
            }
            else
            {
                string query = param as string ?? TargetFloorInput;
                if (string.IsNullOrWhiteSpace(query))
                {
                    AddLog("⚠️ Vui lòng nhập số tầng cần đến!");
                    return;
                }

                query = query.Trim();
                // Match floor number or label (e.g. "1" matches "1" or "Tầng 1")
                targetConfig = FloorConfigs.FirstOrDefault(f =>
                    string.Equals(f.FloorNumber, query, StringComparison.OrdinalIgnoreCase) ||
                    string.Equals(f.FloorLabel, query, StringComparison.OrdinalIgnoreCase) ||
                    f.FloorLabel.EndsWith(query, StringComparison.OrdinalIgnoreCase) ||
                    f.FloorNumber.Contains(query));
            }

            if (targetConfig == null)
            {
                AddLog($"⚠️ Chưa có cấu hình bản đồ cho Tầng '{param ?? TargetFloorInput}'. Đang sử dụng bản đồ đang chọn '{SelectedMap}'...");
                await SwitchFloorAndRelocalizeAsync(SelectedMap);
                return;
            }

            if (string.IsNullOrEmpty(targetConfig.MapName))
            {
                AddLog($"⚠️ {targetConfig.FloorLabel} chưa được chọn Bản đồ! Vui lòng bấm '⚙️ Cài đặt tầng' để gán map.");
                IsFloorConfigExpanded = true;
                return;
            }

            AddLog($"🏢 [ĐI THANG MÁY] Bắt đầu điều phối đến {targetConfig.FloorLabel} (Số tầng: {targetConfig.FloorNumber}) -> Bản đồ: '{targetConfig.MapName}', Điểm định vị buồng thang: '{targetConfig.InsideCabinPoint}'...");

            await _robotService.SendCommandAsync(new SwitchMapAndRelocalizeCommand
            {
                MapName = targetConfig.MapName,
                PlaceName = !string.IsNullOrEmpty(targetConfig.InsideCabinPoint) ? targetConfig.InsideCabinPoint : null,
                X = targetConfig.ElevatorX,
                Y = targetConfig.ElevatorY,
                Theta = targetConfig.ElevatorTheta
            });
        }

        [RelayCommand]
        public async Task SwitchFloorAndRelocalizeAsync(string? floorParam = null)
        {
            string targetMap = !string.IsNullOrEmpty(floorParam) ? floorParam : SelectedMap;
            if (string.IsNullOrEmpty(targetMap)) return;

            // Check if this map belongs to any configured floor to get its inside cabin elevator point
            var matchedConfig = FloorConfigs.FirstOrDefault(f => string.Equals(f.MapName, targetMap, StringComparison.OrdinalIgnoreCase));
            string elevatorPlace = matchedConfig?.InsideCabinPoint ?? "";

            if (string.IsNullOrEmpty(elevatorPlace))
            {
                string lower = targetMap.ToLower();
                if (lower.Contains("1") || lower.Contains("t1")) elevatorPlace = "TrongThangMay_T1";
                else if (lower.Contains("2") || lower.Contains("t2") || lower.Contains("111015")) elevatorPlace = "TrongThangMay_T2";
            }

            AddLog($"🏢 [ĐI THANG MÁY] Bắt đầu chuyển sang bản đồ '{targetMap}' và định vị tự động...");
            await _robotService.SendCommandAsync(new SwitchMapAndRelocalizeCommand
            {
                MapName = targetMap,
                PlaceName = !string.IsNullOrEmpty(elevatorPlace) ? elevatorPlace : null
            });
        }

        [RelayCommand]
        public async Task MoveHeadAsync(string direction)
        {
            int h = 0, v = 0;
            switch (direction.ToLowerInvariant())
            {
                case "up": v = -10; break;
                case "down": v = 10; break;
                case "left": h = -15; break;
                case "right": h = 15; break;
            }

            var cmd = new HeadCommand
            {
                HorizontalAngle = h,
                VerticalAngle = v
            };
            await _robotService.SendCommandAsync(cmd);
        }

        [RelayCommand]
        public async Task EmergencyStopAsync()
        {
            AddLog(">>> [KÍCH HOẠT DỪNG KHẨN CẤP (EMERGENCY STOP)] <<<");
            await _robotService.SendCommandAsync(new EmergencyStopCommand());
        }

        [RelayCommand]
        public async Task NavigateAsync(object? param = null)
        {
            if (IsRelocalizeMode)
            {
                await SetLocationAsync(param);
                IsRelocalizeMode = false;
                return;
            }

            string dest = "";
            double? x = null;
            double? y = null;

            if (param is MapPlaceItem place)
            {
                dest = place.Name;
                x = place.X;
                y = place.Y;
            }
            else if (param is string str && !string.IsNullOrWhiteSpace(str))
            {
                dest = str.Trim();
                if (dest.StartsWith("[") && dest.Contains("]"))
                {
                    int endBracket = dest.IndexOf(']');
                    dest = dest.Substring(endBracket + 1).Trim();
                }
                var found = MapStations.FirstOrDefault(s => s.Name.Equals(dest, StringComparison.OrdinalIgnoreCase));
                if (found != null)
                {
                    x = found.X;
                    y = found.Y;
                }
            }
            else if (!string.IsNullOrWhiteSpace(TargetDestination))
            {
                dest = TargetDestination.Trim();
                if (dest.StartsWith("[") && dest.Contains("]"))
                {
                    int endBracket = dest.IndexOf(']');
                    dest = dest.Substring(endBracket + 1).Trim();
                }
                var found = MapStations.FirstOrDefault(s => s.Name.Equals(dest, StringComparison.OrdinalIgnoreCase));
                if (found != null)
                {
                    x = found.X;
                    y = found.Y;
                }
            }

            if (string.IsNullOrWhiteSpace(dest)) return;

            // Nếu robot đang mất định vị, gợi ý người dùng định vị robot tại điểm này
            if (!IsEstimated && !IsSimulationMode && IsConnected)
            {
                var ask = System.Windows.MessageBox.Show(
                    $"Robot hiện đang MẤT ĐỊNH VỊ!\n\nBạn có muốn ĐẶT LẠI VỊ TRÍ của robot tại điểm '{dest}' không?\n\n- Bấm 'Yes': Đặt vị trí robot tại '{dest}'.\n- Bấm 'No': Tiếp tục gửi lệnh di chuyển.",
                    "Robot Chưa Định Vị",
                    System.Windows.MessageBoxButton.YesNoCancel,
                    System.Windows.MessageBoxImage.Question);

                if (ask == System.Windows.MessageBoxResult.Yes)
                {
                    await SetLocationAsync(param);
                    return;
                }
                else if (ask == System.Windows.MessageBoxResult.Cancel)
                {
                    return;
                }
            }

            TargetDestination = dest;
            AddLog($"[ĐIỀU HƯỚNG] Gửi lệnh di chuyển tới: '{dest}'" + (x.HasValue ? $" (X={x:F2}m, Y={y:F2}m)" : ""));

            if (IsCharging && !dest.ToLowerInvariant().Contains("sạc") && !dest.ToLowerInvariant().Contains("charge"))
            {
                AddLog($"⚡ [TRẠM SẠC] Robot đang ở trạm sạc. Gửi lệnh tách khỏi dock sạc để di chuyển tới '{dest}'...");
                await ChargeAsync("STOP_CHARGE");
                await Task.Delay(400);
                await ChargeAsync("LEAVE_PILE");
                await Task.Delay(1800);
            }

            var cmd = new NavigateCommand
            {
                Destination = dest,
                X = x,
                Y = y
            };
            await _robotService.SendCommandAsync(cmd);
        }

        public void NavigateToCanvasPoint(double canvasX, double canvasY)
        {
            if (MapDisplayWidth <= 0 || MapDisplayHeight <= 0) return;

            double normX = Math.Clamp(canvasX / MapDisplayWidth, 0.0, 1.0);
            double normY = Math.Clamp(canvasY / MapDisplayHeight, 0.0, 1.0);

            double spanX = Math.Max(0.1, MapMaxX - MapMinX);
            double spanY = Math.Max(0.1, MapMaxY - MapMinY);

            double worldX = Math.Round(MapMinX + normX * spanX, 2);
            double worldY = Math.Round(MapMaxY - normY * spanY, 2);

            // Check if clicking near an existing station (within 0.6m)
            var nearby = MapStations.FirstOrDefault(s => Math.Sqrt(Math.Pow(s.X - worldX, 2) + Math.Pow(s.Y - worldY, 2)) < 0.6);
            if (nearby != null)
            {
                _ = NavigateAsync(nearby);
            }
            else
            {
                string targetName = $"Điểm ({worldX:F1}m, {worldY:F1}m)";
                TargetDestination = targetName;
                AddLog($"[BẢN ĐỒ] Bấm chọn điểm: X={worldX:F2}m, Y={worldY:F2}m. Gửi lệnh di chuyển...");
                var cmd = new NavigateCommand
                {
                    Destination = targetName,
                    X = worldX,
                    Y = worldY,
                    Theta = 0.0
                };
                _ = _robotService.SendCommandAsync(cmd);
            }
        }

        public void RelocalizeToCanvasPoint(double canvasX, double canvasY)
        {
            if (MapDisplayWidth <= 0 || MapDisplayHeight <= 0) return;

            double normX = Math.Clamp(canvasX / MapDisplayWidth, 0.0, 1.0);
            double normY = Math.Clamp(canvasY / MapDisplayHeight, 0.0, 1.0);

            double spanX = Math.Max(0.1, MapMaxX - MapMinX);
            double spanY = Math.Max(0.1, MapMaxY - MapMinY);

            double worldX = Math.Round(MapMinX + normX * spanX, 2);
            double worldY = Math.Round(MapMaxY - normY * spanY, 2);

            var nearby = MapStations.FirstOrDefault(s => Math.Sqrt(Math.Pow(s.X - worldX, 2) + Math.Pow(s.Y - worldY, 2)) < 0.6);
            if (nearby != null)
            {
                AddLog($"📍 [ĐỊNH VỊ] Đã chọn trạm '{nearby.Name}' ({nearby.X:F2}m, {nearby.Y:F2}m) để định vị lại robot...");
                _ = SetLocationAsync(nearby);
            }
            else
            {
                ManualTargetX = worldX;
                ManualTargetY = worldY;
                double theta = PoseTheta * Math.PI / 180.0;
                AddLog($"📍 [ĐỊNH VỊ] Đặt lại vị trí robot tại tọa độ ({worldX:F2}m, {worldY:F2}m)...");
                _ = _robotService.SendCommandAsync(new SetPoseEstimateCommand { X = worldX, Y = worldY, Theta = theta });
            }

            IsRelocalizeMode = false;
        }

        [RelayCommand]
        public async Task StopNavigateAsync()
        {
            AddLog("[LỆNH DỪNG ĐIỀU HƯỚNG]");
            await _robotService.SendCommandAsync(new StopNavigateCommand());
        }

        [RelayCommand]
        public async Task DispatchTableOrderAsync(string tableName)
        {
            TargetDestination = tableName;
            AddLog($"[GIAO HÀNG] Bắt đầu hành trình tới: {tableName}");
            await SpeakAsync($"Bắt đầu hành trình giao món ăn đến {tableName}.");
            await NavigateAsync(tableName);
        }

        [RelayCommand]
        public async Task SpeakAsync(string? phrase = null)
        {
            var textToSpeak = !string.IsNullOrEmpty(phrase) ? phrase : SpeechText;
            if (string.IsNullOrWhiteSpace(textToSpeak)) return;

            AddLog($"🔊 [PHÁT GIỌNG NÓI] {textToSpeak}");
            var cmd = new SpeakCommand { Text = textToSpeak };
            await _robotService.SendCommandAsync(cmd);
        }

        [RelayCommand]
        public async Task ChargeAsync(string action)
        {
            var cmd = new ChargeCommand { Action = action.ToUpperInvariant() };
            await _robotService.SendCommandAsync(cmd);
        }

        [RelayCommand]
        public async Task ControlDoorAsync(string action)
        {
            var cmd = new DoorCommand { DoorId = 1, Action = action.ToUpperInvariant() };
            await _robotService.SendCommandAsync(cmd);
        }

        [RelayCommand]
        public void SwitchFleetRobot(FleetRobotItem robot)
        {
            if (robot != null && robot.Ip != RobotIp)
            {
                _ = Task.Run(async () =>
                {
                    if (IsConnected) await _robotService.DisconnectAsync();
                    RobotIp = robot.Ip;
                    RobotPort = robot.Port;
                    RobotId = robot.Id;
                    AddLog($"Chuyển đổi sang robot: {robot.Name} ({robot.Ip})");
                    await _robotService.ConnectAsync(robot.Ip, robot.Port);
                });
            }
        }

        [RelayCommand]
        public void ClearLogs()
        {
            LogHistory.Clear();
            AddLog("Đã xóa nhật ký sự kiện.");
        }

        [RelayCommand]
        public void ZoomIn()
        {
            ApplyZoom(0.25);
        }

        [RelayCommand]
        public void ZoomOut()
        {
            ApplyZoom(-0.25);
        }

        [RelayCommand]
        public void ResetZoom()
        {
            MapZoom = 1.0;
            MapPanX = 0.0;
            MapPanY = 0.0;
            MapZoomText = "100%";
        }

        public void ApplyZoom(double delta)
        {
            double newZoom = Math.Clamp(MapZoom + delta, 0.4, 4.0);
            MapZoom = Math.Round(newZoom, 2);
            MapZoomText = $"{(int)(MapZoom * 100)}%";
        }

        [RelayCommand]
        public void ShowDashboard()
        {
            HideAllViews();
            SelectedNavMenu = "Dashboard";
            DashboardVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            DashboardNavBg = "#162034";
            DashboardNavFg = "#00E5FF";
        }

        [RelayCommand]
        public void ShowMaps()
        {
            HideAllViews();
            SelectedNavMenu = "Maps";
            MapsVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            MapsNavBg = "#162034";
            MapsNavFg = "#00E5FF";
            _ = RefreshMapAsync();
        }

        [RelayCommand]
        public void ShowTasks()
        {
            HideAllViews();
            SelectedNavMenu = "Tasks";
            TasksVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            TasksNavBg = "#162034";
            TasksNavFg = "#00E5FF";
            UpdateAvailableStationNames();
        }

        public void UpdateAvailableStationNames()
        {
            AvailableStationNames.Clear();
            var allSaved = LoadAllSavedStations();
            var uniqueItems = new List<string>();

            // Case A: User has configured elevator floors in FloorConfigs
            if (FloorConfigs.Count > 0)
            {
                // ONLY show stations that belong to explicitly configured floors!
                foreach (var fc in FloorConfigs)
                {
                    if (string.IsNullOrWhiteSpace(fc.MapName)) continue;
                    string targetMap = fc.MapName.Trim();
                    string floorLabel = !string.IsNullOrWhiteSpace(fc.FloorLabel) ? fc.FloorLabel.Trim() : "Tầng";

                    var matchedStations = FindStationsForMap(allSaved, targetMap);
                    if (matchedStations != null)
                    {
                        foreach (var station in matchedStations)
                        {
                            if (string.IsNullOrWhiteSpace(station.Name)) continue;
                            string formatted = $"[{floorLabel}] {station.Name}";
                            if (!uniqueItems.Contains(formatted))
                            {
                                uniqueItems.Add(formatted);
                            }
                        }
                    }

                    // If this configured floor matches the currently active map, also include any active MapStations
                    if (string.Equals(CurrentMap, targetMap, StringComparison.OrdinalIgnoreCase) ||
                        NormalizeMapKey(CurrentMap) == NormalizeMapKey(targetMap))
                    {
                        foreach (var st in MapStations)
                        {
                            if (string.IsNullOrWhiteSpace(st.Name)) continue;
                            string formatted = $"[{floorLabel}] {st.Name}";
                            if (!uniqueItems.Contains(formatted))
                            {
                                uniqueItems.Add(formatted);
                            }
                        }
                    }
                }
            }
            else
            {
                // Case B: No elevator floors configured. Use current active map stations without floor tags
                foreach (var st in MapStations)
                {
                    if (string.IsNullOrWhiteSpace(st.Name)) continue;
                    if (!uniqueItems.Contains(st.Name))
                    {
                        uniqueItems.Add(st.Name);
                    }
                }

                if (!string.IsNullOrEmpty(CurrentMap))
                {
                    var matchedStations = FindStationsForMap(allSaved, CurrentMap);
                    if (matchedStations != null)
                    {
                        foreach (var s in matchedStations)
                        {
                            if (!string.IsNullOrWhiteSpace(s.Name) && !uniqueItems.Contains(s.Name))
                            {
                                uniqueItems.Add(s.Name);
                            }
                        }
                    }
                }
            }

            foreach (var item in uniqueItems)
            {
                AvailableStationNames.Add(item);
            }
        }

        [RelayCommand]
        public void ResetTaskForm()
        {
            UpdateAvailableStationNames();
            IsEditingExistingTask = false;
            TaskEditorHeader = "➕ TẠO NHIỆM VỤ GIAO 3 KHAY";
            FormTaskId = $"TASK-{DateTime.Now:HHmmss}";
            FormTaskName = $"Giao bàn {DateTime.Now:HH:mm}";
            FormReturnStation = AvailableStationNames.FirstOrDefault(s => s.Contains("Stand-by") || s.Contains("Sạc") || s.Contains("Charge")) ?? "Stand-by Point";
            FormWaitTimeSeconds = 30;
            FormGreetingSpeech = "Kính chào quý khách! Món ăn đã sẵn sàng trên khay của robot, chúc quý khách ngon miệng!";

            FormTray1HasDish = true;
            FormTray1Destination = AvailableStationNames.FirstOrDefault(s => s.Contains("1") || s.Contains("B1")) ?? AvailableStationNames.ElementAtOrDefault(0) ?? "Bàn 1";
            FormTray1DishName = "Cơm sườn nướng đặc biệt";
            FormTray1Note = "Ít ớt";

            FormTray2HasDish = true;
            FormTray2Destination = AvailableStationNames.FirstOrDefault(s => s.Contains("2") || s.Contains("B2")) ?? AvailableStationNames.ElementAtOrDefault(1) ?? "Bàn 2";
            FormTray2DishName = "Cà phê sữa đá & Nước cam";
            FormTray2Note = "Ít đường";

            FormTray3HasDish = false;
            FormTray3Destination = AvailableStationNames.FirstOrDefault(s => s.Contains("4") || s.Contains("B3")) ?? AvailableStationNames.ElementAtOrDefault(2) ?? "Bàn 3";
            FormTray3DishName = "Set Trà Đào Cam Sả";
            FormTray3Note = "";
        }

        [RelayCommand]
        public void EditTask(FoodDeliveryTask? task)
        {
            if (task == null) return;
            IsEditingExistingTask = true;
            TaskEditorHeader = $"✏️ SỬA NHIỆM VỤ: {task.Id}";
            FormTaskId = task.Id;
            FormTaskName = task.TaskName;
            FormReturnStation = task.ReturnStation;
            FormWaitTimeSeconds = task.WaitTimeSeconds;
            FormGreetingSpeech = task.GreetingSpeech;

            FormTray1HasDish = task.Tray1.HasDish;
            FormTray1Destination = task.Tray1.Destination;
            FormTray1DishName = task.Tray1.DishName;
            FormTray1Note = task.Tray1.Note;

            FormTray2HasDish = task.Tray2.HasDish;
            FormTray2Destination = task.Tray2.Destination;
            FormTray2DishName = task.Tray2.DishName;
            FormTray2Note = task.Tray2.Note;

            FormTray3HasDish = task.Tray3.HasDish;
            FormTray3Destination = task.Tray3.Destination;
            FormTray3DishName = task.Tray3.DishName;
            FormTray3Note = task.Tray3.Note;

            AddLog($"✏️ [NHIỆM VỤ] Đang chỉnh sửa thông tin nhiệm vụ '{task.Id}' - {task.TaskName}.");
        }

        [RelayCommand]
        public void CancelEditTask()
        {
            ResetTaskForm();
            AddLog("ℹ️ Đã hủy chế độ sửa nhiệm vụ.");
        }

        [RelayCommand]
        public void SaveTask()
        {
            if (!FormTray1HasDish && !FormTray2HasDish && !FormTray3HasDish)
            {
                MessageBox.Show("Vui lòng kích hoạt ít nhất 1 khay có món ăn và chọn bàn giao!", "Chưa chọn khay", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            if (IsEditingExistingTask)
            {
                var existing = DeliveryTasks.FirstOrDefault(t => t.Id == FormTaskId);
                if (existing != null)
                {
                    existing.TaskName = FormTaskName;
                    existing.ReturnStation = FormReturnStation;
                    existing.WaitTimeSeconds = FormWaitTimeSeconds;
                    existing.GreetingSpeech = FormGreetingSpeech;

                    existing.Tray1.HasDish = FormTray1HasDish;
                    existing.Tray1.Destination = FormTray1Destination;
                    existing.Tray1.DishName = FormTray1DishName;
                    existing.Tray1.Note = FormTray1Note;

                    existing.Tray2.HasDish = FormTray2HasDish;
                    existing.Tray2.Destination = FormTray2Destination;
                    existing.Tray2.DishName = FormTray2DishName;
                    existing.Tray2.Note = FormTray2Note;

                    existing.Tray3.HasDish = FormTray3HasDish;
                    existing.Tray3.Destination = FormTray3Destination;
                    existing.Tray3.DishName = FormTray3DishName;
                    existing.Tray3.Note = FormTray3Note;

                    AddLog($"💾 [NHIỆM VỤ] Đã cập nhật thành công nhiệm vụ '{existing.Id}' - {existing.TaskName}.");
                }
            }
            else
            {
                var newTask = new FoodDeliveryTask
                {
                    Id = string.IsNullOrWhiteSpace(FormTaskId) ? $"TASK-{DateTime.Now:HHmmss}" : FormTaskId,
                    TaskName = string.IsNullOrWhiteSpace(FormTaskName) ? "Giao món ăn" : FormTaskName,
                    ReturnStation = FormReturnStation,
                    WaitTimeSeconds = FormWaitTimeSeconds,
                    GreetingSpeech = FormGreetingSpeech,
                    Status = "Chờ thực hiện",
                    StatusColor = "#F59E0B",
                    CreatedAt = DateTime.Now
                };

                newTask.Tray1.HasDish = FormTray1HasDish;
                newTask.Tray1.Destination = FormTray1Destination;
                newTask.Tray1.DishName = FormTray1DishName;
                newTask.Tray1.Note = FormTray1Note;

                newTask.Tray2.HasDish = FormTray2HasDish;
                newTask.Tray2.Destination = FormTray2Destination;
                newTask.Tray2.DishName = FormTray2DishName;
                newTask.Tray2.Note = FormTray2Note;

                newTask.Tray3.HasDish = FormTray3HasDish;
                newTask.Tray3.Destination = FormTray3Destination;
                newTask.Tray3.DishName = FormTray3DishName;
                newTask.Tray3.Note = FormTray3Note;

                DeliveryTasks.Insert(0, newTask);
                AddLog($"➕ [NHIỆM VỤ] Đã tạo nhiệm vụ mới '{newTask.Id}' - {newTask.TaskName} ({newTask.ActiveTrayCount} khay).");
            }

            SaveDeliveryTasksToDisk();
            ResetTaskForm();
        }

        [RelayCommand]
        public void DeleteTask(FoodDeliveryTask? task)
        {
            if (task == null) return;
            var confirm = MessageBox.Show($"Bạn có chắc chắn muốn xóa nhiệm vụ '{task.Id}' ({task.TaskName}) không?",
                                          "Xác nhận xóa nhiệm vụ",
                                          MessageBoxButton.YesNo,
                                          MessageBoxImage.Question);
            if (confirm != MessageBoxResult.Yes) return;

            DeliveryTasks.Remove(task);
            SaveDeliveryTasksToDisk();
            if (FormTaskId == task.Id)
            {
                ResetTaskForm();
            }
            AddLog($"🗑️ [NHIỆM VỤ] Đã xóa nhiệm vụ '{task.Id}'.");
        }

        [RelayCommand]
        public async Task DispatchDeliveryTaskAsync(FoodDeliveryTask? task)
        {
            if (task == null) return;
            if (IsDeliveryRunning)
            {
                MessageBox.Show("Robot đang trong một tiến trình giao hàng khác. Vui lòng hoàn thành hoặc hủy bỏ trước khi bắt đầu nhiệm vụ mới.", "Robot đang bận", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            IsDeliveryRunning = true;
            ActiveRunningTask = task;
            task.Status = "Đang giao hàng";
            task.StatusColor = "#00E5FF";

            _deliveryCts = new CancellationTokenSource();
            var token = _deliveryCts.Token;

            AddLog($"🚀 [GIAO HÀNG 3 KHAY] Bắt đầu nhiệm vụ '{task.Id}': {task.TrayRouteOverview}");
            await SpeakAsync($"Bắt đầu hành trình giao thức ăn nhiệm vụ {task.TaskName}.");

            _ = Task.Run(async () =>
            {
                try
                {
                    var traysToDeliver = new List<TrayDeliveryConfig>();
                    if (task.Tray1.HasDish && !string.IsNullOrWhiteSpace(task.Tray1.Destination)) traysToDeliver.Add(task.Tray1);
                    if (task.Tray2.HasDish && !string.IsNullOrWhiteSpace(task.Tray2.Destination)) traysToDeliver.Add(task.Tray2);
                    if (task.Tray3.HasDish && !string.IsNullOrWhiteSpace(task.Tray3.Destination)) traysToDeliver.Add(task.Tray3);

                    for (int i = 0; i < traysToDeliver.Count; i++)
                    {
                        if (token.IsCancellationRequested) break;
                        var tray = traysToDeliver[i];

                        string rawDest = (tray.Destination ?? "").Trim();
                        string cleanDest = rawDest;
                        string? targetMap = null;

                        // Check if formatted with floor tag e.g. "[Tầng 1] B1" or "[Tầng 2] 4"
                        if (rawDest.StartsWith("[") && rawDest.Contains("]"))
                        {
                            int endBracket = rawDest.IndexOf(']');
                            string floorTag = rawDest.Substring(1, endBracket - 1).Trim();
                            cleanDest = rawDest.Substring(endBracket + 1).Trim();

                            var floorMatch = FloorConfigs.FirstOrDefault(f => 
                                f.FloorLabel.Contains(floorTag, StringComparison.OrdinalIgnoreCase) ||
                                floorTag.Contains(f.FloorNumber, StringComparison.OrdinalIgnoreCase));
                            if (floorMatch != null)
                            {
                                targetMap = floorMatch.MapName;
                            }
                        }
                        else
                        {
                            // Search which map contains this station in saved_stations
                            var allSaved = LoadAllSavedStations();
                            foreach (var kvp in allSaved)
                            {
                                if (kvp.Value.Any(s => s.Name.Equals(cleanDest, StringComparison.OrdinalIgnoreCase)))
                                {
                                    targetMap = kvp.Key;
                                    break;
                                }
                            }
                        }

                        // If the destination belongs to another floor, switch floor automatically via elevator!
                        if (!string.IsNullOrEmpty(targetMap) && !string.Equals(targetMap, CurrentMap, StringComparison.OrdinalIgnoreCase))
                        {
                            await Application.Current.Dispatcher.InvokeAsync(async () =>
                            {
                                AddLog($"🏢 [CHUYỂN TẦNG THANG MÁY] Khay {tray.TrayNumber} giao đến '{cleanDest}' thuộc '{targetMap}'. Robot đang đi thang máy chuyển tầng...");
                                await SwitchFloorAndRelocalizeAsync(targetMap);
                            });
                            for (int el = 0; el < 4; el++)
                            {
                                if (token.IsCancellationRequested) break;
                                await Task.Delay(1000, token);
                            }
                        }

                        DeliveryCurrentTrayNumber = tray.TrayNumber;
                        DeliveryCurrentDestination = cleanDest;
                        DeliveryProgressStatus = $"Đang tới {cleanDest} ({tray.TrayLabel})...";

                        tray.Status = "Đang tới bàn";
                        tray.StatusColor = "#00E5FF";

                        await Application.Current.Dispatcher.InvokeAsync(async () =>
                        {
                            AddLog($"🍱 [KHAY {tray.TrayNumber}] Di chuyển tới bàn '{cleanDest}' để giao món '{tray.DishName}'...");
                            await SpeakAsync($"Robot đang giao món đến {cleanDest}.");
                            await NavigateAsync(cleanDest);
                        });

                        // Travel wait period or arrival check
                        for (int w = 0; w < 10; w++)
                        {
                            if (token.IsCancellationRequested) break;
                            await Task.Delay(1000, token);
                        }

                        if (token.IsCancellationRequested) break;

                        // Arrived at table
                        tray.Status = "Chờ lấy món";
                        tray.StatusColor = "#10B981";
                        DeliveryProgressStatus = $"Đã tới {cleanDest}. Mời quý khách nhận món tại {tray.TrayLabel}!";

                        await Application.Current.Dispatcher.InvokeAsync(async () =>
                        {
                            string speech = string.Format(task.GreetingSpeech, tray.TrayNumber);
                            if (!string.IsNullOrEmpty(tray.DishName)) speech += $" Món: {tray.DishName}.";
                            AddLog($"📢 [ĐÃ TỚI BÀN] {cleanDest} - {speech}");
                            await SpeakAsync(speech);
                        });

                        // Wait for customer to pick up the dish
                        int waitSec = Math.Max(10, task.WaitTimeSeconds);
                        for (int s = 0; s < waitSec; s++)
                        {
                            if (token.IsCancellationRequested) break;
                            DeliveryProgressStatus = $"Chờ khách lấy món {tray.TrayLabel} ({waitSec - s}s)...";
                            await Task.Delay(1000, token);
                        }

                        tray.Status = "Đã lấy món";
                        tray.StatusColor = "#38BDF8";
                        await Application.Current.Dispatcher.InvokeAsync(async () =>
                        {
                            AddLog($"✅ [HOÀN TẤT KHAY {tray.TrayNumber}] Khách đã nhận món tại {cleanDest}.");
                            await SpeakAsync("Cảm ơn quý khách! Chúc quý khách ngon miệng.");
                        });

                        await Task.Delay(2000, token);
                    }

                    if (!token.IsCancellationRequested)
                    {
                        // All trays finished -> Return to Kitchen / Station
                        string rawReturn = (task.ReturnStation ?? "Stand-by Point").Trim();
                        string cleanReturn = rawReturn;
                        string? returnMap = null;
                        if (rawReturn.StartsWith("[") && rawReturn.Contains("]"))
                        {
                            int endBracket = rawReturn.IndexOf(']');
                            string floorTag = rawReturn.Substring(1, endBracket - 1).Trim();
                            cleanReturn = rawReturn.Substring(endBracket + 1).Trim();
                            var floorMatch = FloorConfigs.FirstOrDefault(f => 
                                f.FloorLabel.Contains(floorTag, StringComparison.OrdinalIgnoreCase) ||
                                floorTag.Contains(f.FloorNumber, StringComparison.OrdinalIgnoreCase));
                            if (floorMatch != null) returnMap = floorMatch.MapName;
                        }

                        if (!string.IsNullOrEmpty(returnMap) && !string.Equals(returnMap, CurrentMap, StringComparison.OrdinalIgnoreCase))
                        {
                            await Application.Current.Dispatcher.InvokeAsync(async () =>
                            {
                                AddLog($"🏢 [QUAY VỀ] Đi thang máy chuyển sang '{returnMap}' để về trạm '{cleanReturn}'...");
                                await SwitchFloorAndRelocalizeAsync(returnMap);
                            });
                            await Task.Delay(3000, token);
                        }

                        DeliveryCurrentTrayNumber = 4;
                        DeliveryCurrentDestination = cleanReturn;
                        DeliveryProgressStatus = $"Đang quay về trạm '{cleanReturn}'...";

                        await Application.Current.Dispatcher.InvokeAsync(async () =>
                        {
                            AddLog($"🏠 [KẾT THÚC NHIỆM VỤ] Đã giao xong tất cả các khay. Robot đang quay về '{cleanReturn}'...");
                            await SpeakAsync("Đã hoàn thành giao hàng. Robot đang quay về trạm.");
                            await NavigateAsync(cleanReturn);
                        });

                        await Task.Delay(5000, token);

                        await Application.Current.Dispatcher.InvokeAsync(() =>
                        {
                            task.Status = "Hoàn tất";
                            task.StatusColor = "#10B981";
                            SaveDeliveryTasksToDisk();
                            AddLog($"🏁 [NHIỆM VỤ HOÀN TẤT] {task.Id} đã hoàn thành xuất sắc!");
                        });
                    }
                }
                catch (TaskCanceledException)
                {
                    await Application.Current.Dispatcher.InvokeAsync(() =>
                    {
                        AddLog($"⏹ [HỦY NHIỆM VỤ] Đã dừng nhiệm vụ giao hàng '{task.Id}'.");
                        task.Status = "Đã hủy";
                        task.StatusColor = "#EF4444";
                    });
                }
                finally
                {
                    await Application.Current.Dispatcher.InvokeAsync(() =>
                    {
                        IsDeliveryRunning = false;
                        ActiveRunningTask = null;
                        DeliveryProgressStatus = "Sẵn sàng";
                        DeliveryCurrentTrayNumber = 0;
                    });
                }
            }, token);
        }

        [RelayCommand]
        public async Task AbortDeliveryAsync()
        {
            if (_deliveryCts != null && !_deliveryCts.IsCancellationRequested)
            {
                _deliveryCts.Cancel();
            }
            await StopNavigateAsync();
            AddLog("⏹ [DỪNG KHẨN CẤP] Đã phát lệnh hủy tiến trình giao hàng.");
        }

        private readonly string _tasksConfigFile = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "delivery_tasks.json");

        public void LoadDeliveryTasksFromDisk()
        {
            try
            {
                string path = _tasksConfigFile;
                if (!System.IO.File.Exists(path))
                {
                    string projectPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "delivery_tasks.json");
                    if (System.IO.File.Exists(projectPath)) path = projectPath;
                }

                if (System.IO.File.Exists(path))
                {
                    string json = System.IO.File.ReadAllText(path);
                    var list = System.Text.Json.JsonSerializer.Deserialize<List<FoodDeliveryTask>>(json,
                        new System.Text.Json.JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                    if (list != null && list.Count > 0)
                    {
                        DeliveryTasks.Clear();
                        foreach (var t in list) DeliveryTasks.Add(t);
                        return;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine("LoadDeliveryTasksFromDisk error: " + ex.Message);
            }

            // Defaults if file not found
            DeliveryTasks.Clear();
            var task1 = new FoodDeliveryTask
            {
                Id = "TASK-001",
                TaskName = "Giao Combo Bữa Trưa 2 Bàn",
                ReturnStation = "Stand-by Point",
                WaitTimeSeconds = 30,
                GreetingSpeech = "Kính chào quý khách! Món ăn đã sẵn sàng trên khay của robot, chúc quý khách ngon miệng!",
                Status = "Chờ thực hiện",
                StatusColor = "#F59E0B"
            };
            task1.Tray1.HasDish = true;
            task1.Tray1.Destination = "phòng họp";
            task1.Tray1.DishName = "Cơm sườn nướng mật ong x2";
            task1.Tray1.Note = "Thêm canh";

            task1.Tray2.HasDish = true;
            task1.Tray2.Destination = "cafe";
            task1.Tray2.DishName = "Cà phê sữa đá & Nước cam";
            task1.Tray2.Note = "Ít đường";

            task1.Tray3.HasDish = false;

            var task2 = new FoodDeliveryTask
            {
                Id = "TASK-002",
                TaskName = "Giao Đồ Uống & Tráng Miệng",
                ReturnStation = "Charging Point",
                WaitTimeSeconds = 25,
                GreetingSpeech = "Kính chào quý khách! Đồ uống của quý khách đã tới, chúc quý khách thưởng thức ngon miệng!",
                Status = "Chờ thực hiện",
                StatusColor = "#F59E0B"
            };
            task2.Tray1.HasDish = true;
            task2.Tray1.Destination = "tivi";
            task2.Tray1.DishName = "Set Trà Đào Cam Sả x3";

            task2.Tray2.HasDish = false;

            task2.Tray3.HasDish = true;
            task2.Tray3.Destination = "phòng họp";
            task2.Tray3.DishName = "Đĩa hoa quả tươi thập cẩm";

            DeliveryTasks.Add(task1);
            DeliveryTasks.Add(task2);
            SaveDeliveryTasksToDisk();
        }

        public void SaveDeliveryTasksToDisk()
        {
            try
            {
                var options = new System.Text.Json.JsonSerializerOptions { WriteIndented = true, PropertyNameCaseInsensitive = true };
                string json = System.Text.Json.JsonSerializer.Serialize(DeliveryTasks, options);
                System.IO.File.WriteAllText(_tasksConfigFile, json);

                string projectPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "delivery_tasks.json");
                if (System.IO.Directory.Exists(System.IO.Path.GetDirectoryName(projectPath)))
                {
                    try { System.IO.File.WriteAllText(projectPath, json); } catch { }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine("SaveDeliveryTasksToDisk error: " + ex.Message);
            }
        }

        [RelayCommand]
        public void CenterOnRobot()
        {
            MapPanX = (MapDisplayWidth / 2.0) - CanvasRobotLeft;
            MapPanY = (MapDisplayHeight / 2.0) - CanvasRobotTop;
            AddLog("Đã căn giữa bản đồ theo vị trí robot.");
        }

        [RelayCommand]
        public async Task NavigateToManualCoordAsync()
        {
            string dest = $"Tọa độ ({ManualTargetX:F2}m, {ManualTargetY:F2}m)";
            TargetDestination = dest;
            AddLog($"[ĐIỀU HƯỚNG] Đi tới tọa độ chỉ định: X={ManualTargetX:F2}m, Y={ManualTargetY:F2}m");
            var cmd = new NavigateCommand
            {
                Destination = dest,
                X = ManualTargetX,
                Y = ManualTargetY,
                Theta = 0.0
            };
            await _robotService.SendCommandAsync(cmd);
        }

        [RelayCommand]
        public void ToggleMapTheme()
        {
            IsWhiteMapTheme = !IsWhiteMapTheme;
            MapViewportBackground = "#080C14";
            MapThemeButtonText = IsWhiteMapTheme ? "⚡ Nền Cyber" : "☀️ Sàn Trắng";
            StationTextForeground = IsWhiteMapTheme ? "#0369A1" : "#00E5FF";
            StationTextHaloColor = IsWhiteMapTheme ? "#FFFFFF" : "#000000";
            if (_rawMapSource != null)
            {
                CurrentMapImage = IsWhiteMapTheme ? ConvertToCleanWhiteMap(_rawMapSource) : ConvertToCyberMap(_rawMapSource);
            }
            AddLog($"Đã chuyển hiển thị bản đồ sang: {(IsWhiteMapTheme ? "Sàn Trắng (Architectural White)" : "Nền Cyber Tech")}");
        }

        public static BitmapSource ConvertToCyberMap(BitmapSource source)
        {
            try
            {
                var formatted = new FormatConvertedBitmap(source, PixelFormats.Bgra32, null, 0);
                int width = formatted.PixelWidth;
                int height = formatted.PixelHeight;
                int stride = width * 4;
                byte[] src = new byte[height * stride];
                formatted.CopyPixels(src, stride, 0);

                byte[] classification = new byte[width * height];
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        int idx = (y * width + x) * 4;
                        byte b = src[idx];
                        byte g = src[idx + 1];
                        byte r = src[idx + 2];

                        // 1. Outside / Unexplored / Background
                        if ((r >= 5 && r <= 14 && g >= 9 && g <= 17 && b >= 16 && b <= 26) // #080C14
                            || (r >= 130 && r <= 170 && Math.Abs(r - g) < 15 && Math.Abs(g - b) < 15) // 0x96 grey
                            || (Math.Abs(r - 24) < 5 && Math.Abs(g - 42) < 5 && Math.Abs(b - 82) < 5)) // UNDETECT 0xFF182A52
                        {
                            classification[y * width + x] = 0; // Outside
                        }
                        // 2. Floor / Walkable corridor
                        else if ((r > 200 && g > 200 && b > 200) // Pure White
                            || (b > 160 && g > 90 && g < 150 && r > 50 && r < 100) // PASS 0xFF4E75C0
                            || (Math.Abs(r - 16) < 6 && Math.Abs(g - 30) < 6 && Math.Abs(b - 51) < 6)) // Cyber Navy
                        {
                            classification[y * width + x] = 1; // Floor
                        }
                        // 3. Wall / Obstacle (0x00, 0x05, BLOCK 0xFF1D3C7F, CAD #1E293B, Cyber #00E5FF)
                        else
                        {
                            classification[y * width + x] = 2; // Wall
                        }
                    }
                }

                bool[] continuousWall = new bool[width * height];
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        if (classification[y * width + x] == 2)
                        {
                            continuousWall[y * width + x] = true;
                            for (int dy = -1; dy <= 1; dy++)
                            {
                                for (int dx = -1; dx <= 1; dx++)
                                {
                                    int ny = y + dy;
                                    int nx = x + dx;
                                    if (nx >= 0 && nx < width && ny >= 0 && ny < height)
                                    {
                                        if (classification[ny * width + nx] != 1)
                                        {
                                            continuousWall[ny * width + nx] = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                byte[] dst = new byte[height * stride];
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        int dstIdx = (y * width + x) * 4;
                        int gridIdx = y * width + x;

                        if (continuousWall[gridIdx])
                        {
                            // Glowing Cyan Wall (#00E5FF)
                            dst[dstIdx] = 255;    // B
                            dst[dstIdx + 1] = 229;// G
                            dst[dstIdx + 2] = 0;  // R
                            dst[dstIdx + 3] = 255;// Alpha
                        }
                        else if (classification[gridIdx] == 1)
                        {
                            // High-Tech Navy Floor (#101E33)
                            dst[dstIdx] = 51;     // B
                            dst[dstIdx + 1] = 30; // G
                            dst[dstIdx + 2] = 16; // R
                            dst[dstIdx + 3] = 255;// Alpha
                        }
                        else
                        {
                            // Outside Unexplored: Deep Dark Slate (#080C14)
                            dst[dstIdx] = 20;     // B
                            dst[dstIdx + 1] = 12; // G
                            dst[dstIdx + 2] = 8;  // R
                            dst[dstIdx + 3] = 255;// Alpha
                        }
                    }
                }

                var writeableBmp = new WriteableBitmap(width, height, 96, 96, PixelFormats.Bgra32, null);
                writeableBmp.WritePixels(new Int32Rect(0, 0, width, height), dst, stride, 0);
                writeableBmp.Freeze();
                return writeableBmp;
            }
            catch
            {
                return source;
            }
        }

        public static BitmapSource ConvertToCleanWhiteMap(BitmapSource source)
        {
            try
            {
                var formatted = new FormatConvertedBitmap(source, PixelFormats.Bgra32, null, 0);
                int width = formatted.PixelWidth;
                int height = formatted.PixelHeight;
                int stride = width * 4;
                byte[] src = new byte[height * stride];
                formatted.CopyPixels(src, stride, 0);

                // Classify pixels: 0 = Outside, 1 = Floor, 2 = Wall
                byte[] classification = new byte[width * height];
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        int idx = (y * width + x) * 4;
                        byte b = src[idx];
                        byte g = src[idx + 1];
                        byte r = src[idx + 2];

                        // 1. Outside / Unexplored / Background
                        if ((r >= 5 && r <= 14 && g >= 9 && g <= 17 && b >= 16 && b <= 26) // #080C14
                            || (r >= 130 && r <= 170 && Math.Abs(r - g) < 15 && Math.Abs(g - b) < 15) // 0x96 grey
                            || (Math.Abs(r - 24) < 5 && Math.Abs(g - 42) < 5 && Math.Abs(b - 82) < 5)) // UNDETECT 0xFF182A52
                        {
                            classification[y * width + x] = 0; // Outside
                        }
                        // 2. Floor / Walkable corridor
                        else if ((r > 200 && g > 200 && b > 200) // Pure White
                            || (b > 160 && g > 90 && g < 150 && r > 50 && r < 100) // PASS 0xFF4E75C0
                            || (Math.Abs(r - 16) < 6 && Math.Abs(g - 30) < 6 && Math.Abs(b - 51) < 6)) // Cyber Navy
                        {
                            classification[y * width + x] = 1; // Floor
                        }
                        // 3. Wall / Obstacle (0x00, 0x05, BLOCK 0xFF1D3C7F, CAD #1E293B, Cyber #00E5FF)
                        else
                        {
                            classification[y * width + x] = 2; // Wall
                        }
                    }
                }

                // Connect adjacent wall pixels to make lines continuous and crisp
                bool[] continuousWall = new bool[width * height];
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        if (classification[y * width + x] == 2)
                        {
                            continuousWall[y * width + x] = true;
                            for (int dy = -1; dy <= 1; dy++)
                            {
                                for (int dx = -1; dx <= 1; dx++)
                                {
                                    int ny = y + dy;
                                    int nx = x + dx;
                                    if (nx >= 0 && nx < width && ny >= 0 && ny < height)
                                    {
                                        if (classification[ny * width + nx] != 1)
                                        {
                                            continuousWall[ny * width + nx] = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                byte[] dst = new byte[height * stride];
                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        int dstIdx = (y * width + x) * 4;
                        int gridIdx = y * width + x;

                        if (continuousWall[gridIdx])
                        {
                            // Solid continuous dark slate wall line (#1E293B)
                            dst[dstIdx] = 59;     // B
                            dst[dstIdx + 1] = 41; // G
                            dst[dstIdx + 2] = 30; // R
                            dst[dstIdx + 3] = 255;// Alpha
                        }
                        else if (classification[gridIdx] == 1)
                        {
                            // Walkable Room / Corridor: Clean Pure White (#FFFFFF)
                            dst[dstIdx] = 255;
                            dst[dstIdx + 1] = 255;
                            dst[dstIdx + 2] = 255;
                            dst[dstIdx + 3] = 255;
                        }
                        else
                        {
                            // Outside Unexplored Area: Deep Dark Background (#080C14)
                            // Perfectly blends into the dark viewport so there is ZERO milky mirror haze!
                            dst[dstIdx] = 20;     // B
                            dst[dstIdx + 1] = 12; // G
                            dst[dstIdx + 2] = 8;  // R
                            dst[dstIdx + 3] = 255;
                        }
                    }
                }

                var writeableBmp = new WriteableBitmap(width, height, 96, 96, PixelFormats.Bgra32, null);
                writeableBmp.WritePixels(new Int32Rect(0, 0, width, height), dst, stride, 0);
                writeableBmp.Freeze();
                return writeableBmp;
            }
            catch
            {
                return source;
            }
        }

        public void UpdateMapBounds(IList<MapPlaceItem>? places, string? mapName = null, double? res = null, double? originX = null, double? originY = null, int? imgW = null, int? imgH = null)
        {
            if (res.HasValue && res.Value > 0.001 && originX.HasValue && originY.HasValue && imgW.HasValue && imgH.HasValue && imgW.Value > 0 && imgH.Value > 0)
            {
                double resolution = res.Value;
                int w = imgW.Value;
                int h = imgH.Value;
                MapMinX = originX.Value;
                MapMaxX = MapMinX + (w * resolution);
                MapMinY = originY.Value;
                MapMaxY = MapMinY + (h * resolution);
            }
            else
            {
                // Exact calibrated bounds for default OrionStar building floor (260x380, res=0.05, origin=(-5.0, -6.0)):
                // map.pgm: origin_x = -5.0, origin_y = -6.0, res = 0.05, 260x380
                // X range: [-5.0, +8.0] (span = 13.0m)
                // Y range: [-6.0, +13.0] (span = 19.0m)
                MapMinX = -5.0;
                MapMaxX = 8.0;
                MapMinY = -6.0;
                MapMaxY = 13.0;
            }

            foreach (var p in places ?? Enumerable.Empty<MapPlaceItem>())
            {
                p.CanvasX = WorldToCanvasX(p.X);
                p.CanvasY = WorldToCanvasY(p.Y);
                p.Icon = (p.Name.ToLowerInvariant().Contains("charge") || p.Name.ToLowerInvariant().Contains("sạc")) ? "⚡" : "📍";
            }
        }

        public double WorldToCanvasX(double worldX)
        {
            double spanX = Math.Max(0.1, MapMaxX - MapMinX);
            double normX = (worldX - MapMinX) / spanX;
            normX = Math.Clamp(normX, 0.0, 1.0);
            return Math.Round(normX * MapDisplayWidth, 1);
        }

        public double WorldToCanvasY(double worldY)
        {
            double spanY = Math.Max(0.1, MapMaxY - MapMinY);
            double normY = (MapMaxY - worldY) / spanY;
            normY = Math.Clamp(normY, 0.0, 1.0);
            return Math.Round(normY * MapDisplayHeight, 1);
        }

        [RelayCommand]
        public void ClearTrajectory()
        {
            TrajectoryPath.Clear();
            AddLog("Đã xóa vệt quỹ đạo di chuyển trên bản đồ.");
        }
    }

    public enum AlertSeverity
    {
        Info,
        Warning,
        Error,
        Success
    }

    public class AlertItem
    {
        public string Title { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;
        public AlertSeverity Severity { get; set; } = AlertSeverity.Info;
        public string Timestamp { get; set; } = string.Empty;
    }

    public class SavedStationRecord
    {
        public string Name { get; set; } = string.Empty;
        public double X { get; set; }
        public double Y { get; set; }
        public double Theta { get; set; }
    }
}
