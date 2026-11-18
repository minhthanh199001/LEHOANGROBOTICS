using System.Diagnostics;
using System.Globalization;
using System.Text.RegularExpressions;

namespace LeHoangCloudServer.Services;

public sealed record MicroArraySample(long AtUnixMs, int Active, int RawAngle);
public sealed record MicroArrayDirection(string Source, double Angle, long AtUnixMs, int SampleCount,
    double SpreadDegrees, long CaptureStartUnixMs, long CaptureEndUnixMs);

/// <summary>Read-only diagnostic bridge. Does not replace Orion's microphone/data-collection callbacks.</summary>
public sealed class MicroArrayTelemetry(IConfiguration configuration) : BackgroundService
{
    // Verified installed AgentService 100.3.168 / V11.9. Its SoundInfoTask adds 30 degrees
    // to the logged DSP audio_angle before sending the angle to Orion clients.
    public const string SupportedAgentSha256 = "27a3aa8f6b263cff696b69dee8afcd3218f1989456a3000d9e3f38d334a3c555";
    public const string DirectionSource = "orion-microarray-native-debug";
    private readonly string _serial = configuration["GREETING_ROBOT_ADB_SERIAL"] ?? "";
    private readonly string _adb = configuration["GREETING_ADB_PATH"] ?? "adb";
    private readonly object _sync = new();
    private readonly List<MicroArraySample> _samples = [];
    private long _lastArrival;
    private string _state = "disabled", _bootId = "";
    private bool _verified;
    private static readonly Regex Line = new(@"^\s*(\d+\.\d+)\s+\d+\s+\d+\s+V\s+SoundInfoTask:\s*active:(-?\d+),audio_angle:(-?\d+),beam_angle:-?\d+\s*$", RegexOptions.Compiled);

    public object Status { get { lock (_sync) return new { enabled = _serial.Length > 0, ready = Fresh(), state = _state }; } }
    private bool Fresh() => _verified && _lastArrival > 0 && Environment.TickCount64 - _lastArrival < 2000;
    public MicroArrayDirection? ForCapture(string bootId, long start, long end, long? voiceEnd = null)
    {
        lock (_sync)
        {
            if (!Fresh() || bootId.Length == 0 || !string.Equals(bootId, _bootId, StringComparison.OrdinalIgnoreCase)) return null;
            return SelectSpeechWindow(_samples, start, end, voiceEnd);
        }
    }
    public object CaptureStatus(string bootId, long start, long end, long? voiceEnd = null)
    {
        lock (_sync)
        {
            var window = _samples.Where(x => x.AtUnixMs >= start - 250 && x.AtUnixMs <= (voiceEnd ?? end) + 200).ToArray();
            int active = window.Count(x => x.Active == 1 && x.RawAngle is >= 0 and <= 360);
            return new { state = !Fresh() ? "stream_unavailable" : bootId != _bootId ? "wrong_robot" :
                window.Length == 0 ? "no_matching_timestamps" : active < 3 ? "insufficient_active_samples" :
                SelectSpeechWindow(window, start, end, voiceEnd) is null ? "unstable_direction" : "matched",
                samples = window.Length, activeSamples = active,
                latestDeltaMs = _samples.Count == 0 ? (long?)null : _samples[^1].AtUnixMs - end };
        }
    }
    public static MicroArraySample? Parse(string line)
    {
        var match = Line.Match(line);
        if (!match.Success || !decimal.TryParse(match.Groups[1].Value, NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture, out var seconds)
            || seconds < 0 || seconds > long.MaxValue / 1000m
            || !int.TryParse(match.Groups[2].Value, out var active) || !int.TryParse(match.Groups[3].Value, out var angle)) return null;
        return new((long)(seconds * 1000), active, angle);
    }
    public static MicroArrayDirection? Select(IEnumerable<MicroArraySample> samples, long start, long end)
    {
        if (start < 1 || end < start || end - start > 6500) return null;
        var voice = samples.Where(x => x.Active == 1 && x.RawAngle is >= 0 and <= 360
            && x.AtUnixMs >= start - 250 && x.AtUnixMs <= end + 200).DistinctBy(x => x.AtUnixMs).OrderBy(x => x.AtUnixMs).ToArray();
        if (voice.Length < 3 || voice[^1].AtUnixMs - voice[0].AtUnixMs < 100) return null;
        // DSP occasionally reports a short jump to a different direction during one utterance.
        // Use a tightly grouped majority; retain the ambiguity guard for competing speakers.
        double Distance(double a, double b) => Math.Abs((a - b + 540) % 360 - 180);
        var inliers = voice.Select(seed => voice.Where(x => Distance((x.RawAngle + 30) % 360d,
            (seed.RawAngle + 30) % 360d) <= 25).ToArray()).OrderByDescending(x => x.Length).First();
        if (inliers.Length < 3 || inliers.Length <= voice.Length * .60 || inliers[^1].AtUnixMs - inliers[0].AtUnixMs < 100) return null;
        var angles = inliers.Select(x => (x.RawAngle + 30) % 360d).ToArray();
        double sine = angles.Sum(x => Math.Sin(x * Math.PI / 180)), cosine = angles.Sum(x => Math.Cos(x * Math.PI / 180));
        if (Math.Sqrt(sine * sine + cosine * cosine) / inliers.Length < .92) return null;
        double mean = (Math.Atan2(sine, cosine) * 180 / Math.PI + 360) % 360;
        double spread = angles.Max(x => Math.Abs((x - mean + 540) % 360 - 180));
        if (spread > 35) return null;
        return new(DirectionSource, mean, inliers[^1].AtUnixMs, inliers.Length, spread, start, end);
    }
    public static MicroArrayDirection? SelectSpeechWindow(IEnumerable<MicroArraySample> samples, long start, long end, long? voiceEnd)
    {
        if (voiceEnd is null) return Select(samples, start, end);
        if (voiceEnd < start || voiceEnd > end) return null;
        return Select(samples.Where(x => x.AtUnixMs <= voiceEnd.Value + 200), start, end);
    }
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        if (_serial.Length == 0) return;
        string? previousLevel = null;
        try
        {
            while (!stop.IsCancellationRequested)
            {
                Process? log = null;
                try
                {
                    SetState("connecting");
                    string sha = await Command(stop, "shell", "sha256sum", "/system/priv-app/AgentService/AgentService.apk");
                    if (!sha.TrimStart().StartsWith(SupportedAgentSha256, StringComparison.OrdinalIgnoreCase)) {
                        SetState("unsupported_firmware"); await Task.Delay(5000, stop); continue;
                    }
                    string boot = (await Command(stop, "shell", "cat", "/proc/sys/kernel/random/boot_id")).Trim();
                    if (!Guid.TryParse(boot, out _)) throw new IOException("boot_identity_unavailable");
                    string pid = (await Command(stop, "shell", "pidof", "com.ainirobot.agentservice")).Trim();
                    if (!Regex.IsMatch(pid, @"^\d+$")) throw new IOException("agent_process_unavailable");
                    if (previousLevel is null) {
                        string level = (await Command(stop, "shell", "getprop", "log.tag.SoundInfoTask")).Trim();
                        if (!Regex.IsMatch(level, @"^(|[VDIWEFS]|VERBOSE|DEBUG|INFO|WARN|ERROR|FATAL|SILENT)$", RegexOptions.IgnoreCase))
                            throw new IOException("unsupported_log_level");
                        previousLevel = level;
                    }
                    await Command(stop, "shell", "setprop", "log.tag.SoundInfoTask", "V");
                    lock (_sync) { _samples.Clear(); _bootId = boot; _lastArrival = 0; _verified = true; _state = "waiting_for_samples"; }
                    log = Start("logcat", "-v", "epoch", "-T", "1", "--pid=" + pid, "-s", "SoundInfoTask:V", "*:S");
                    // Drain stderr without logging vendor diagnostics or microphone content.
                    Task stderr = log.StandardError.ReadToEndAsync(stop);
                    while (!stop.IsCancellationRequested)
                    {
                        using var lineTimeout = CancellationTokenSource.CreateLinkedTokenSource(stop);
                        lineTimeout.CancelAfter(TimeSpan.FromSeconds(4));
                        string? text = await log.StandardOutput.ReadLineAsync(lineTimeout.Token);
                        if (text is null) break;
                        var sample = Parse(text);
                        if (sample is null) continue;
                        lock (_sync)
                        {
                            if (_samples.Count > 0 && sample.AtUnixMs < _samples[^1].AtUnixMs - 1000) _samples.Clear();
                            _samples.Add(sample); _samples.RemoveAll(x => sample.AtUnixMs - x.AtUnixMs > 35000);
                            if (_samples.Count > 1000) _samples.RemoveRange(0, _samples.Count - 1000);
                            _lastArrival = Environment.TickCount64; _state = "streaming";
                        }
                    }
                }
                catch (OperationCanceledException) when (!stop.IsCancellationRequested) { SetState("stream_timeout"); }
                catch (Exception) when (!stop.IsCancellationRequested) { SetState("robot_unavailable"); }
                finally
                {
                    if (log is not null) { try { if (!log.HasExited) log.Kill(); } catch (InvalidOperationException) {} log.Dispose(); }
                    lock (_sync) { _verified = false; _samples.Clear(); }
                }
                await Task.Delay(1000, stop);
            }
        }
        catch (OperationCanceledException) when (stop.IsCancellationRequested) {}
        finally
        {
            if (previousLevel is not null) {
                try { await Command(CancellationToken.None, "shell", "setprop log.tag.SoundInfoTask '" + previousLevel + "'"); }
                catch (Exception) { /* A disconnected robot may be unavailable during shutdown. */ }
            }
            SetState("stopped");
        }
    }
    private void SetState(string state) { lock (_sync) { _state = state; _verified = false; } }
    private Process Start(params string[] args)
    {
        var info = new ProcessStartInfo(_adb) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        info.ArgumentList.Add("-s"); info.ArgumentList.Add(_serial);
        foreach (string arg in args) info.ArgumentList.Add(arg);
        return Process.Start(info) ?? throw new IOException("adb_start_failed");
    }
    private async Task<string> Command(CancellationToken stop, params string[] args)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(stop); timeout.CancelAfter(TimeSpan.FromSeconds(5));
        using var process = Start(args);
        try {
            var output = process.StandardOutput.ReadToEndAsync(timeout.Token);
            var error = process.StandardError.ReadToEndAsync(timeout.Token);
            await process.WaitForExitAsync(timeout.Token);
            string result = await output; await error;
            if (process.ExitCode != 0) throw new IOException("adb_command_failed");
            return result;
        } finally { try { if (!process.HasExited) process.Kill(); } catch (InvalidOperationException) {} }
    }
}
