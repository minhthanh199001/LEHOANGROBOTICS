using System.Buffers.Binary;
using System.Net;
using System.Text;
using System.Text.Json;
using LeHoangCloudServer.Services;
using Microsoft.Extensions.Configuration;

int checks = 0;
void Check(bool value, string name) { if (!value) throw new Exception(name); checks++; }
string key = "sk-" + new string('x', 48), token = new string('a', 64);
var configuration = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?> {
    ["OPENAI_API_KEY"] = key, ["GREETING_SPEECH_TOKEN"] = token
}).Build();
byte[] wave = new byte[16044];
Encoding.ASCII.GetBytes("RIFF").CopyTo(wave, 0); Encoding.ASCII.GetBytes("WAVEfmt ").CopyTo(wave, 8);
Encoding.ASCII.GetBytes("data").CopyTo(wave, 36);
BinaryPrimitives.WriteUInt32LittleEndian(wave.AsSpan(4), (uint)wave.Length - 8);
BinaryPrimitives.WriteUInt32LittleEndian(wave.AsSpan(16), 16);
BinaryPrimitives.WriteUInt16LittleEndian(wave.AsSpan(20), 1); BinaryPrimitives.WriteUInt16LittleEndian(wave.AsSpan(22), 1);
BinaryPrimitives.WriteUInt32LittleEndian(wave.AsSpan(24), 16000); BinaryPrimitives.WriteUInt32LittleEndian(wave.AsSpan(28), 32000);
BinaryPrimitives.WriteUInt16LittleEndian(wave.AsSpan(32), 2); BinaryPrimitives.WriteUInt16LittleEndian(wave.AsSpan(34), 16);
BinaryPrimitives.WriteUInt32LittleEndian(wave.AsSpan(40), (uint)wave.Length - 44);
Check(GreetingSpeechService.ValidWave(wave), "Orion mono PCM WAV accepted");
var stereo = (byte[])wave.Clone(); stereo[22] = 2;
Check(!GreetingSpeechService.ValidWave(stereo), "Reject mixed stereo data");
var wrongLength = (byte[])wave.Clone(); wrongLength[40]++;
Check(!GreetingSpeechService.ValidWave(wrongLength), "Reject inconsistent data length");
Check(!GreetingSpeechService.ValidWave(new byte[GreetingSpeechService.MaximumWaveBytes + 1]), "Bound request size");
Check(!GreetingSpeechService.ValidWave(wave.AsSpan(0, 1000)), "Reject too-short audio");

var entered = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
int calls = 0;
var handler = new FakeHandler(async (request, cancellation) => {
    calls++;
    Check(request.RequestUri?.AbsoluteUri == "https://api.openai.com/v1/audio/transcriptions", "Use official audio endpoint");
    Check(request.Headers.Authorization?.Parameter == key, "Authenticate only to upstream");
    var multipart = (MultipartFormDataContent)request.Content!;
    var parts = new Dictionary<string, List<string>>();
    foreach (var part in multipart) {
        string name = part.Headers.ContentDisposition!.Name!.Trim('"');
        if (name == "file") { Check((await part.ReadAsByteArrayAsync(cancellation)).SequenceEqual(wave), "Preserve uploaded audio"); continue; }
        if (!parts.ContainsKey(name)) parts[name] = [];
        parts[name].Add(await part.ReadAsStringAsync(cancellation));
    }
    Check(parts["model"].Single() == "gpt-transcribe", "Current transcription model");
    Check(parts["languages[]"].SequenceEqual(new[] { "en", "vi" }), "Bilingual hints");
    Check(parts["keywords[]"].SequenceEqual(new[] { "Hello robot", "robot ơi" }), "Literal keyword hints");
    entered.TrySetResult(); await release.Task.WaitAsync(cancellation);
    return new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("{\"text\":\"robot ơi\"}", Encoding.UTF8, "application/json") };
});
using var client = new HttpClient(handler);
var speech = new GreetingSpeechService(client, configuration);
Check(speech.Ready, "Configured service ready");
Check(!speech.Authorized(null) && !speech.Authorized("Bearer wrong") && !speech.Authorized(token), "Reject missing and wrong credentials");
Check(speech.Authorized("Bearer " + token), "Accept scoped speech token");
var first = speech.TranscribeAsync(wave, CancellationToken.None);
await entered.Task;
Check((await speech.TranscribeAsync(wave, CancellationToken.None)).Status == 429 && calls == 1, "No queued or duplicated upstream requests");
release.SetResult();
var recognized = await first;
Check(recognized.Status == 200 && recognized.Text == "robot ơi", "Return actual provider transcript");
Check(!JsonSerializer.Serialize(recognized).Contains(key), "Do not expose API credentials");
Check((await speech.TranscribeAsync(stereo, CancellationToken.None)).Status == 400 && calls == 1, "Invalid WAV never reaches OpenAI");

using var badClient = new HttpClient(new FakeHandler((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.Unauthorized) { Content = new StringContent(key) })));
var bad = await new GreetingSpeechService(badClient, configuration).TranscribeAsync(wave, CancellationToken.None);
Check(bad.Status == 502 && bad.UpstreamStatus == 401 && !JsonSerializer.Serialize(bad).Contains(key), "Sanitize upstream failures");
var unconfigured = new GreetingSpeechService(client, new ConfigurationBuilder().Build());
Check(!unconfigured.Ready && !unconfigured.Authorized("Bearer " + token), "Unconfigured service closed");
Check((await unconfigured.TranscribeAsync(wave, CancellationToken.None)).Status == 503, "Require server configuration");
string invalidKeyFile = Path.GetTempFileName();
try {
    File.WriteAllText(invalidKeyFile, new string('x', 4097));
    var fileConfig = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?> {
        ["OPENAI_API_KEY"] = key, ["OPENAI_API_KEY_FILE"] = invalidKeyFile, ["GREETING_SPEECH_TOKEN"] = token
    }).Build();
    Check(!new GreetingSpeechService(client, fileConfig).Ready, "Oversized specified key file cannot fall back to another environment key");
    File.Delete(invalidKeyFile);
    Check(!new GreetingSpeechService(client, fileConfig).Ready, "Missing specified key file cannot fall back to another environment key");
} finally { if (File.Exists(invalidKeyFile)) File.Delete(invalidKeyFile); }
Console.WriteLine($"Greeting speech: {checks} checks passed. No real audio or API key used.");

sealed class FakeHandler(Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> send) : HttpMessageHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => send(request, cancellationToken);
}
