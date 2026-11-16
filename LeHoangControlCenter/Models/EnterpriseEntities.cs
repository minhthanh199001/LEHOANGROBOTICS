using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace LeHoangControlCenter.Models
{
    public partial class OrganizationItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = string.Empty;

        [ObservableProperty]
        private string _name = string.Empty;

        [ObservableProperty]
        private string _code = string.Empty;

        [ObservableProperty]
        private string _description = string.Empty;

        [ObservableProperty]
        private string _contactPerson = string.Empty;

        [ObservableProperty]
        private string _phone = string.Empty;

        [ObservableProperty]
        private string _email = string.Empty;

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;

        [ObservableProperty]
        private int _siteCount = 0;

        [ObservableProperty]
        private int _robotCount = 0;
    }

    public partial class SiteItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = string.Empty;

        [ObservableProperty]
        private string _organizationId = string.Empty;

        [ObservableProperty]
        private string _organizationName = string.Empty;

        [ObservableProperty]
        private string _name = string.Empty;

        [ObservableProperty]
        private string _code = string.Empty;

        [ObservableProperty]
        private string _address = string.Empty;

        [ObservableProperty]
        private string _contactPhone = string.Empty;

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;

        [ObservableProperty]
        private int _robotCount = 0;
    }

    public partial class EnterpriseRobotItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = string.Empty;

        [ObservableProperty]
        private string _siteId = string.Empty;

        [ObservableProperty]
        private string _siteName = string.Empty;

        [ObservableProperty]
        private string _organizationId = string.Empty;

        [ObservableProperty]
        private string _organizationName = string.Empty;

        [ObservableProperty]
        private string _name = string.Empty;

        [ObservableProperty]
        private string _model = "OrionStar Lucki";

        [ObservableProperty]
        private string _ip = "192.168.0.125";

        [ObservableProperty]
        private int _port = 8899;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(BatteryColor))]
        [NotifyPropertyChangedFor(nameof(BatteryDisplayText))]
        private int _batteryLevel = 100;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(BatteryDisplayText))]
        private bool _isCharging = false;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(StatusColor))]
        [NotifyPropertyChangedFor(nameof(IsOnline))]
        private string _status = "Online"; // Online, Active, Charging, Offline, Error

        [ObservableProperty]
        private string _assignedMap = "tăng 2-1110154527";

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;

        [ObservableProperty]
        private DateTime _lastSeen = DateTime.Now;

        public bool IsOnline => !string.IsNullOrEmpty(Status) &&
            (Status.Equals("Online", StringComparison.OrdinalIgnoreCase) ||
             Status.Equals("Available", StringComparison.OrdinalIgnoreCase) ||
             Status.Equals("Active", StringComparison.OrdinalIgnoreCase) ||
             Status.Equals("Charging", StringComparison.OrdinalIgnoreCase));

        public string StatusColor => Status?.ToLowerInvariant() switch
        {
            "online" or "available" => "#10B981",    // Màu xanh lá (Green)
            "active" => "#00E5FF",                   // Xanh ngọc (Cyan)
            "charging" => "#F59E0B",                 // Vàng cam (Amber)
            "offline" => "#64748B",                  // Xám (Slate)
            "error" => "#EF4444",                    // Đỏ (Red)
            _ => "#10B981"
        };

        public string BatteryColor => BatteryLevel switch
        {
            > 40 => "#10B981",
            >= 20 => "#F59E0B",
            _ => "#EF4444"
        };

        public string BatteryDisplayText => IsCharging ? $"⚡ {BatteryLevel}%" : $"🔋 {BatteryLevel}%";
    }

    public partial class UserAccountItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = string.Empty;

        [ObservableProperty]
        private string _username = string.Empty;

        [ObservableProperty]
        private string _password = string.Empty;

        [ObservableProperty]
        private string _fullName = string.Empty;

        [ObservableProperty]
        private string _email = string.Empty;

        [ObservableProperty]
        private string _phone = string.Empty;

        [ObservableProperty]
        private string _role = "Operator"; // SuperAdmin, OrgAdmin, SiteManager, Operator

        [ObservableProperty]
        private string? _organizationId;

        [ObservableProperty]
        private string? _organizationName;

        [ObservableProperty]
        private string? _siteId;

        [ObservableProperty]
        private string? _siteName;

        [ObservableProperty]
        private bool _isActive = true;

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;
    }

    public partial class DeliveryOrderItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = string.Empty;

        [ObservableProperty]
        private string _orderCode = string.Empty;

        [ObservableProperty]
        private string _organizationId = string.Empty;

        [ObservableProperty]
        private string _siteId = string.Empty;

        [ObservableProperty]
        private string _siteName = string.Empty;

        [ObservableProperty]
        private string _robotId = string.Empty;

        [ObservableProperty]
        private string _robotName = string.Empty;

        [ObservableProperty]
        private string _tray1Dest = string.Empty;

        [ObservableProperty]
        private string _tray2Dest = string.Empty;

        [ObservableProperty]
        private string _tray3Dest = string.Empty;

        [ObservableProperty]
        private string _speedMode = "Nhanh (0.7 m/s)";

        [ObservableProperty]
        private string _status = "Hoàn thành"; // Hoàn thành, Đang giao, Đã hủy

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;

        [ObservableProperty]
        private DateTime? _completedAt = DateTime.Now;
    }

    public partial class AlertLogItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = string.Empty;

        [ObservableProperty]
        private string _organizationId = string.Empty;

        [ObservableProperty]
        private string _siteId = string.Empty;

        [ObservableProperty]
        private string _siteName = string.Empty;

        [ObservableProperty]
        private string _robotId = string.Empty;

        [ObservableProperty]
        private string _title = string.Empty;

        [ObservableProperty]
        private string _message = string.Empty;

        [ObservableProperty]
        private string _severity = "Warning"; // Info, Warning, Error, Critical

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;

        [ObservableProperty]
        private bool _isRead = false;
    }
}

