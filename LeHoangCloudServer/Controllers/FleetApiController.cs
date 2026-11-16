using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using LeHoangCloudServer.Hubs;
using LeHoangCloudServer.Models;
using LeHoangCloudServer.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;

namespace LeHoangCloudServer.Controllers;

[ApiController]
[Route("api/fleet")]
public class FleetApiController : ControllerBase
{
    private readonly CloudDatabaseService _db;
    private readonly IHubContext<FleetHub> _hubContext;

    public FleetApiController(CloudDatabaseService db, IHubContext<FleetHub> hubContext)
    {
        _db = db;
        _hubContext = hubContext;
    }

    [HttpGet("summary")]
    public ActionResult<FleetSummaryDto> GetSummary([FromQuery] string? orgId = null, [FromQuery] string? siteId = null)
    {
        return Ok(_db.GetFleetSummary(orgId, siteId));
    }

    [HttpGet("organizations")]
    public ActionResult<List<CloudOrganization>> GetOrganizations()
    {
        return Ok(_db.GetOrganizations());
    }

    [HttpGet("sites")]
    public ActionResult<List<CloudSite>> GetSites([FromQuery] string? orgId = null)
    {
        return Ok(_db.GetSites(orgId));
    }

    [HttpGet("robots")]
    public ActionResult<List<CloudRobotItem>> GetRobots([FromQuery] string? orgId = null, [FromQuery] string? siteId = null)
    {
        return Ok(_db.GetAllRobots(orgId, siteId));
    }

    [HttpGet("alerts")]
    public ActionResult<List<FleetAlertItem>> GetAlerts()
    {
        return Ok(_db.GetRecentAlerts());
    }

    [HttpPost("telemetry")]
    public async Task<IActionResult> IngestTelemetry([FromBody] RobotTelemetryPacket packet)
    {
        _db.UpdateRobotTelemetry(packet);
        await _hubContext.Clients.Group("Observers").SendAsync("OnTelemetryUpdated", packet);
        return Ok(new { success = true, receivedAt = DateTime.UtcNow });
    }

    [HttpPost("command")]
    public async Task<IActionResult> SendCommand([FromBody] CloudCommandPacket command)
    {
        await _hubContext.Clients.Group("EdgeNodes").SendAsync("OnCloudCommandReceived", command);
        _db.AddAlert(new FleetAlertItem
        {
            RobotId = command.TargetRobotId,
            AlertType = "WARNING",
            Message = $"Lệnh từ REST API [{command.CommandType}] đã gửi tới robot {command.TargetRobotId}",
            Timestamp = DateTime.UtcNow
        });
        return Ok(new { success = true, message = $"Command {command.CommandType} queued for {command.TargetRobotId}" });
    }

    [HttpGet("users")]
    public ActionResult<List<CloudUserAccountItem>> GetUsers()
    {
        return Ok(_db.GetUserAccounts());
    }

    [HttpGet("current-user")]
    public ActionResult<CloudUserAccountItem> GetCurrentUser()
    {
        return Ok(_db.GetCurrentUser());
    }

    [HttpPost("current-user")]
    public async Task<IActionResult> SetCurrentUser([FromBody] CloudUserAccountItem user)
    {
        _db.SetCurrentUser(user);
        await _hubContext.Clients.Group("Observers").SendAsync("OnCurrentUserUpdated", user);
        return Ok(new { success = true, user });
    }

    [HttpPost("login")]
    public async Task<IActionResult> Login([FromBody] CloudLoginRequest request)
    {
        var user = _db.Authenticate(request.Username, request.Password);
        if (user != null)
        {
            await _hubContext.Clients.Group("Observers").SendAsync("OnCurrentUserUpdated", user);
            return Ok(new { success = true, user });
        }
        return Unauthorized(new { success = false, message = "Tài khoản hoặc mật khẩu không chính xác." });
    }

    [HttpGet("map-info")]
    public ActionResult<CloudMapInfo> GetMapInfo([FromQuery] string? mapName = null)
    {
        return Ok(_db.GetMapInfo(mapName));
    }

    [HttpGet("stations")]
    public ActionResult<List<MapStationItem>> GetStations([FromQuery] string? mapName = null)
    {
        return Ok(_db.GetMapInfo(mapName).Stations);
    }
}

