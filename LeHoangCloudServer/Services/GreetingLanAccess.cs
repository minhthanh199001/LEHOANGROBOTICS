using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace LeHoangCloudServer.Services;

// Explicit LAN installation mode; remote/cloud deployments keep key authentication.
public sealed class GreetingLanAccess(IConfiguration configuration)
{
    public bool Enabled => string.Equals(configuration["GREETING_LAN_AUTO_PAIRING"], "true", StringComparison.OrdinalIgnoreCase);

    public bool Allows(HttpContext context)
    {
        if (!Enabled) return false;
        var remote = Normalize(context.Connection.RemoteIpAddress);
        var local = Normalize(context.Connection.LocalIpAddress);
        if (remote == null || local == null ||
            !IPAddress.TryParse(context.Request.Host.Host, out var host) || !Normalize(host)!.Equals(local)) return false;
        // Do not turn the existing permissive Fleet CORS policy into cross-site robot control.
        foreach (var header in new[] { "Origin", "Referer" })
        {
            var value = context.Request.Headers[header].ToString();
            if (value.Length == 0) continue;
            if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
                !string.Equals(uri.GetLeftPart(UriPartial.Authority),
                    context.Request.Scheme + "://" + context.Request.Host, StringComparison.OrdinalIgnoreCase)) return false;
        }
        if (IPAddress.IsLoopback(remote) && IPAddress.IsLoopback(local)) return true;
        if (remote.AddressFamily != AddressFamily.InterNetwork || local.AddressFamily != AddressFamily.InterNetwork) return false;
        if (!PrivateIPv4(remote) || !PrivateIPv4(local)) return false;
        return NetworkInterface.GetAllNetworkInterfaces()
            .Where(adapter => adapter.OperationalStatus == OperationalStatus.Up)
            .SelectMany(adapter => adapter.GetIPProperties().UnicastAddresses)
            .Any(address => address.Address.Equals(local) && SameSubnet(remote, local, address.IPv4Mask));
    }
    private static bool PrivateIPv4(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        return bytes[0] == 10 || (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) || (bytes[0] == 192 && bytes[1] == 168);
    }

    public static bool SameSubnet(IPAddress remote, IPAddress local, IPAddress mask)
    {
        var a = remote.GetAddressBytes(); var b = local.GetAddressBytes(); var m = mask.GetAddressBytes();
        return a.Length == 4 && b.Length == 4 && m.Length == 4 &&
            Enumerable.Range(0, 4).All(i => (a[i] & m[i]) == (b[i] & m[i]));
    }
    private static IPAddress? Normalize(IPAddress? address) => address?.IsIPv4MappedToIPv6 == true ? address.MapToIPv4() : address;
}
