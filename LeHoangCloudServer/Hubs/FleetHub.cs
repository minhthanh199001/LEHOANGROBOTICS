using System;
using System.Threading.Tasks;
using LeHoangCloudServer.Models;
using LeHoangCloudServer.Services;
using Microsoft.AspNetCore.SignalR;
using Microsoft.Extensions.Logging;

namespace LeHoangCloudServer.Hubs;

public class FleetHub : Hub
{
    private readonly CloudDatabaseService _db;
    private readonly ILogger<FleetHub> _logger;

    public FleetHub(CloudDatabaseService db, ILogger<FleetHub> logger)
    {
        _db = db;
        _logger = logger;
    }

    public override async Task OnConnectedAsync()
    {
        _logger.LogInformation("Client connected: {ConnectionId}", Context.ConnectionId);
        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        _logger.LogInformation("Client disconnected: {ConnectionId}", Context.ConnectionId);
        await base.OnDisconnectedAsync(exception);
    }

    /// <summary>
    /// Edge Control Center or Robot registers itself with organization & site info
    /// </summary>
    public async Task RegisterEdgeNode(string robotId, string orgId, string siteId)
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, "EdgeNodes");
        if (!string.IsNullOrEmpty(siteId))
        {
            await Groups.AddToGroupAsync(Context.ConnectionId, $"Site_{siteId}");
        }
        _logger.LogInformation("EdgeNode registered: {RobotId} at {OrgId}/{SiteId}", robotId, orgId, siteId);
    }

    /// <summary>
    /// Web dashboard or Mobile app registers as an Observer
    /// </summary>
    public async Task RegisterObserver(string? siteId = null)
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, "Observers");
        if (!string.IsNullOrEmpty(siteId))
        {
            await Groups.AddToGroupAsync(Context.ConnectionId, $"Observer_{siteId}");
        }
        
        // Send initial fleet state immediately
        var robots = _db.GetAllRobots(null, siteId);
        var summary = _db.GetFleetSummary(null, siteId);
        var currentUser = _db.GetCurrentUser();
        await Clients.Caller.SendAsync("OnInitialStateReceived", summary, robots, currentUser);
    }

    /// <summary>
    /// Edge Control Center or Web client syncs currently logged-in user profile
    /// </summary>
    public async Task ReportCurrentUser(CloudUserAccountItem user)
    {
        _db.SetCurrentUser(user);
        await Clients.Group("Observers").SendAsync("OnCurrentUserUpdated", user);
    }

    /// <summary>
    /// Edge Control Center streams real-time robot telemetry
    /// </summary>
    public async Task ReportTelemetry(RobotTelemetryPacket packet)
    {
        _db.UpdateRobotTelemetry(packet);

        // Broadcast to all active observers (Web Dashboards, Mobile Apps)
        await Clients.Group("Observers").SendAsync("OnTelemetryUpdated", packet);
    }

    /// <summary>
    /// Report an event or alert
    /// </summary>
    public async Task ReportAlert(string robotId, string alertType, string message)
    {
        var robot = _db.GetRobotById(robotId);
        var alert = new FleetAlertItem
        {
            RobotId = robotId,
            RobotName = robot?.Name ?? robotId,
            SiteName = robot?.SiteName ?? "Chi nhánh",
            AlertType = alertType,
            Message = message,
            Timestamp = DateTime.UtcNow
        };
        _db.AddAlert(alert);

        await Clients.Group("Observers").SendAsync("OnAlertReceived", alert);
    }

    /// <summary>
    /// Cloud Web Dashboard or Mobile app sends a command down to edge robot
    /// </summary>
    public async Task SendCommand(CloudCommandPacket command)
    {
        _logger.LogInformation("Cloud command issued: {CommandType} to {RobotId}", command.CommandType, command.TargetRobotId);
        
        // Broadcast command to edge nodes managing this robot
        await Clients.Group("EdgeNodes").SendAsync("OnCloudCommandReceived", command);
        
        // Also record an alert
        var alert = new FleetAlertItem
        {
            RobotId = command.TargetRobotId,
            AlertType = "WARNING",
            Message = $"Lệnh từ Cloud [{command.CommandType}] đã gửi tới robot {command.TargetRobotId}",
            Timestamp = DateTime.UtcNow
        };
        _db.AddAlert(alert);
        await Clients.Group("Observers").SendAsync("OnAlertReceived", alert);
    }
}

