using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace LeHoangControlCenter.Models
{
    public class TelemetryMessage
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "TELEMETRY";

        [JsonPropertyName("robotId")]
        public string RobotId { get; set; } = string.Empty;

        [JsonPropertyName("timestamp")]
        public long Timestamp { get; set; }

        [JsonPropertyName("battery")]
        public BatteryInfo? Battery { get; set; }

        [JsonPropertyName("pose")]
        public PoseInfo? Pose { get; set; }

        [JsonPropertyName("status")]
        public RobotStatusInfo? Status { get; set; }

        [JsonPropertyName("errors")]
        public List<string>? Errors { get; set; }
    }

    public class BatteryInfo
    {
        [JsonPropertyName("level")]
        public int Level { get; set; }

        [JsonPropertyName("isCharging")]
        public bool IsCharging { get; set; }
    }

    public class PoseInfo
    {
        [JsonPropertyName("x")]
        public double X { get; set; }

        [JsonPropertyName("y")]
        public double Y { get; set; }

        [JsonPropertyName("theta")]
        public double Theta { get; set; }

        [JsonPropertyName("isEstimated")]
        public bool IsEstimated { get; set; }
    }

    public class RobotStatusInfo
    {
        [JsonPropertyName("state")]
        public string State { get; set; } = "IDLE"; // IDLE, MOVING, NAVIGATING, CRUISING, LEADING, CHARGING

        [JsonPropertyName("currentMap")]
        public string CurrentMap { get; set; } = string.Empty;

        [JsonPropertyName("emergencyStop")]
        public bool EmergencyStop { get; set; }

        [JsonPropertyName("currentPlace")]
        public string CurrentPlace { get; set; } = string.Empty;

        [JsonPropertyName("doorStatus")]
        public string DoorStatus { get; set; } = "CLOSED";

        [JsonPropertyName("speed")]
        public double Speed { get; set; }
    }

    public class CommandResultMessage
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "COMMAND_RESULT";

        [JsonPropertyName("msgId")]
        public string MsgId { get; set; } = string.Empty;

        [JsonPropertyName("command")]
        public string Command { get; set; } = string.Empty;

        [JsonPropertyName("success")]
        public bool Success { get; set; }

        [JsonPropertyName("resultCode")]
        public int ResultCode { get; set; }

        [JsonPropertyName("message")]
        public string Message { get; set; } = string.Empty;
    }

    public class MapDataMessage
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "MAP_DATA";

        [JsonPropertyName("currentMap")]
        public string CurrentMap { get; set; } = string.Empty;

        [JsonPropertyName("mapList")]
        public List<string> MapList { get; set; } = new();

        [JsonPropertyName("imageBase64")]
        public string ImageBase64 { get; set; } = string.Empty;

        [JsonPropertyName("originX")]
        public double OriginX { get; set; } = 0.0;

        [JsonPropertyName("originY")]
        public double OriginY { get; set; } = 0.0;

        [JsonPropertyName("resolution")]
        public double Resolution { get; set; } = 0.0;

        [JsonPropertyName("pgmWidth")]
        public int PgmWidth { get; set; } = 0;

        [JsonPropertyName("pgmHeight")]
        public int PgmHeight { get; set; } = 0;

        [JsonPropertyName("places")]
        public List<MapPlaceItem> Places { get; set; } = new();
    }

    public class MapPlaceItem
    {
        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;

        [JsonPropertyName("x")]
        public double X { get; set; }

        [JsonPropertyName("y")]
        public double Y { get; set; }

        [JsonPropertyName("theta")]
        public double Theta { get; set; } = 0.0;

        public double CanvasX { get; set; } = 0.0;
        public double CanvasY { get; set; } = 0.0;

        public string Icon
        {
            get
            {
                if (string.IsNullOrEmpty(Name)) return "📍";
                string n = Name.ToLowerInvariant();
                if (n.Contains("trongthang") || n.Contains("cabin")) return "🛗";
                if (n.Contains("chothang") || n.Contains("sanh") || n.Contains("waiting")) return "🚪";
                if (n.Contains("charg") || n.Contains("sac")) return "⚡";
                if (n.Contains("stand-by") || n.Contains("cho")) return "⏸️";
                return "📍";
            }
            set { }
        }

        public string BadgeColor
        {
            get
            {
                if (string.IsNullOrEmpty(Name)) return "#00E5FF";
                string n = Name.ToLowerInvariant();
                if (n.Contains("trongthang") || n.Contains("cabin")) return "#10B981"; // Emerald
                if (n.Contains("chothang") || n.Contains("sanh") || n.Contains("waiting")) return "#38BDF8"; // Sky
                if (n.Contains("charg") || n.Contains("sac")) return "#F59E0B"; // Amber
                return "#00E5FF"; // Cyan
            }
        }

        public string TypeBadge
        {
            get
            {
                if (string.IsNullOrEmpty(Name)) return "Trạm";
                string n = Name.ToLowerInvariant();
                if (n.Contains("trongthang") || n.Contains("cabin")) return "🛗 Cabin";
                if (n.Contains("chothang") || n.Contains("sanh") || n.Contains("waiting")) return "🚪 Chờ sảnh";
                if (n.Contains("charg") || n.Contains("sac")) return "⚡ Sạc";
                return "Điểm";
            }
        }
    }
}

