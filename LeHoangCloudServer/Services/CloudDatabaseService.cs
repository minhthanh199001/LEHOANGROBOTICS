using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using LeHoangCloudServer.Models;
using Microsoft.Data.Sqlite;

namespace LeHoangCloudServer.Services;

public class CloudDatabaseService
{
    private readonly string _connectionString;
    // In-memory cache for ultra-fast 60fps real-time robot telemetry queries
    private readonly ConcurrentDictionary<string, CloudRobotItem> _robotLiveCache = new();
    private readonly ConcurrentQueue<FleetAlertItem> _recentAlerts = new();

    public CloudDatabaseService()
    {
        string? envDbPath = Environment.GetEnvironmentVariable("DATABASE_PATH");
        string dbPath = !string.IsNullOrWhiteSpace(envDbPath) ? envDbPath : Path.Combine(AppContext.BaseDirectory, "cloud_fleet.db");
        var parentDir = Path.GetDirectoryName(dbPath);
        if (!string.IsNullOrWhiteSpace(parentDir) && !Directory.Exists(parentDir))
        {
            Directory.CreateDirectory(parentDir);
        }
        _connectionString = $"Data Source={dbPath}";
        InitializeDatabase();
        LoadInitialCache();
    }

    private void InitializeDatabase()
    {
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();

        string ddl = @"
            CREATE TABLE IF NOT EXISTS Organizations (
                Id TEXT PRIMARY KEY,
                Name TEXT NOT NULL,
                Code TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS Sites (
                Id TEXT PRIMARY KEY,
                OrganizationId TEXT NOT NULL,
                Name TEXT NOT NULL,
                Code TEXT NOT NULL,
                Address TEXT,
                Phone TEXT,
                FOREIGN KEY (OrganizationId) REFERENCES Organizations(Id)
            );

            CREATE TABLE IF NOT EXISTS Robots (
                Id TEXT PRIMARY KEY,
                SiteId TEXT NOT NULL,
                Name TEXT NOT NULL,
                Model TEXT DEFAULT 'OrionStar Mini',
                Ip TEXT DEFAULT '192.168.0.125',
                Port INTEGER DEFAULT 8899,
                Status TEXT DEFAULT 'Offline',
                Battery INTEGER DEFAULT 100,
                IsCharging INTEGER DEFAULT 0,
                AssignedMap TEXT DEFAULT 'tang 2-1110154527',
                PoseX REAL DEFAULT 0,
                PoseY REAL DEFAULT 0,
                PoseAngle REAL DEFAULT 0,
                LastSeen TEXT,
                FOREIGN KEY (SiteId) REFERENCES Sites(Id)
            );

            CREATE TABLE IF NOT EXISTS FleetAlerts (
                Id TEXT PRIMARY KEY,
                RobotId TEXT,
                AlertType TEXT,
                Message TEXT,
                Timestamp TEXT
            );

            CREATE TABLE IF NOT EXISTS UserAccounts (
                Id TEXT PRIMARY KEY,
                Username TEXT UNIQUE NOT NULL,
                Password TEXT NOT NULL,
                FullName TEXT NOT NULL,
                Email TEXT,
                Role TEXT NOT NULL,
                OrganizationId TEXT,
                SiteId TEXT,
                IsActive INTEGER NOT NULL DEFAULT 1,
                CreatedAt TEXT NOT NULL
            );
        ";

        using var cmd = new SqliteCommand(ddl, conn);
        cmd.ExecuteNonQuery();

        // Seed initial enterprise data if empty
        using var countCmd = new SqliteCommand("SELECT COUNT(*) FROM Organizations;", conn);
        long count = (long)(countCmd.ExecuteScalar() ?? 0);
        if (count == 0)
        {
            SeedInitialData(conn);
        }

        // Seed users if empty
        using var userCountCmd = new SqliteCommand("SELECT COUNT(*) FROM UserAccounts;", conn);
        long userCount = (long)(userCountCmd.ExecuteScalar() ?? 0);
        if (userCount == 0)
        {
            SeedUsers(conn);
        }
    }

    private void SeedUsers(SqliteConnection conn)
    {
        string desktopDbPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "LeHoangControlCenter", "bin", "Debug", "net8.0-windows", "lehoang_fleet.db");
        if (File.Exists(desktopDbPath))
        {
            try
            {
                using var dtConn = new SqliteConnection($"Data Source={desktopDbPath}");
                dtConn.Open();
                using var dtCmd = new SqliteCommand("SELECT Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt FROM UserAccounts", dtConn);
                using var reader = dtCmd.ExecuteReader();
                while (reader.Read())
                {
                    using var insCmd = new SqliteCommand(@"
                        INSERT OR REPLACE INTO UserAccounts (Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt)
                        VALUES (@Id, @Username, @Password, @FullName, @Email, @Role, @OrgId, @SiteId, @IsActive, @CreatedAt)", conn);
                    insCmd.Parameters.AddWithValue("@Id", reader.GetString(0));
                    insCmd.Parameters.AddWithValue("@Username", reader.GetString(1));
                    insCmd.Parameters.AddWithValue("@Password", reader.GetString(2));
                    insCmd.Parameters.AddWithValue("@FullName", reader.GetString(3));
                    insCmd.Parameters.AddWithValue("@Email", reader.IsDBNull(4) ? "" : reader.GetString(4));
                    insCmd.Parameters.AddWithValue("@Role", reader.GetString(5));
                    insCmd.Parameters.AddWithValue("@OrgId", reader.IsDBNull(6) ? DBNull.Value : reader.GetString(6));
                    insCmd.Parameters.AddWithValue("@SiteId", reader.IsDBNull(7) ? DBNull.Value : reader.GetString(7));
                    insCmd.Parameters.AddWithValue("@IsActive", reader.GetInt32(8));
                    insCmd.Parameters.AddWithValue("@CreatedAt", reader.GetString(9));
                    insCmd.ExecuteNonQuery();
                }
            }
            catch { }
        }

        string seedUserSql = @"
            INSERT OR IGNORE INTO UserAccounts (Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt) VALUES
            ('USR-01', 'admin', '123456', 'Quản trị viên LH', 'admin@lehoangrobotics.vn', 'SuperAdmin', NULL, NULL, 1, datetime('now')),
            ('USR-02', 'vinpearl_admin', '123456', 'Ban Quản trị Vinpearl', 'admin@vinpearl.com', 'OrgAdmin', 'VINPEARL', NULL, 1, datetime('now')),
            ('USR-03', 'vlm81_manager', '123456', 'Quản lý Landmark 81', 'manager.lm81@vinpearl.com', 'SiteManager', 'VINPEARL', 'VIN-LANDMARK', 1, datetime('now')),
            ('USR-04', 'operator', '123456', 'Vận hành viên Chuỗi', 'operator@lehoangrobotics.vn', 'Operator', NULL, NULL, 1, datetime('now'));
        ";
        using var seedCmd = new SqliteCommand(seedUserSql, conn);
        seedCmd.ExecuteNonQuery();
    }

    private void SeedInitialData(SqliteConnection conn)
    {
        string seedSql = @"
            INSERT INTO Organizations (Id, Name, Code) VALUES 
            ('LEHOANG', 'Lê Hoàng Robotics Corp', 'LEHOANG'),
            ('MUONGTHANH', 'Khách sạn Mường Thanh', 'MT-HOSP'),
            ('VINPEARL', 'Tập đoàn Vinpearl', 'VINPEARL');

            INSERT INTO Sites (Id, OrganizationId, Name, Code, Address, Phone) VALUES 
            ('RND-Q1', 'LEHOANG', 'Trung tâm R&D & Trụ sở chính', 'RND-Q1', 'Số 12 Đường Lê Hoàng, TP. HCM', '0901234567'),
            ('MT-SG', 'MUONGTHANH', 'Mường Thanh Luxury Sài Gòn', 'MT-SG', '261 Nguyễn Văn Trỗi, Phú Nhuận, TP. HCM', '0987654321'),
            ('VIN-LANDMARK', 'VINPEARL', 'Vinpearl Landmark 81 Luxury', 'VIN-81', '720A Điện Biên Phủ, Bình Thạnh, TP. HCM', '0912345678');

            INSERT INTO Robots (Id, SiteId, Name, Model, Ip, Port, Status, Battery, IsCharging, AssignedMap, PoseX, PoseY, PoseAngle, LastSeen) VALUES
            ('ROB-01', 'RND-Q1', 'Lê Hoàng 01 (Trụ Sở)', 'OrionStar Mini', '192.168.0.125', 8899, 'Online', 95, 0, 'tang 2-1110154527', 2.80, 2.86, 1.57, datetime('now')),
            ('ROB-02', 'MT-SG', 'Mường Thanh Bot Phục Vụ 01', 'OrionStar Mini', '192.168.2.120', 8899, 'Online', 65, 0, 'tang 2-1110154527', 3.50, 4.20, 0.0, datetime('now')),
            ('ROB-03', 'VIN-LANDMARK', 'Vinpearl Service Bot 01', 'OrionStar Mini', '192.168.10.45', 8899, 'Charging', 88, 1, 'sanh_chinh', 0.0, 0.0, 0.0, datetime('now'));

            INSERT INTO FleetAlerts (Id, RobotId, AlertType, Message, Timestamp) VALUES
            ('ALT-01', 'ROB-01', 'SUCCESS', 'Robot kết nối thành công vào Cloud Gateway', datetime('now')),
            ('ALT-02', 'ROB-03', 'INFO', 'Robot đang sạc tại Trạm sạc tự động (Charging Pole)', datetime('now'));

            INSERT OR IGNORE INTO UserAccounts (Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt) VALUES
            ('USR-01', 'admin', '123456', 'Quản trị viên LH', 'admin@lehoangrobotics.vn', 'SuperAdmin', NULL, NULL, 1, datetime('now')),
            ('USR-02', 'vinpearl_admin', '123456', 'Ban Quản trị Vinpearl', 'admin@vinpearl.com', 'OrgAdmin', 'VINPEARL', NULL, 1, datetime('now')),
            ('USR-03', 'vlm81_manager', '123456', 'Quản lý Landmark 81', 'manager.lm81@vinpearl.com', 'SiteManager', 'VINPEARL', 'VIN-LANDMARK', 1, datetime('now')),
            ('USR-04', 'operator', '123456', 'Vận hành viên Chuỗi', 'operator@lehoangrobotics.vn', 'Operator', NULL, NULL, 1, datetime('now'));
        ";

        using var seedCmd = new SqliteCommand(seedSql, conn);
        seedCmd.ExecuteNonQuery();
    }

    private void LoadInitialCache()
    {
        var robots = GetAllRobotsFromDb();
        foreach (var r in robots)
        {
            _robotLiveCache[r.Id] = r;
        }

        // Add some default alerts
        _recentAlerts.Enqueue(new FleetAlertItem { RobotId = "ROB-01", RobotName = "Lê Hoàng 01", SiteName = "Trụ sở R&D", AlertType = "SUCCESS", Message = "Hệ thống Cloud trung tâm khởi động thành công", Timestamp = DateTime.UtcNow });
    }

    public List<CloudOrganization> GetOrganizations()
    {
        var list = new List<CloudOrganization>();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var cmd = new SqliteCommand("SELECT Id, Name, Code FROM Organizations ORDER BY Name;", conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new CloudOrganization
            {
                Id = reader.GetString(0),
                Name = reader.GetString(1),
                Code = reader.GetString(2)
            });
        }
        return list;
    }

    public void SaveOrganization(CloudOrganization org)
    {
        if (string.IsNullOrWhiteSpace(org.Id)) org.Id = "ORG-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var cmd = new SqliteCommand(@"
            INSERT INTO Organizations (Id, Name, Code)
            VALUES (@Id, @Name, @Code)
            ON CONFLICT(Id) DO UPDATE SET Name = @Name, Code = @Code;", conn);
        cmd.Parameters.AddWithValue("@Id", org.Id);
        cmd.Parameters.AddWithValue("@Name", org.Name);
        cmd.Parameters.AddWithValue("@Code", org.Code);
        cmd.ExecuteNonQuery();
    }

    public void DeleteOrganization(string id)
    {
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var tran = conn.BeginTransaction();
        try
        {
            // 1. Xoá tất cả robots thuộc các sites của tổ chức này
            using var delRobots = new SqliteCommand(@"
                DELETE FROM Robots WHERE SiteId IN (SELECT Id FROM Sites WHERE OrganizationId = @Id);", conn, tran);
            delRobots.Parameters.AddWithValue("@Id", id);
            delRobots.ExecuteNonQuery();

            // 2. Hủy gán người dùng khỏi tổ chức & site này
            using var unassignUsers = new SqliteCommand(@"
                UPDATE UserAccounts SET OrganizationId = NULL, SiteId = NULL WHERE OrganizationId = @Id;", conn, tran);
            unassignUsers.Parameters.AddWithValue("@Id", id);
            unassignUsers.ExecuteNonQuery();

            // 3. Xoá các chi nhánh (Sites) thuộc tổ chức
            using var delSites = new SqliteCommand("DELETE FROM Sites WHERE OrganizationId = @Id;", conn, tran);
            delSites.Parameters.AddWithValue("@Id", id);
            delSites.ExecuteNonQuery();

            // 4. Xoá tổ chức
            using var cmd = new SqliteCommand("DELETE FROM Organizations WHERE Id = @Id;", conn, tran);
            cmd.Parameters.AddWithValue("@Id", id);
            cmd.ExecuteNonQuery();

            tran.Commit();
        }
        catch
        {
            tran.Rollback();
            throw;
        }
    }

    public List<CloudSite> GetSites(string? orgId = null)
    {
        var list = new List<CloudSite>();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        string sql = string.IsNullOrEmpty(orgId)
            ? "SELECT Id, OrganizationId, Name, Code, Address, Phone FROM Sites ORDER BY Name;"
            : "SELECT Id, OrganizationId, Name, Code, Address, Phone FROM Sites WHERE OrganizationId = @orgId ORDER BY Name;";

        using var cmd = new SqliteCommand(sql, conn);
        if (!string.IsNullOrEmpty(orgId)) cmd.Parameters.AddWithValue("@orgId", orgId);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new CloudSite
            {
                Id = reader.GetString(0),
                OrganizationId = reader.GetString(1),
                Name = reader.GetString(2),
                Code = reader.GetString(3),
                Address = reader.IsDBNull(4) ? "" : reader.GetString(4),
                Phone = reader.IsDBNull(5) ? "" : reader.GetString(5)
            });
        }
        return list;
    }

    public void SaveSite(CloudSite site)
    {
        if (string.IsNullOrWhiteSpace(site.Id)) site.Id = "SITE-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var cmd = new SqliteCommand(@"
            INSERT INTO Sites (Id, OrganizationId, Name, Code, Address, Phone)
            VALUES (@Id, @OrgId, @Name, @Code, @Address, @Phone)
            ON CONFLICT(Id) DO UPDATE SET OrganizationId = @OrgId, Name = @Name, Code = @Code, Address = @Address, Phone = @Phone;", conn);
        cmd.Parameters.AddWithValue("@Id", site.Id);
        cmd.Parameters.AddWithValue("@OrgId", site.OrganizationId);
        cmd.Parameters.AddWithValue("@Name", site.Name);
        cmd.Parameters.AddWithValue("@Code", site.Code);
        cmd.Parameters.AddWithValue("@Address", site.Address ?? "");
        cmd.Parameters.AddWithValue("@Phone", site.Phone ?? "");
        cmd.ExecuteNonQuery();
    }

    public void DeleteSite(string id)
    {
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var tran = conn.BeginTransaction();
        try
        {
            // 1. Xoá robots thuộc chi nhánh này
            using var delRobots = new SqliteCommand("DELETE FROM Robots WHERE SiteId = @Id;", conn, tran);
            delRobots.Parameters.AddWithValue("@Id", id);
            delRobots.ExecuteNonQuery();

            // 2. Hủy gán người dùng khỏi chi nhánh này
            using var unassignUsers = new SqliteCommand("UPDATE UserAccounts SET SiteId = NULL WHERE SiteId = @Id;", conn, tran);
            unassignUsers.Parameters.AddWithValue("@Id", id);
            unassignUsers.ExecuteNonQuery();

            // 3. Xoá chi nhánh
            using var cmd = new SqliteCommand("DELETE FROM Sites WHERE Id = @Id;", conn, tran);
            cmd.Parameters.AddWithValue("@Id", id);
            cmd.ExecuteNonQuery();

            tran.Commit();
        }
        catch
        {
            tran.Rollback();
            throw;
        }
    }

    public void SaveRobot(CloudRobotItem robot)
    {
        if (string.IsNullOrWhiteSpace(robot.Id)) robot.Id = "ROB-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
        
        // Fill OrgName and SiteName if missing
        if (!string.IsNullOrEmpty(robot.SiteId))
        {
            var site = GetSites().FirstOrDefault(s => s.Id == robot.SiteId);
            if (site != null)
            {
                robot.SiteName = site.Name;
                robot.OrgId = site.OrganizationId;
                var org = GetOrganizations().FirstOrDefault(o => o.Id == site.OrganizationId);
                if (org != null) robot.OrgName = org.Name;
            }
        }

        // Dọn dẹp key trùng tên hoặc trùng IP trong live cache
        var duplicateKeys = _robotLiveCache.Where(kv => 
            !string.Equals(kv.Key, robot.Id, StringComparison.OrdinalIgnoreCase) && 
            (string.Equals(kv.Value.Name, robot.Name, StringComparison.OrdinalIgnoreCase) || 
             (!string.IsNullOrEmpty(robot.Ip) && string.Equals(kv.Value.Ip, robot.Ip, StringComparison.OrdinalIgnoreCase)))
        ).Select(kv => kv.Key).ToList();

        foreach (var k in duplicateKeys)
        {
            _robotLiveCache.TryRemove(k, out _);
        }

        robot.LastSeen = DateTime.UtcNow;
        _robotLiveCache[robot.Id] = robot;

        // Lưu / cập nhật trực tiếp vào cơ sở dữ liệu SQLite Robots
        try
        {
            using var conn = new SqliteConnection(_connectionString);
            conn.Open();
            string sql = @"
                INSERT INTO Robots (Id, SiteId, Name, Model, Ip, Port, Status, Battery, IsCharging, AssignedMap, PoseX, PoseY, PoseAngle, LastSeen)
                VALUES (@Id, @SiteId, @Name, @Model, @Ip, @Port, @Status, @Battery, @IsCharging, @AssignedMap, @PoseX, @PoseY, @PoseAngle, datetime('now'))
                ON CONFLICT(Id) DO UPDATE SET
                    SiteId = @SiteId,
                    Name = @Name,
                    Model = @Model,
                    Ip = @Ip,
                    Port = @Port,
                    Status = @Status,
                    Battery = @Battery,
                    IsCharging = @IsCharging,
                    AssignedMap = @AssignedMap,
                    LastSeen = datetime('now');
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@Id", robot.Id);
            cmd.Parameters.AddWithValue("@SiteId", robot.SiteId ?? "");
            cmd.Parameters.AddWithValue("@Name", robot.Name);
            cmd.Parameters.AddWithValue("@Model", robot.Model ?? "OrionStar Lucki");
            cmd.Parameters.AddWithValue("@Ip", robot.Ip ?? "192.168.0.32");
            cmd.Parameters.AddWithValue("@Port", robot.Port > 0 ? robot.Port : 8899);
            cmd.Parameters.AddWithValue("@Status", robot.Status ?? "Online");
            cmd.Parameters.AddWithValue("@Battery", robot.Battery);
            cmd.Parameters.AddWithValue("@IsCharging", robot.IsCharging ? 1 : 0);
            cmd.Parameters.AddWithValue("@AssignedMap", robot.AssignedMap ?? "");
            cmd.Parameters.AddWithValue("@PoseX", robot.PoseX);
            cmd.Parameters.AddWithValue("@PoseY", robot.PoseY);
            cmd.Parameters.AddWithValue("@PoseAngle", robot.PoseAngle);
            cmd.ExecuteNonQuery();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SaveRobot] Error saving to DB: {ex.Message}");
        }
    }

    public void DeleteRobot(string id)
    {
        _robotLiveCache.TryRemove(id, out _);
        foreach (var k in _robotLiveCache.Keys.Where(k => 
            string.Equals(k, id, StringComparison.OrdinalIgnoreCase) || 
            string.Equals(_robotLiveCache[k].Name, id, StringComparison.OrdinalIgnoreCase) ||
            string.Equals(_robotLiveCache[k].Ip, id, StringComparison.OrdinalIgnoreCase)).ToList())
        {
            _robotLiveCache.TryRemove(k, out _);
        }

        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var cmd = new SqliteCommand("DELETE FROM Robots WHERE Id = @Id OR Name = @Id OR Ip = @Id;", conn);
        cmd.Parameters.AddWithValue("@Id", id);
        cmd.ExecuteNonQuery();
    }

    public void SaveUserAccount(CloudUserAccountItem user)
    {
        if (string.IsNullOrWhiteSpace(user.Id)) user.Id = "USR-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();

        string sql = @"
            INSERT INTO UserAccounts (Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt)
            VALUES (@Id, @Username, @Password, @FullName, @Email, @Role, @OrgId, @SiteId, @IsActive, @CreatedAt)
            ON CONFLICT(Id) DO UPDATE SET 
                Username = @Username,
                Password = CASE WHEN @Password IS NOT NULL AND @Password != '' THEN @Password ELSE Password END,
                FullName = @FullName,
                Email = @Email,
                Role = @Role,
                OrganizationId = @OrgId,
                SiteId = @SiteId,
                IsActive = @IsActive;";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@Id", user.Id);
        cmd.Parameters.AddWithValue("@Username", user.Username);
        cmd.Parameters.AddWithValue("@Password", user.Password ?? "123456");
        cmd.Parameters.AddWithValue("@FullName", user.FullName);
        cmd.Parameters.AddWithValue("@Email", user.Email ?? "");
        cmd.Parameters.AddWithValue("@Role", user.Role ?? "Staff");
        cmd.Parameters.AddWithValue("@OrgId", (object?)user.OrganizationId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@SiteId", (object?)user.SiteId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@IsActive", user.IsActive ? 1 : 0);
        cmd.Parameters.AddWithValue("@CreatedAt", DateTime.UtcNow.ToString("o"));
        cmd.ExecuteNonQuery();
    }

    public void DeleteUserAccount(string id)
    {
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var cmd = new SqliteCommand("DELETE FROM UserAccounts WHERE Id = @Id;", conn);
        cmd.Parameters.AddWithValue("@Id", id);
        cmd.ExecuteNonQuery();
    }

    public List<CloudRobotItem> GetAllRobots(string? orgId = null, string? siteId = null)
    {
        // Chống lặp thẻ: Nhóm theo Name hoặc Id chuẩn để đảm bảo chỉ có duy nhất 1 bản ghi cho mỗi Robot
        var list = _robotLiveCache.Values
            .GroupBy(r => !string.IsNullOrEmpty(r.Name) ? r.Name.Trim().ToUpperInvariant() : r.Id)
            .Select(g => g.OrderByDescending(r => !string.IsNullOrEmpty(r.Ip)).First())
            .ToList();

        if (!string.IsNullOrEmpty(siteId))
        {
            list = list.Where(r => r.SiteId == siteId).ToList();
        }
        else if (!string.IsNullOrEmpty(orgId))
        {
            list = list.Where(r => r.OrgId == orgId).ToList();
        }
        return list;
    }

    public CloudRobotItem? GetRobotById(string robotId)
    {
        if (string.IsNullOrWhiteSpace(robotId)) return null;
        if (_robotLiveCache.TryGetValue(robotId, out var item)) return item;

        // Tìm theo Name hoặc IP nếu robotId gửi lên là Tên hoặc IP
        return _robotLiveCache.Values.FirstOrDefault(r => 
            string.Equals(r.Id, robotId, StringComparison.OrdinalIgnoreCase) ||
            string.Equals(r.Name, robotId, StringComparison.OrdinalIgnoreCase) ||
            (!string.IsNullOrEmpty(r.Ip) && string.Equals(r.Ip, robotId, StringComparison.OrdinalIgnoreCase)));
    }

    public void UpdateRobotTelemetry(RobotTelemetryPacket packet)
    {
        if (string.IsNullOrEmpty(packet.RobotId)) return;

        // Chỉ cập nhật nếu Robot thực sự tồn tại trong hệ thống (KHÔNG tự động sinh mới robot từ telemetry)
        var existing = GetRobotById(packet.RobotId);
        if (existing == null)
        {
            // Robot không tồn tại hoặc đã bị xóa -> bỏ qua, không tự động sinh robot ma
            return;
        }

        // Đảm bảo chỉ lưu 1 key chuẩn canonical Id trong LiveCache
        _robotLiveCache[existing.Id] = existing;
        if (!string.Equals(packet.RobotId, existing.Id, StringComparison.OrdinalIgnoreCase))
        {
            _robotLiveCache.TryRemove(packet.RobotId, out _);
        }

        existing.Battery = Math.Clamp(packet.Battery, 0, 100);
        existing.IsCharging = packet.IsCharging;
        existing.BatteryText = (packet.IsCharging ? "⚡ " : "🔋 ") + existing.Battery + "%";
        existing.BatteryColor = existing.Battery > 50 ? "#10B981" : existing.Battery > 20 ? "#F59E0B" : "#EF4444";

        existing.Status = string.IsNullOrEmpty(packet.Status) ? (packet.IsCharging ? "Charging" : "Online") : packet.Status;
        existing.StatusColor = existing.Status switch
        {
            "Online" => "#10B981",
            "Charging" => "#F59E0B",
            "EmergencyStop" => "#EF4444",
            _ => "#64748B"
        };

        if (!string.IsNullOrEmpty(packet.MapName)) existing.AssignedMap = packet.MapName;
        existing.PoseX = packet.PoseX;
        existing.PoseY = packet.PoseY;
        existing.PoseAngle = packet.PoseAngle;
        if (!string.IsNullOrEmpty(packet.ActiveMode)) existing.ActiveMode = packet.ActiveMode;
        if (!string.IsNullOrEmpty(packet.OrgId)) existing.OrgId = packet.OrgId;
        if (!string.IsNullOrEmpty(packet.SiteId)) existing.SiteId = packet.SiteId;
        existing.LastSeen = DateTime.UtcNow;

        // Async write back to DB (chỉ UPDATE bản ghi đã tồn tại)
        UpdateRobotInDb(existing);
    }

    private void UpdateRobotInDb(CloudRobotItem r)
    {
        try
        {
            using var conn = new SqliteConnection(_connectionString);
            conn.Open();
            string sql = @"
                UPDATE Robots SET 
                    Status = @status, 
                    Battery = @battery, 
                    IsCharging = @charging, 
                    PoseX = @x, 
                    PoseY = @y, 
                    PoseAngle = @angle, 
                    AssignedMap = CASE WHEN @map != '' THEN @map ELSE AssignedMap END,
                    LastSeen = datetime('now')
                WHERE Id = @id OR Name = @id;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", r.Id);
            cmd.Parameters.AddWithValue("@status", r.Status);
            cmd.Parameters.AddWithValue("@battery", r.Battery);
            cmd.Parameters.AddWithValue("@charging", r.IsCharging ? 1 : 0);
            cmd.Parameters.AddWithValue("@x", r.PoseX);
            cmd.Parameters.AddWithValue("@y", r.PoseY);
            cmd.Parameters.AddWithValue("@angle", r.PoseAngle);
            cmd.Parameters.AddWithValue("@map", r.AssignedMap ?? "");
            cmd.ExecuteNonQuery();
        }
        catch (Exception)
        {
            // Non-critical background telemetry persist error
        }
    }

    public FleetSummaryDto GetFleetSummary(string? orgId = null, string? siteId = null)
    {
        var robots = GetAllRobots(orgId, siteId);
        int total = robots.Count;
        int online = robots.Count(r => r.Status == "Online");
        int charging = robots.Count(r => r.Status == "Charging" || r.IsCharging);
        int offline = total - online - charging;
        int avgBat = total > 0 ? (int)robots.Average(r => r.Battery) : 0;

        return new FleetSummaryDto
        {
            TotalOrganizations = GetOrganizations().Count,
            TotalSites = GetSites().Count,
            TotalRobots = total,
            OnlineRobots = online,
            ChargingRobots = charging,
            OfflineRobots = Math.Max(0, offline),
            AvgBattery = avgBat,
            ActiveOrders = online > 0 ? 12 : 0,
            ServerTime = DateTime.UtcNow
        };
    }

    public void AddAlert(FleetAlertItem alert)
    {
        _recentAlerts.Enqueue(alert);
        while (_recentAlerts.Count > 50) _recentAlerts.TryDequeue(out _);
    }

    public List<FleetAlertItem> GetRecentAlerts()
    {
        return _recentAlerts.Reverse().Take(15).ToList();
    }

    private List<CloudRobotItem> GetAllRobotsFromDb()
    {
        var list = new List<CloudRobotItem>();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();

        string sql = @"
            SELECT r.Id, r.Name, r.Model, r.SiteId, s.Name, s.OrganizationId, o.Name,
                   r.Ip, r.Port, r.Status, r.Battery, r.IsCharging, r.AssignedMap, r.PoseX, r.PoseY, r.PoseAngle
            FROM Robots r
            LEFT JOIN Sites s ON r.SiteId = s.Id
            LEFT JOIN Organizations o ON s.OrganizationId = o.Id;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            int bat = reader.GetInt32(10);
            bool isChg = reader.GetInt32(11) == 1;
            string status = reader.IsDBNull(9) ? "Online" : reader.GetString(9);

            list.Add(new CloudRobotItem
            {
                Id = reader.GetString(0),
                Name = reader.GetString(1),
                Model = reader.IsDBNull(2) ? "OrionStar Lucki" : reader.GetString(2),
                SiteId = reader.IsDBNull(3) ? "" : reader.GetString(3),
                SiteName = reader.IsDBNull(4) ? "Chi nhánh" : reader.GetString(4),
                OrgId = reader.IsDBNull(5) ? "" : reader.GetString(5),
                OrgName = reader.IsDBNull(6) ? "" : reader.GetString(6),
                Ip = reader.IsDBNull(7) ? "192.168.0.32" : reader.GetString(7),
                Port = reader.GetInt32(8),
                Status = status,
                StatusColor = status == "Online" ? "#10B981" : status == "Charging" ? "#F59E0B" : "#64748B",
                Battery = bat,
                IsCharging = isChg,
                BatteryText = (isChg ? "⚡ " : "🔋 ") + bat + "%",
                BatteryColor = bat > 50 ? "#10B981" : bat > 20 ? "#F59E0B" : "#EF4444",
                AssignedMap = reader.IsDBNull(12) ? "tang 2-1110154527" : reader.GetString(12),
                PoseX = reader.GetDouble(13),
                PoseY = reader.GetDouble(14),
                PoseAngle = reader.GetDouble(15),
                LastSeen = DateTime.UtcNow
            });
        }
        return list;
    }

    private CloudUserAccountItem _currentUser = new CloudUserAccountItem
    {
        Id = "USR-01",
        Username = "admin",
        FullName = "Quản trị viên LH",
        Email = "admin@lehoangrobotics.vn",
        Role = "SuperAdmin",
        OrganizationName = "Lê Hoàng Robotics Corp",
        SiteName = "Toàn hệ thống",
        IsActive = true
    };

    public CloudUserAccountItem GetCurrentUser() => _currentUser;

    public void SetCurrentUser(CloudUserAccountItem user)
    {
        if (user != null) _currentUser = user;
    }

    public List<CloudUserAccountItem> GetUserAccounts()
    {
        var list = new List<CloudUserAccountItem>();
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        string sql = @"
            SELECT u.Id, u.Username, u.FullName, u.Email, u.Role,
                   u.OrganizationId, o.Name AS OrgName,
                   u.SiteId, s.Name AS SiteName, u.IsActive
            FROM UserAccounts u
            LEFT JOIN Organizations o ON u.OrganizationId = o.Id
            LEFT JOIN Sites s ON u.SiteId = s.Id
            ORDER BY u.Username ASC;";
        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new CloudUserAccountItem
            {
                Id = reader.GetString(0),
                Username = reader.GetString(1),
                FullName = reader.GetString(2),
                Email = reader.IsDBNull(3) ? "" : reader.GetString(3),
                Role = reader.GetString(4),
                OrganizationId = reader.IsDBNull(5) ? null : reader.GetString(5),
                OrganizationName = reader.IsDBNull(6) ? "Lê Hoàng Robotics Corp" : reader.GetString(6),
                SiteId = reader.IsDBNull(7) ? null : reader.GetString(7),
                SiteName = reader.IsDBNull(8) ? "Toàn hệ thống" : reader.GetString(8),
                IsActive = reader.GetInt32(9) == 1
            });
        }
        return list;
    }

    public CloudUserAccountItem? Authenticate(string username, string password)
    {
        using var conn = new SqliteConnection(_connectionString);
        conn.Open();
        string sql = @"
            SELECT u.Id, u.Username, u.FullName, u.Email, u.Role,
                   u.OrganizationId, o.Name AS OrgName,
                   u.SiteId, s.Name AS SiteName, u.IsActive
            FROM UserAccounts u
            LEFT JOIN Organizations o ON u.OrganizationId = o.Id
            LEFT JOIN Sites s ON u.SiteId = s.Id
            WHERE LOWER(u.Username) = LOWER(@Username) AND u.Password = @Password AND u.IsActive = 1
            LIMIT 1;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@Username", username.Trim());
        cmd.Parameters.AddWithValue("@Password", password.Trim());
        using var reader = cmd.ExecuteReader();
        if (reader.Read())
        {
            var user = new CloudUserAccountItem
            {
                Id = reader.GetString(0),
                Username = reader.GetString(1),
                FullName = reader.GetString(2),
                Email = reader.IsDBNull(3) ? "" : reader.GetString(3),
                Role = reader.GetString(4),
                OrganizationId = reader.IsDBNull(5) ? null : reader.GetString(5),
                OrganizationName = reader.IsDBNull(6) ? "Lê Hoàng Robotics Corp" : reader.GetString(6),
                SiteId = reader.IsDBNull(7) ? null : reader.GetString(7),
                SiteName = reader.IsDBNull(8) ? "Toàn hệ thống" : reader.GetString(8),
                IsActive = reader.GetInt32(9) == 1
            };
            _currentUser = user;
            return user;
        }
        return null;
    }

    public CloudMapInfo GetMapInfo(string? mapName = null)
    {
        var info = new CloudMapInfo
        {
            MapName = string.IsNullOrEmpty(mapName) ? "tầng 2-1110154527" : mapName,
            ImageUrl = "img/default_map_hd.png",
            MinX = -5.0,
            MaxX = 8.0,
            MinY = -6.0,
            MaxY = 13.0,
            Stations = new List<MapStationItem>()
        };

        string[] candidatePaths = new[]
        {
            Path.Combine(AppContext.BaseDirectory, "saved_stations.json"),
            Path.Combine(Directory.GetCurrentDirectory(), "saved_stations.json"),
            Path.Combine(Directory.GetCurrentDirectory(), "..", "LeHoangControlCenter", "saved_stations.json"),
            @"F:\EDU\2026\LEHOANGROBOTICS\LeHoangControlCenter\saved_stations.json"
        };

        foreach (var path in candidatePaths)
        {
            if (File.Exists(path))
            {
                try
                {
                    var json = File.ReadAllText(path);
                    using var doc = System.Text.Json.JsonDocument.Parse(json);
                    System.Text.Json.JsonElement floorElement = default;
                    if (doc.RootElement.TryGetProperty(info.MapName, out var found))
                    {
                        floorElement = found;
                    }
                    else
                    {
                        foreach (var prop in doc.RootElement.EnumerateObject())
                        {
                            floorElement = prop.Value;
                            info.MapName = prop.Name;
                            break;
                        }
                    }

                    if (floorElement.ValueKind == System.Text.Json.JsonValueKind.Array)
                    {
                        foreach (var item in floorElement.EnumerateArray())
                        {
                            string name = item.TryGetProperty("Name", out var n) ? n.GetString() ?? "" : "";
                            double x = item.TryGetProperty("X", out var xProp) ? xProp.GetDouble() : 0.0;
                            double y = item.TryGetProperty("Y", out var yProp) ? yProp.GetDouble() : 0.0;
                            double th = item.TryGetProperty("Theta", out var thProp) ? thProp.GetDouble() : 0.0;

                            string type = "delivery";
                            string icon = "📍";
                            string lower = name.ToLowerInvariant();
                            if (lower.Contains("charge") || lower.Contains("sạc")) { type = "charge"; icon = "⚡"; }
                            else if (lower.Contains("dock") || lower.Contains("pole")) { type = "dock"; icon = "🔌"; }
                            else if (lower.Contains("standby") || lower.Contains("stand-by") || lower.Contains("chờ")) { type = "standby"; icon = "🅿️"; }
                            else if (lower.Contains("thang") || lower.Contains("tm")) { type = "elevator"; icon = "🛗"; }
                            else if (lower.Contains("cafe") || lower.Contains("cà phê")) { type = "cafe"; icon = "☕"; }
                            else if (lower.Contains("họp")) { type = "meeting"; icon = "🏢"; }
                            else if (lower.Contains("tivi")) { type = "screen"; icon = "📺"; }

                            info.Stations.Add(new MapStationItem
                            {
                                Name = name,
                                X = x,
                                Y = y,
                                Theta = th,
                                Icon = icon,
                                Type = type
                            });
                        }
                        if (info.Stations.Count > 0) return info;
                    }
                }
                catch { }
            }
        }

        // Fallback default stations if file cannot be read
        info.Stations = new List<MapStationItem>
        {
            new() { Name = "Stand-by Point", X = 1.93, Y = 5.99, Icon = "🅿️", Type = "standby" },
            new() { Name = "Charging Point", X = 0.6, Y = -0.31, Icon = "⚡", Type = "charge" },
            new() { Name = "Charging Pole", X = 0.0, Y = -0.31, Icon = "🔌", Type = "dock" },
            new() { Name = "Positioning Point", X = 1.46, Y = 0.66, Icon = "🎯", Type = "position" },
            new() { Name = "ChoTM2", X = 2.14, Y = 5.97, Icon = "🛗", Type = "elevator" },
            new() { Name = "TrongTM2", X = 4.52, Y = 6.12, Icon = "🛗", Type = "elevator" },
            new() { Name = "tivi", X = 1.44, Y = 1.31, Icon = "📺", Type = "screen" },
            new() { Name = "cafe", X = 1.12, Y = -1.16, Icon = "☕", Type = "cafe" },
            new() { Name = "phòng họp", X = 1.69, Y = 8.38, Icon = "🏢", Type = "meeting" },
            new() { Name = "BanThanh", X = 2.66, Y = 2.81, Icon = "🍽️", Type = "delivery" },
            new() { Name = "test", X = 1.48, Y = 4.75, Icon = "🧪", Type = "delivery" }
        };
        return info;
    }
}


