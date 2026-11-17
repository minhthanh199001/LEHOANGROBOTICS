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

    [HttpPost("organizations")]
    public async Task<IActionResult> SaveOrganization([FromBody] CloudOrganization org)
    {
        _db.SaveOrganization(org);
        await _hubContext.Clients.All.SendAsync("OnOrganizationsUpdated", _db.GetOrganizations());
        return Ok(new { success = true, organization = org });
    }

    [HttpDelete("organizations/{id}")]
    public async Task<IActionResult> DeleteOrganization(string id)
    {
        _db.DeleteOrganization(id);
        await _hubContext.Clients.All.SendAsync("OnOrganizationsUpdated", _db.GetOrganizations());
        return Ok(new { success = true, id });
    }

    [HttpGet("sites")]
    public ActionResult<List<CloudSite>> GetSites([FromQuery] string? orgId = null)
    {
        return Ok(_db.GetSites(orgId));
    }

    [HttpPost("sites")]
    public async Task<IActionResult> SaveSite([FromBody] CloudSite site)
    {
        _db.SaveSite(site);
        await _hubContext.Clients.All.SendAsync("OnSitesUpdated", _db.GetSites());
        return Ok(new { success = true, site });
    }

    [HttpDelete("sites/{id}")]
    public async Task<IActionResult> DeleteSite(string id)
    {
        _db.DeleteSite(id);
        await _hubContext.Clients.All.SendAsync("OnSitesUpdated", _db.GetSites());
        return Ok(new { success = true, id });
    }

    [HttpGet("robots")]
    public ActionResult<List<CloudRobotItem>> GetRobots([FromQuery] string? orgId = null, [FromQuery] string? siteId = null)
    {
        return Ok(_db.GetAllRobots(orgId, siteId));
    }

    [HttpPost("robots")]
    public async Task<IActionResult> UpsertRobot([FromBody] CloudRobotItem robot)
    {
        _db.SaveRobot(robot);
        await _hubContext.Clients.All.SendAsync("OnRobotUpserted", robot);
        return Ok(new { success = true, robot });
    }

    [HttpDelete("robots/{id}")]
    public async Task<IActionResult> DeleteRobot(string id)
    {
        var decodedId = System.Net.WebUtility.UrlDecode(id);
        _db.DeleteRobot(decodedId);
        await _hubContext.Clients.All.SendAsync("OnRobotDeleted", decodedId);
        return Ok(new { success = true, id = decodedId });
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
        await _hubContext.Clients.All.SendAsync("OnTelemetryUpdated", packet);
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

    [HttpPost("users")]
    public async Task<IActionResult> SaveUser([FromBody] CloudUserAccountItem user)
    {
        _db.SaveUserAccount(user);
        await _hubContext.Clients.All.SendAsync("OnUsersUpdated", _db.GetUserAccounts());
        return Ok(new { success = true, user });
    }

    [HttpDelete("users/{id}")]
    public async Task<IActionResult> DeleteUser(string id)
    {
        _db.DeleteUserAccount(id);
        await _hubContext.Clients.All.SendAsync("OnUsersUpdated", _db.GetUserAccounts());
        return Ok(new { success = true, id });
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
        await _hubContext.Clients.All.SendAsync("OnCurrentUserUpdated", user);
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

