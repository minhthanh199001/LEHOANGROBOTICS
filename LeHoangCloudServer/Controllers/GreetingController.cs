using System.Security.Cryptography;
using System.Text;
using LeHoangCloudServer.Models;
using LeHoangCloudServer.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;

namespace LeHoangCloudServer.Controllers;

[ApiController]
[Route("api/greeting")]
public sealed class GreetingController : ControllerBase
{
    private readonly GreetingService _service;
    private readonly IConfiguration _configuration;
    public GreetingController(GreetingService service, IConfiguration configuration)
    { _service = service; _configuration = configuration; }

    private IActionResult? CheckAdmin()
    {
        var key = Environment.GetEnvironmentVariable("GREETING_ADMIN_KEY") ?? _configuration["Greeting:AdminKey"];
        if (string.IsNullOrWhiteSpace(key) || key.Length < 24)
            return StatusCode(503, new { message = "Cấu hình GREETING_ADMIN_KEY (ít nhất 24 ký tự) trên server để bật quản lý Greetingbot." });
        var supplied = Request.Headers["X-Greeting-Admin-Key"].ToString();
        return CryptographicOperations.FixedTimeEquals(SHA256.HashData(Encoding.UTF8.GetBytes(key)),
            SHA256.HashData(Encoding.UTF8.GetBytes(supplied))) ? null : Unauthorized(new { message = "Mã quản trị Greetingbot không đúng." });
    }

    [HttpGet("robots")]
    public IActionResult List() => CheckAdmin() ?? Ok(_service.List());

    [HttpPost("robots")]
    public IActionResult Register(GreetingRegistration registration)
    {
        var error = CheckAdmin(); if (error != null) return error;
        try { var result = _service.Register(registration); return Ok(new { robot = result.Robot, deviceToken = result.Token }); }
        catch (SqliteException ex) when (ex.SqliteErrorCode == 19) { return Conflict(new { message = "ID robot đã tồn tại. Chọn robot trong danh sách." }); }
    }

    [HttpGet("robots/{id}")]
    public IActionResult Get(string id)
    {
        var error = CheckAdmin(); if (error != null) return error;
        var robot = _service.Get(id); return robot == null ? NotFound() : Ok(robot);
    }

    [HttpPut("robots/{id}/settings")]
    public IActionResult Settings(string id, GreetingSettings settings)
    {
        var error = CheckAdmin(); if (error != null) return error;
        return _service.SaveSettings(id, settings) ? Ok(settings) : NotFound();
    }

    [HttpPost("robots/{id}/commands")]
    public IActionResult Command(string id, GreetingCommandRequest request)
    {
        var error = CheckAdmin(); if (error != null) return error;
        try { return Accepted($"/api/greeting/robots/{id}", _service.Enqueue(id, request)); }
        catch (KeyNotFoundException ex) { return NotFound(new { message = ex.Message }); }
        catch (InvalidOperationException ex) { return Conflict(new { message = ex.Message }); }
    }

    [HttpPost("device/{id}/sync")]
    [RequestSizeLimit(524288)]
    public IActionResult Sync(string id, GreetingSyncRequest request)
    {
        if (!_service.Authenticate(id, Request.Headers["X-Greeting-Device-Token"].ToString()))
            return Unauthorized(new { message = "ID hoặc mã ghép nối thiết bị không đúng." });
        return Ok(_service.Sync(id, request));
    }
}
