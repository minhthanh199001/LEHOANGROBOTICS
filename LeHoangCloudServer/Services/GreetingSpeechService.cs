using System.Buffers.Binary;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace LeHoangCloudServer.Services;

/// <summary>Bounded, authenticated audio transcription. API keys and recordings never enter the fleet DB.</summary>
public sealed class GreetingSpeechService(HttpClient client, IConfiguration configuration)
{
    public const int MaximumWaveBytes = 256044;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private readonly string _token = configuration["GREETING_SPEECH_TOKEN"] ?? "";
    private readonly string _key = ReadKey(configuration);
    public bool Ready => _token.Length >= 32 && _key.Length > 0;
    public string Model => "gpt-transcribe";

    public bool Authorized(string? header)
    {
        if (_token.Length < 32 || header is null || !header.StartsWith("Bearer ", StringComparison.Ordinal)) return false;
        return CryptographicOperations.FixedTimeEquals(SHA256.HashData(Encoding.UTF8.GetBytes(_token)),
            SHA256.HashData(Encoding.UTF8.GetBytes(header[7..])));
    }

    private static string ReadKey(IConfiguration configuration)
    {
        string text = configuration["OPENAI_API_KEY"] ?? "";
        string path = configuration["OPENAI_API_KEY_FILE"] ?? "";
        if (path.Length > 0)
        {
            text = "";
            try { if (new FileInfo(path).Length <= 4096) text = File.ReadAllText(path); }
            catch (IOException) { return ""; }
            catch (UnauthorizedAccessException) { return ""; }
        }
        var match = Regex.Match(text, @"sk-[A-Za-z0-9_-]{20,}");
        return match.Success ? match.Value : "";
    }

    public static bool ValidWave(ReadOnlySpan<byte> wave)
    {
        if (wave.Length < 44 + 14400 || wave.Length > MaximumWaveBytes || (wave.Length - 44) % 2 != 0) return false;
        return wave[..4].SequenceEqual("RIFF"u8) && wave.Slice(8, 4).SequenceEqual("WAVE"u8)
            && wave.Slice(12, 4).SequenceEqual("fmt "u8) && wave.Slice(36, 4).SequenceEqual("data"u8)
            && BinaryPrimitives.ReadUInt32LittleEndian(wave[4..]) == wave.Length - 8
            && BinaryPrimitives.ReadUInt32LittleEndian(wave[16..]) == 16
            && BinaryPrimitives.ReadUInt16LittleEndian(wave[20..]) == 1
            && BinaryPrimitives.ReadUInt16LittleEndian(wave[22..]) == 1
            && BinaryPrimitives.ReadUInt32LittleEndian(wave[24..]) == 16000
            && BinaryPrimitives.ReadUInt32LittleEndian(wave[28..]) == 32000
            && BinaryPrimitives.ReadUInt16LittleEndian(wave[32..]) == 2
            && BinaryPrimitives.ReadUInt16LittleEndian(wave[34..]) == 16
            && BinaryPrimitives.ReadUInt32LittleEndian(wave[40..]) == wave.Length - 44;
    }

    public async Task<SpeechResult> TranscribeAsync(byte[] wave, CancellationToken cancellation)
    {
        if (!Ready) return new(503, "speech_not_configured");
        if (!ValidWave(wave)) return new(400, "invalid_wave");
        if (!await _gate.WaitAsync(0, cancellation)) return new(429, "speech_busy");
        try
        {
            using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
            deadline.CancelAfter(TimeSpan.FromSeconds(12));
            using var form = new MultipartFormDataContent();
            var audio = new ByteArrayContent(wave);
            audio.Headers.ContentType = new MediaTypeHeaderValue("audio/wav");
            form.Add(audio, "file", "greeting.wav");
            form.Add(new StringContent(Model), "model");
            form.Add(new StringContent("en"), "languages[]");
            form.Add(new StringContent("vi"), "languages[]");
            form.Add(new StringContent("Hello robot"), "keywords[]");
            form.Add(new StringContent("robot ơi", Encoding.UTF8), "keywords[]");
            using var request = new HttpRequestMessage(HttpMethod.Post, "https://api.openai.com/v1/audio/transcriptions");
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _key);
            request.Content = form;
            using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, deadline.Token);
            // Never return raw provider errors: they may contain credential or request details.
            if (!response.IsSuccessStatusCode) return new(502, "openai_error", UpstreamStatus: (int)response.StatusCode);
            await using var stream = await response.Content.ReadAsStreamAsync(deadline.Token);
            using var json = await JsonDocument.ParseAsync(stream, cancellationToken: deadline.Token);
            if (!json.RootElement.TryGetProperty("text", out var text) || text.ValueKind != JsonValueKind.String)
                return new(502, "invalid_openai_response");
            string transcript = text.GetString() ?? "";
            if (transcript.Length > 2000) return new(502, "transcript_too_long");
            return new(200, "", transcript);
        }
        catch (OperationCanceledException) when (!cancellation.IsCancellationRequested) { return new(504, "openai_timeout"); }
        catch (HttpRequestException) { return new(502, "openai_unreachable"); }
        catch (JsonException) { return new(502, "invalid_openai_response"); }
        finally { _gate.Release(); }
    }
}

public sealed record SpeechResult(int Status, string Error, string Text = "", int? UpstreamStatus = null);
