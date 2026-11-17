using System;
using System.Collections.Generic;

namespace LeHoangCloudServer.Models;

public class CloudRobotItem
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Model { get; set; } = "OrionStar Mini";
    public string OrgId { get; set; } = string.Empty;
    public string OrgName { get; set; } = string.Empty;
    public string SiteId { get; set; } = string.Empty;
    public string SiteName { get; set; } = string.Empty;
    public string Ip { get; set; } = string.Empty;
    public int Port { get; set; } = 8899;
    public string Status { get; set; } = "Offline"; // Online, Charging, Offline, EmergencyStop
    public string StatusColor { get; set; } = "#64748B";
    public int Battery { get; set; } = 100;
    public bool IsCharging { get; set; } = false;
    public string BatteryText { get; set; } = "100%";
    public string BatteryColor { get; set; } = "#10B981";
    public string AssignedMap { get; set; } = "default_floor";
    public double PoseX { get; set; } = 0.0;
    public double PoseY { get; set; } = 0.0;
    public double PoseAngle { get; set; } = 0.0;
    public string ActiveMode { get; set; } = "Standby";
    public DateTime LastSeen { get; set; } = DateTime.UtcNow;
}

public class CloudOrganization
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Code { get; set; } = string.Empty;
}

public class CloudSite
{
    public string Id { get; set; } = string.Empty;
    public string OrganizationId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Code { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public string Phone { get; set; } = string.Empty;
}

public class FleetSummaryDto
{
    public int TotalOrganizations { get; set; }
    public int TotalSites { get; set; }
    public int TotalRobots { get; set; }
    public int OnlineRobots { get; set; }
    public int ChargingRobots { get; set; }
    public int OfflineRobots { get; set; }
    public int AvgBattery { get; set; }
    public int ActiveOrders { get; set; }
    public DateTime ServerTime { get; set; } = DateTime.UtcNow;
}

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
    public string CommandType { get; set; } = string.Empty; // EMERGENCY_STOP, RETURN_HOME, NAVIGATE, SWITCH_MAP, REBOOT
    public string Parameter { get; set; } = string.Empty;
    public string Issuer { get; set; } = "CloudWebAdmin";
    public DateTime IssuedAt { get; set; } = DateTime.UtcNow;
}

public class FleetAlertItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N")[..8];
    public string RobotId { get; set; } = string.Empty;
    public string RobotName { get; set; } = string.Empty;
    public string SiteName { get; set; } = string.Empty;
    public string AlertType { get; set; } = "INFO"; // WARNING, ERROR, SUCCESS, INFO
    public string Message { get; set; } = string.Empty;
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
}

public class CloudUserAccountItem
{
    public string Id { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public string? Password { get; set; }
    public string FullName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Role { get; set; } = "SuperAdmin";
    public string? OrganizationId { get; set; }
    public string? OrganizationName { get; set; }
    public string? SiteId { get; set; }
    public string? SiteName { get; set; }
    public bool IsActive { get; set; } = true;
    public string AvatarInitials => GetInitials(FullName);

    private static string GetInitials(string name)
    {
        if (string.IsNullOrWhiteSpace(name)) return "QH";
        var parts = name.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length == 1) return parts[0][..Math.Min(2, parts[0].Length)].ToUpper();
        return $"{parts[0][0]}{parts[^1][0]}".ToUpper();
    }
}

public class CloudLoginRequest
{
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public class MapStationItem
{
    public string Name { get; set; } = string.Empty;
    public double X { get; set; }
    public double Y { get; set; }
    public double Theta { get; set; }
    public string Icon { get; set; } = "📍";
    public string Type { get; set; } = "delivery"; // charge, dock, elevator, standby, delivery
}

public class CloudMapInfo
{
    public string MapName { get; set; } = "tầng 2-1110154527";
    public string ImageUrl { get; set; } = "img/default_map_hd.png";
    public double MinX { get; set; } = -2.0;
    public double MaxX { get; set; } = 6.0;
    public double MinY { get; set; } = -2.0;
    public double MaxY { get; set; } = 13.0;
    public List<MapStationItem> Stations { get; set; } = new();
}


