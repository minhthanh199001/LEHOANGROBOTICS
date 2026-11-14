using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace LeHoangControlCenter.Models
{
    public class MapWaypoint
    {
        public string Name { get; set; } = string.Empty;
        public double X { get; set; } // meters
        public double Y { get; set; } // meters
        public string PointType { get; set; } = "TABLE"; // TABLE, CHARGE_DOCK, RECEPTION, PATROL_POINT
        public string Color { get; set; } = "#38BDF8";
    }

    public partial class FleetRobotItem : ObservableObject
    {
        [ObservableProperty]
        private string _id = "LH-ROBOT-01";

        [ObservableProperty]
        private string _name = "Robot Phục Vụ 01";

        [ObservableProperty]
        private string _ip = "192.168.1.100";

        [ObservableProperty]
        private int _port = 8088;

        [ObservableProperty]
        private int _batteryLevel = 100;

        [ObservableProperty]
        private bool _isOnline;

        [ObservableProperty]
        private string _state = "IDLE";

        [ObservableProperty]
        private string _currentTask = "Chờ lệnh";
    }

    public enum TaskType
    {
        Delivery,
        Leading,
        Patrol,
        Charge
    }

    public enum MissionTaskStatus
    {
        Pending,
        Running,
        Completed,
        Failed,
        Cancelled
    }

    public partial class RobotMissionTask : ObservableObject
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N")[..8];

        [ObservableProperty]
        private string _title = string.Empty;

        [ObservableProperty]
        private TaskType _type = TaskType.Delivery;

        [ObservableProperty]
        private string _destination = string.Empty;

        [ObservableProperty]
        private string _speechAnnouncement = string.Empty;

        [ObservableProperty]
        private MissionTaskStatus _status = MissionTaskStatus.Pending;

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;
    }
}
