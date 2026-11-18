using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using LeHoangCloudServer.Models;
using Microsoft.Data.Sqlite;

namespace LeHoangCloudServer.Services;

// Shares the existing SQLite database, with independent tables for Greetingbot.
public sealed class GreetingService
{
    private readonly string _connectionString;
    private readonly object _gate = new();
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public GreetingService(CloudDatabaseService existingDatabase)
    {
        var path = Environment.GetEnvironmentVariable("DATABASE_PATH");
        if (string.IsNullOrWhiteSpace(path)) path = Path.Combine(AppContext.BaseDirectory, "cloud_fleet.db");
        _connectionString = new SqliteConnectionStringBuilder { DataSource = path }.ToString();
        using var db = Open();
        using var cmd = db.CreateCommand();
        cmd.CommandText = """
            CREATE TABLE IF NOT EXISTS GreetingRobots (
                Id TEXT PRIMARY KEY, Name TEXT NOT NULL, TokenHash TEXT NOT NULL,
                Settings TEXT NOT NULL, Snapshot TEXT NOT NULL, LastSeen TEXT);
            CREATE TABLE IF NOT EXISTS GreetingCommands (
                Id TEXT PRIMARY KEY, RobotId TEXT NOT NULL, Payload TEXT NOT NULL);
            CREATE INDEX IF NOT EXISTS IX_GreetingCommands_RobotId ON GreetingCommands(RobotId);
            """;
        cmd.ExecuteNonQuery();
    }

    private SqliteConnection Open() { var db = new SqliteConnection(_connectionString); db.Open(); return db; }
    private static string Hash(string token) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
    private static string Serialize<T>(T value) => JsonSerializer.Serialize(value, Json);
    private static T Deserialize<T>(string value) => JsonSerializer.Deserialize<T>(value, Json)!;

    public bool Authenticate(string id, string token)
    {
        if (string.IsNullOrEmpty(token) || token.Length > 256) return false;
        using var db = Open();
        using var cmd = db.CreateCommand();
        cmd.CommandText = "SELECT TokenHash FROM GreetingRobots WHERE Id=$id";
        cmd.Parameters.AddWithValue("$id", id);
        var stored = cmd.ExecuteScalar() as string;
        return stored != null && CryptographicOperations.FixedTimeEquals(
            Encoding.ASCII.GetBytes(stored), Encoding.ASCII.GetBytes(Hash(token)));
    }

    public (GreetingRobot Robot, string Token) Register(GreetingRegistration registration)
    {
        lock (_gate)
        {
            using var db = Open();
            using var cmd = db.CreateCommand();
            var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(24)).ToLowerInvariant();
            cmd.CommandText = "INSERT INTO GreetingRobots VALUES ($id,$name,$hash,$settings,$snapshot,NULL)";
            cmd.Parameters.AddWithValue("$id", registration.Id);
            cmd.Parameters.AddWithValue("$name", registration.Name);
            cmd.Parameters.AddWithValue("$hash", Hash(token));
            cmd.Parameters.AddWithValue("$settings", Serialize(new GreetingSettings()));
            cmd.Parameters.AddWithValue("$snapshot", Serialize(new GreetingSnapshot()));
            cmd.ExecuteNonQuery();
            return (new GreetingRobot { Id = registration.Id, Name = registration.Name }, token);
        }
    }

    public List<GreetingRobot> List()
    {
        lock (_gate)
        {
            using var db = Open();
            using var cmd = db.CreateCommand();
            cmd.CommandText = "SELECT Id,Name,Settings,Snapshot,LastSeen FROM GreetingRobots ORDER BY Name";
            using var reader = cmd.ExecuteReader();
            var result = new List<GreetingRobot>();
            while (reader.Read()) result.Add(new GreetingRobot {
                Id = reader.GetString(0), Name = reader.GetString(1),
                Settings = Deserialize<GreetingSettings>(reader.GetString(2)),
                Snapshot = Deserialize<GreetingSnapshot>(reader.GetString(3)),
                LastSeen = reader.IsDBNull(4) ? null : DateTimeOffset.Parse(reader.GetString(4))
            });
            return result;
        }
    }

    public GreetingRobot? Get(string id)
    {
        lock (_gate)
        {
            var robot = List().Find(r => r.Id == id);
            if (robot != null) robot.Commands = Commands(id).OrderByDescending(c => c.CreatedAt).Take(20).ToList();
            return robot;
        }
    }

    public bool SaveSettings(string id, GreetingSettings settings)
    {
        lock (_gate)
        {
            using var db = Open();
            using var cmd = db.CreateCommand();
            cmd.CommandText = "UPDATE GreetingRobots SET Settings=$settings WHERE Id=$id";
            cmd.Parameters.AddWithValue("$settings", Serialize(settings));
            cmd.Parameters.AddWithValue("$id", id);
            return cmd.ExecuteNonQuery() == 1;
        }
    }

    private List<GreetingCommand> Commands(string id)
    {
        using var db = Open();
        using var cmd = db.CreateCommand();
        cmd.CommandText = "SELECT Payload FROM GreetingCommands WHERE RobotId=$id";
        cmd.Parameters.AddWithValue("$id", id);
        using var reader = cmd.ExecuteReader();
        var commands = new List<GreetingCommand>();
        while (reader.Read())
        {
            var c = Deserialize<GreetingCommand>(reader.GetString(0));
            if (c.Status == "queued" && c.ExpiresAt < DateTimeOffset.UtcNow) { c.Status = "expired"; c.Message = "Lệnh đã hết hạn, không thực thi."; }
            if (c.Status == "delivered" && c.ExpiresAt < DateTimeOffset.UtcNow) { c.Status = "unconfirmed"; c.Message = "Chưa nhận được xác nhận từ robot. Kiểm tra trạng thái thực tế; lệnh không được phát lại."; }
            commands.Add(c);
        }
        return commands;
    }

    private void SaveCommand(string robotId, GreetingCommand command)
    {
        using var db = Open();
        using var cmd = db.CreateCommand();
        cmd.CommandText = "INSERT OR REPLACE INTO GreetingCommands VALUES ($id,$robot,$payload)";
        cmd.Parameters.AddWithValue("$id", command.Id);
        cmd.Parameters.AddWithValue("$robot", robotId);
        cmd.Parameters.AddWithValue("$payload", Serialize(command));
        cmd.ExecuteNonQuery();
    }

    public GreetingCommand Enqueue(string id, GreetingCommandRequest request)
    {
        lock (_gate)
        {
            var robot = Get(id) ?? throw new KeyNotFoundException("Không tìm thấy Greetingbot.");
            if (!robot.Online) throw new InvalidOperationException("Robot đang ngoại tuyến; lệnh không được lưu để chạy lại khi kết nối.");
            if (!robot.Snapshot.Ready) throw new InvalidOperationException("Mở ứng dụng Greetingbot từ Home của RobotOS và kiểm tra quyền SDK.");
            if (request.Type == "navigate")
            {
                if (!robot.Snapshot.Localized) throw new InvalidOperationException("Robot chưa định vị trên bản đồ.");
                if (request.MapName != robot.Snapshot.MapName || string.IsNullOrWhiteSpace(request.MapName))
                    throw new InvalidOperationException("Bản đồ đã thay đổi. Hãy tải lại điểm.");
                if (!robot.Snapshot.Places.Any(p => p.Name == request.PlaceName && p.Status == 0))
                    throw new InvalidOperationException("Điểm không tồn tại hoặc không thể tới trên bản đồ hiện tại.");
                if (robot.Snapshot.NavigationState is "moving" or "checking" or "stopping" ||
                    robot.Commands.Any(c => c.Type == "navigate" && c.Status is "queued" or "delivered"))
                    throw new InvalidOperationException("Đã có lệnh điều hướng. Dừng trước khi chọn điểm khác.");
            }
            if (request.Type == "microphone_test" && robot.Snapshot.MicrophoneState != "idle")
                throw new InvalidOperationException("Microphone hoặc loa đang được sử dụng.");
            if (request.Type == "stop_navigation")
            {
                // A stop must also cancel navigation that has not yet reached the robot.
                foreach (var pending in Commands(id).Where(c => c.Type == "navigate" && c.Status == "queued"))
                { pending.Status = "failed"; pending.Message = "Đã hủy bởi lệnh dừng."; SaveCommand(id, pending); }
            }
            var command = new GreetingCommand { Type = request.Type, PlaceName = request.PlaceName, MapName = request.MapName };
            SaveCommand(id, command);
            return command;
        }
    }

    public object Sync(string id, GreetingSyncRequest request)
    {
        lock (_gate)
        {
            var robot = Get(id) ?? throw new KeyNotFoundException();
            var commands = Commands(id);
            foreach (var result in request.Results)
            {
                var command = commands.Find(c => c.Id == result.Id);
                if (command == null || command.Status is "completed" or "failed" or "expired" || command.Status == "queued") continue;
                command.Status = result.Status;
                command.Message = result.Message;
                SaveCommand(id, command);
            }
            using (var db = Open())
            using (var cmd = db.CreateCommand())
            {
                cmd.CommandText = "UPDATE GreetingRobots SET Snapshot=$snapshot,LastSeen=$seen WHERE Id=$id";
                cmd.Parameters.AddWithValue("$snapshot", Serialize(request.Snapshot));
                cmd.Parameters.AddWithValue("$seen", DateTimeOffset.UtcNow.ToString("O"));
                cmd.Parameters.AddWithValue("$id", id);
                cmd.ExecuteNonQuery();
            }
            // Deliver once. A lost response never causes an old movement to be replayed.
            var next = commands.Where(c => c.Status == "queued")
                .OrderBy(c => c.Type == "stop_navigation" ? 0 : 1).ThenBy(c => c.CreatedAt).FirstOrDefault();
            if (next != null) { next.Status = "delivered"; SaveCommand(id, next); }
            // Bound durable command history per robot.
            foreach (var old in commands.OrderByDescending(c => c.CreatedAt).Skip(100))
            {
                using var db = Open(); using var cmd = db.CreateCommand();
                cmd.CommandText = "DELETE FROM GreetingCommands WHERE Id=$id";
                cmd.Parameters.AddWithValue("$id", old.Id); cmd.ExecuteNonQuery();
            }
            return new { settings = robot.Settings, command = next, serverTime = DateTimeOffset.UtcNow };
        }
    }
}
