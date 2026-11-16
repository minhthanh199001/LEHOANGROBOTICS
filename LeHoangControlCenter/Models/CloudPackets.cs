using System;

namespace LeHoangControlCenter.Models
{
    public class RobotTelemetryPacket
    {
        public string RobotId { get; set; } = string.Empty;
        public string OrgId { get; set; } = string.Empty;
        public string SiteId { get; set; } = string.Empty;
        public string Status { get; set; } = "Online";
        public int Battery { get; set; }
        public bool IsCharging { get; set; }
        public string MapName { get; set; } = string.Empty;
        public double PoseX { get; set; }
        public double PoseY { get; set; }
        public double PoseAngle { get; set; }
        public string ActiveMode { get; set; } = "Standby";
        public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
    }

    public class CloudCommandPacket
    {
        public string TargetRobotId { get; set; } = string.Empty;
        public string CommandType { get; set; } = string.Empty; // EMERGENCY_STOP, RETURN_HOME, NAVIGATE, SWITCH_MAP, REBOOT, SPEAK
        public string Parameter { get; set; } = string.Empty;
        public string Issuer { get; set; } = "CloudWebAdmin";
        public DateTime IssuedAt { get; set; } = DateTime.UtcNow;
    }
}

