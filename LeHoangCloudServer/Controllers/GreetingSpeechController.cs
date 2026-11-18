using LeHoangCloudServer.Services;
using Microsoft.AspNetCore.Mvc;

namespace LeHoangCloudServer.Controllers;

[ApiController]
[Route("api/greeting/speech")]
public sealed class GreetingSpeechController(GreetingSpeechService speech) : ControllerBase
{
    [HttpGet("status")]
    public IActionResult Status()
    {
        if (!speech.Authorized(Request.Headers.Authorization)) return Unauthorized(new { error = "speech_unauthorized" });
        return Ok(new { ready = speech.Ready, model = speech.Model });
    }

    [HttpPost("transcribe")]
    [Consumes("audio/wav")]
    [RequestSizeLimit(GreetingSpeechService.MaximumWaveBytes)]
    public async Task<IActionResult> Transcribe(CancellationToken cancellation)
    {
        if (!speech.Authorized(Request.Headers.Authorization)) return Unauthorized(new { error = "speech_unauthorized" });
        if (!speech.Ready) return StatusCode(503, new { error = "speech_not_configured" });
        using var audio = new MemoryStream();
        var buffer = new byte[8192];
        int count;
        while ((count = await Request.Body.ReadAsync(buffer, cancellation)) > 0)
        {
            if (audio.Length + count > GreetingSpeechService.MaximumWaveBytes) return StatusCode(413);
            await audio.WriteAsync(buffer.AsMemory(0, count), cancellation);
        }
        var result = await speech.TranscribeAsync(audio.ToArray(), cancellation);
        if (result.Status == 429) Response.Headers.RetryAfter = "2";
        return StatusCode(result.Status, new { text = result.Text, error = result.Error, upstreamStatus = result.UpstreamStatus });
    }
}
