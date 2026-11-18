using System.Net;
using LeHoangCloudServer.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;

int checks = 0;
void Check(bool value, string message) { if (!value) throw new Exception(message); checks++; }
GreetingLanAccess Access(bool enabled) => new(new ConfigurationBuilder().AddInMemoryCollection(
    new Dictionary<string, string?> { ["GREETING_LAN_AUTO_PAIRING"] = enabled ? "true" : "false" }).Build());
DefaultHttpContext Context(string remote = "127.0.0.1", string local = "127.0.0.1", string host = "127.0.0.1:5050")
{
    var context = new DefaultHttpContext();
    context.Connection.RemoteIpAddress = IPAddress.Parse(remote); context.Connection.LocalIpAddress = IPAddress.Parse(local);
    context.Request.Host = new HostString(host); context.Request.Scheme = "http"; return context;
}
var access = Access(true);
Check(!Access(false).Allows(Context()), "Opt-in is required");
Check(access.Allows(Context()), "Direct loopback accepted for local server tools");
Check(access.Allows(Context("::ffff:127.0.0.1", "::ffff:127.0.0.1")), "Mapped IPv4 normalized");
Check(!access.Allows(Context("203.0.113.8", "203.0.113.9", "203.0.113.9:5050")), "Public subnet never grants automatic access");
Check(!access.Allows(Context("8.8.8.8")), "External caller rejected");
var forwarded = Context("8.8.8.8"); forwarded.Request.Headers["X-Forwarded-For"] = "127.0.0.1";
Check(!access.Allows(forwarded), "Spoofed forwarded address is not trusted");
Check(!access.Allows(Context(host:"attacker.example:5050")), "DNS rebinding rejected");
Check(!access.Allows(Context(host:"192.168.0.123:5050")), "Host must match the actual server interface");
foreach (var origin in new[] { "null", "https://attacker.example", "http://127.0.0.1:5051", "http://127.0.0.1:5050.evil.example" })
{
    var context = Context(); context.Request.Headers.Origin = origin;
    Check(!access.Allows(context), "Foreign/malformed origin rejected");
}
var sameOrigin = Context(); sameOrigin.Request.Headers.Origin = "http://127.0.0.1:5050";
Check(access.Allows(sameOrigin), "Same-origin Web accepted");
var foreignReferer = Context(); foreignReferer.Request.Headers.Referer = "https://attacker.example/";
Check(!access.Allows(foreignReferer), "Foreign referer rejected even without Origin");
Console.WriteLine($"PASS: {checks} LAN pairing access checks");
