using System.ComponentModel.DataAnnotations;

namespace LeHoangCloudServer.Models;

public class GreetingSettings
{
    [Range(0.1, 0.7)] public double LinearSpeed { get; set; } = 0.3;
    [Range(0.2, 1.8)] public double AngularSpeed { get; set; } = 0.7;
    [Range(0.05, 0.5)] public double ArrivalTolerance { get; set; } = 0.2;
    [Range(5000, 120000)] public int ObstacleTimeoutMs { get; set; } = 30000;
    [Range(2, 15)] public int MicrophoneSeconds { get; set; } = 5;
    [Range(0, 100)] public int PlaybackVolume { get; set; } = 60;
    [Range(-90.0, 90.0)] public double VoiceAngleOffset { get; set; } = 25;
}

public class GreetingRegistration
{
    [Required, RegularExpression("^[A-Za-z0-9_-]{1,64}$")] public string Id { get; set; } = "GREETING-01";
    [Required, StringLength(100)] public string Name { get; set; } = "Greetingbot";
}

public class GreetingPlace
{
    [Required, StringLength(200)] public string Name { get; set; } = "";
    public double X { get; set; }
    public double Y { get; set; }
    public double Theta { get; set; }
    public int Status { get; set; }
}

public class GreetingSnapshot
{
    public bool SdkConnected { get; set; }
    public bool Authorized { get; set; }
    public bool Foreground { get; set; }
    public bool Suspended { get; set; }
    public bool Localized { get; set; }
    [StringLength(200)] public string MapName { get; set; } = "";
    [MaxLength(1000)] public List<GreetingPlace> Places { get; set; } = new();
    [StringLength(32)] public string NavigationState { get; set; } = "idle";
    [StringLength(200)] public string Target { get; set; } = "";
    [StringLength(32)] public string MicrophoneState { get; set; } = "idle";
    [StringLength(500)] public string Message { get; set; } = "";
    [StringLength(100)] public string SystemVersion { get; set; } = "";
    public bool Ready => SdkConnected && Authorized && Foreground && !Suspended;
}

public class GreetingCommandRequest
{
    [Required, RegularExpression("^(refresh_places|navigate|stop_navigation|microphone_test)$")]
    public string Type { get; set; } = "";
    [StringLength(200)] public string PlaceName { get; set; } = "";
    [StringLength(200)] public string MapName { get; set; } = "";
}

public class GreetingCommand : GreetingCommandRequest
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Status { get; set; } = "queued";
    public string Message { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset ExpiresAt { get; set; } = DateTimeOffset.UtcNow.AddSeconds(15);
}

public class GreetingCommandResult
{
    [Required, StringLength(32)] public string Id { get; set; } = "";
    [RegularExpression("^(running|completed|failed)$")] public string Status { get; set; } = "failed";
    [StringLength(500)] public string Message { get; set; } = "";
}

public class GreetingSyncRequest
{
    [Required] public GreetingSnapshot Snapshot { get; set; } = new();
    [MaxLength(50)] public List<GreetingCommandResult> Results { get; set; } = new();
}

public class GreetingRobot
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Model => "OrionStar Greetingbot";
    public GreetingSettings Settings { get; set; } = new();
    public GreetingSnapshot Snapshot { get; set; } = new();
    public DateTimeOffset? LastSeen { get; set; }
    public bool Online => LastSeen.HasValue && DateTimeOffset.UtcNow - LastSeen.Value < TimeSpan.FromSeconds(10);
    public List<GreetingCommand> Commands { get; set; } = new();
}
