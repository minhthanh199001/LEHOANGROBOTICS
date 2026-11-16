using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using LeHoangControlCenter.Models;
using Microsoft.Data.Sqlite;

namespace LeHoangControlCenter.Services
{
    public class DatabaseService
    {
        private static DatabaseService? _instance;
        public static DatabaseService Instance => _instance ??= new DatabaseService();

        private readonly string _connectionString;

        public DatabaseService()
        {
            string dbPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "lehoang_fleet.db");
            _connectionString = $"Data Source={dbPath}";
            InitializeDatabase();
        }

        private void InitializeDatabase()
        {
            using var conn = new SqliteConnection(_connectionString);
            conn.Open();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                CREATE TABLE IF NOT EXISTS Organizations (
                    Id TEXT PRIMARY KEY,
                    Name TEXT NOT NULL,
                    Code TEXT NOT NULL,
                    Description TEXT,
                    ContactPerson TEXT,
                    Phone TEXT,
                    Email TEXT,
                    CreatedAt TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS Sites (
                    Id TEXT PRIMARY KEY,
                    OrganizationId TEXT NOT NULL,
                    Name TEXT NOT NULL,
                    Code TEXT NOT NULL,
                    Address TEXT,
                    ContactPhone TEXT,
                    CreatedAt TEXT NOT NULL,
                    FOREIGN KEY (OrganizationId) REFERENCES Organizations(Id) ON DELETE CASCADE
                );

                CREATE TABLE IF NOT EXISTS Robots (
                    Id TEXT PRIMARY KEY,
                    SiteId TEXT NOT NULL,
                    OrganizationId TEXT NOT NULL,
                    Name TEXT NOT NULL,
                    Model TEXT,
                    Ip TEXT NOT NULL,
                    Port INTEGER NOT NULL,
                    BatteryLevel INTEGER DEFAULT 100,
                    Status TEXT DEFAULT 'Online',
                    AssignedMap TEXT,
                    CreatedAt TEXT NOT NULL,
                    LastSeen TEXT,
                    FOREIGN KEY (SiteId) REFERENCES Sites(Id) ON DELETE CASCADE,
                    FOREIGN KEY (OrganizationId) REFERENCES Organizations(Id) ON DELETE CASCADE
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
                    IsActive INTEGER DEFAULT 1,
                    CreatedAt TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS DeliveryOrders (
                    Id TEXT PRIMARY KEY,
                    OrderCode TEXT NOT NULL,
                    OrganizationId TEXT NOT NULL,
                    SiteId TEXT NOT NULL,
                    RobotId TEXT NOT NULL,
                    Tray1Dest TEXT,
                    Tray2Dest TEXT,
                    Tray3Dest TEXT,
                    SpeedMode TEXT,
                    Status TEXT NOT NULL,
                    CreatedAt TEXT NOT NULL,
                    CompletedAt TEXT
                );

                CREATE TABLE IF NOT EXISTS AlertLogs (
                    Id TEXT PRIMARY KEY,
                    OrganizationId TEXT NOT NULL,
                    SiteId TEXT NOT NULL,
                    RobotId TEXT,
                    Title TEXT NOT NULL,
                    Message TEXT NOT NULL,
                    Severity TEXT NOT NULL,
                    CreatedAt TEXT NOT NULL,
                    IsRead INTEGER DEFAULT 0
                );
            ";
            cmd.ExecuteNonQuery();

            // Migrate any old 'Available' status to 'Online'
            try
            {
                using var migrateCmd = conn.CreateCommand();
                migrateCmd.CommandText = "UPDATE Robots SET Status = 'Online' WHERE Status = 'Available';";
                migrateCmd.ExecuteNonQuery();
            }
            catch { }

            // Seed Default Data if empty
            SeedDefaultDataIfEmpty(conn);
        }

        private void SeedDefaultDataIfEmpty(SqliteConnection conn)
        {
            using var checkCmd = conn.CreateCommand();
            checkCmd.CommandText = "SELECT COUNT(*) FROM Organizations;";
            long count = (long)(checkCmd.ExecuteScalar() ?? 0L);

            if (count == 0)
            {
                using var trans = conn.BeginTransaction();
                try
                {
                    using var cmd = conn.CreateCommand();
                    cmd.Transaction = trans;
                    cmd.CommandText = @"
                        -- 1. Organizations
                        INSERT INTO Organizations (Id, Name, Code, Description, ContactPerson, Phone, Email, CreatedAt) VALUES
                        ('ORG-LEHOANG', 'Lê Hoàng Robotics Corp', 'LEHOANG', 'Tổng công ty Công nghệ Tự hành Lê Hoàng', 'Lê Hoàng', '0901234567', 'contact@lehoangrobotics.vn', datetime('now')),
                        ('ORG-VINPEARL', 'Tập đoàn Vinpearl Hospitality', 'VINPEARL', 'Hệ thống Khách sạn & Nghỉ dưỡng Vinpearl', 'Nguyễn Văn A', '0912345678', 'fleet@vinpearl.com', datetime('now')),
                        ('ORG-MUONGTHANH', 'Khách sạn Mường Thanh', 'MUONGTHANH', 'Chuỗi Khách sạn Tư nhân Mường Thanh', 'Trần Thị B', '0987654321', 'robotics@muongthanh.vn', datetime('now'));

                        -- 2. Sites
                        INSERT INTO Sites (Id, OrganizationId, Name, Code, Address, ContactPhone, CreatedAt) VALUES
                        ('SITE-RND', 'ORG-LEHOANG', 'Trung tâm R&D & Test Lab', 'RND-Q1', 'Số 12 Đường Lê Hoàng, TP. HCM', '0901234567', datetime('now')),
                        ('SITE-VLM81', 'ORG-VINPEARL', 'Vinpearl Landmark 81', 'VLM-81', '720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP. HCM', '0912345678', datetime('now')),
                        ('SITE-RESORT-NT', 'ORG-VINPEARL', 'Vinpearl Resort & Spa Nha Trang', 'VRS-NT', 'Đảo Hòn Tre, Nha Trang, Khánh Hòa', '0912345679', datetime('now')),
                        ('SITE-MTSG', 'ORG-MUONGTHANH', 'Mường Thanh Luxury Sài Gòn', 'MT-SG', '261 Nguyễn Văn Trỗi, Phú Nhuận, TP. HCM', '0987654321', datetime('now'));

                        -- 3. Robots
                        INSERT INTO Robots (Id, SiteId, OrganizationId, Name, Model, Ip, Port, BatteryLevel, Status, AssignedMap, CreatedAt, LastSeen) VALUES
                        ('ROB01', 'SITE-RND', 'ORG-LEHOANG', 'Lê Hoàng 01 (Test Lab)', 'OrionStar Delivery Bot', '192.168.0.125', 8899, 95, 'Online', 'tăng 2-1110154527', datetime('now'), datetime('now')),
                        ('ROB02', 'SITE-VLM81', 'ORG-VINPEARL', 'Vinpearl LM81 - Bot 01', 'OrionStar Lucki Bot', '192.168.0.134', 8899, 88, 'Active', 'MT1map-0721090621', datetime('now'), datetime('now')),
                        ('ROB03', 'SITE-VLM81', 'ORG-VINPEARL', 'Vinpearl LM81 - Bot 02', 'OrionStar Lucki Bot', '192.168.0.135', 8899, 72, 'Online', 'MT1map-0721090621', datetime('now'), datetime('now')),
                        ('ROB04', 'SITE-RESORT-NT', 'ORG-VINPEARL', 'Vinpearl Resort Bot 01', 'OrionStar Delivery Bot', '192.168.1.110', 8899, 100, 'Charging', 'MT1map-0721090621', datetime('now'), datetime('now')),
                        ('ROB05', 'SITE-MTSG', 'ORG-MUONGTHANH', 'Mường Thanh Bot Phục Vụ', 'OrionStar Lucki Bot', '192.168.2.120', 8899, 65, 'Online', 'tăng 2-1110154527', datetime('now'), datetime('now'));

                        -- 4. User Accounts
                        INSERT INTO UserAccounts (Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt) VALUES
                        ('USR-01', 'admin', '123456', 'Quản trị viên Hệ thống (Lê Hoàng)', 'admin@lehoangrobotics.vn', 'SuperAdmin', NULL, NULL, 1, datetime('now')),
                        ('USR-02', 'vinpearl_admin', '123456', 'Ban Quản trị Vinpearl', 'admin@vinpearl.com', 'OrgAdmin', 'ORG-VINPEARL', NULL, 1, datetime('now')),
                        ('USR-03', 'vlm81_manager', '123456', 'Quản lý Chi nhánh Landmark 81', 'manager.lm81@vinpearl.com', 'SiteManager', 'ORG-VINPEARL', 'SITE-VLM81', 1, datetime('now')),
                        ('USR-04', 'operator01', '123456', 'Nhân viên Vận hành Bot', 'operator@lehoangrobotics.vn', 'Operator', 'ORG-LEHOANG', 'SITE-RND', 1, datetime('now'));

                        -- 5. Delivery Orders
                        INSERT INTO DeliveryOrders (Id, OrderCode, OrganizationId, SiteId, RobotId, Tray1Dest, Tray2Dest, Tray3Dest, SpeedMode, Status, CreatedAt, CompletedAt) VALUES
                        ('ORD-001', 'ORD-20261114-01', 'ORG-LEHOANG', 'SITE-RND', 'ROB01', 'Bàn 1', 'Bàn 2', 'cafe', 'Nhanh (0.7 m/s)', 'Hoàn thành', datetime('now', '-30 minutes'), datetime('now', '-25 minutes')),
                        ('ORD-002', 'ORD-20261114-02', 'ORG-LEHOANG', 'SITE-RND', 'ROB01', 'phòng họp', 'tivi', '', 'Chậm (0.5 m/s)', 'Hoàn thành', datetime('now', '-15 minutes'), datetime('now', '-10 minutes')),
                        ('ORD-003', 'ORD-20261114-03', 'ORG-VINPEARL', 'SITE-VLM81', 'ROB02', 'Bàn 5', '', '', 'Nhanh (0.7 m/s)', 'Đang giao', datetime('now', '-5 minutes'), NULL);

                        -- 6. Alert Logs
                        INSERT INTO AlertLogs (Id, OrganizationId, SiteId, RobotId, Title, Message, Severity, CreatedAt, IsRead) VALUES
                        ('ALT-001', 'ORG-LEHOANG', 'SITE-RND', 'ROB01', 'Mất định vị SLAM', 'Robot mất vị trí sau khi khởi động. Cần khôi phục vị trí.', 'Warning', datetime('now', '-1 hour'), 0),
                        ('ALT-002', 'ORG-VINPEARL', 'SITE-VLM81', 'ROB03', 'Pin yếu dưới 20%', 'Robot ROB03 sắp hết pin, tự động quay về trạm sạc.', 'Info', datetime('now', '-45 minutes'), 1),
                        ('ALT-003', 'ORG-LEHOANG', 'SITE-RND', 'ROB01', 'Vật cản đường', 'Phát hiện vật cản bất ngờ trên hành lang.', 'Warning', datetime('now', '-20 minutes'), 0);
                    ";
                    cmd.ExecuteNonQuery();
                    trans.Commit();
                }
                catch
                {
                    trans.Rollback();
                    throw;
                }
            }
        }

        // =========================================================================
        // ORGANIZATIONS CRUD
        // =========================================================================
        public async Task<List<OrganizationItem>> GetOrganizationsAsync()
        {
            var list = new List<OrganizationItem>();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                SELECT o.Id, o.Name, o.Code, o.Description, o.ContactPerson, o.Phone, o.Email, o.CreatedAt,
                       (SELECT COUNT(*) FROM Sites s WHERE s.OrganizationId = o.Id) AS SiteCount,
                       (SELECT COUNT(*) FROM Robots r WHERE r.OrganizationId = o.Id) AS RobotCount
                FROM Organizations o
                ORDER BY o.Name ASC;";

            using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                list.Add(new OrganizationItem
                {
                    Id = reader.GetString(0),
                    Name = reader.GetString(1),
                    Code = reader.GetString(2),
                    Description = reader.IsDBNull(3) ? "" : reader.GetString(3),
                    ContactPerson = reader.IsDBNull(4) ? "" : reader.GetString(4),
                    Phone = reader.IsDBNull(5) ? "" : reader.GetString(5),
                    Email = reader.IsDBNull(6) ? "" : reader.GetString(6),
                    CreatedAt = DateTime.TryParse(reader.GetString(7), out var dt) ? dt : DateTime.Now,
                    SiteCount = reader.GetInt32(8),
                    RobotCount = reader.GetInt32(9)
                });
            }
            return list;
        }

        public async Task InsertOrganizationAsync(OrganizationItem org)
        {
            if (string.IsNullOrWhiteSpace(org.Id)) org.Id = "ORG-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                INSERT INTO Organizations (Id, Name, Code, Description, ContactPerson, Phone, Email, CreatedAt)
                VALUES (@Id, @Name, @Code, @Description, @ContactPerson, @Phone, @Email, @CreatedAt);";
            cmd.Parameters.AddWithValue("@Id", org.Id);
            cmd.Parameters.AddWithValue("@Name", org.Name);
            cmd.Parameters.AddWithValue("@Code", org.Code);
            cmd.Parameters.AddWithValue("@Description", org.Description ?? "");
            cmd.Parameters.AddWithValue("@ContactPerson", org.ContactPerson ?? "");
            cmd.Parameters.AddWithValue("@Phone", org.Phone ?? "");
            cmd.Parameters.AddWithValue("@Email", org.Email ?? "");
            cmd.Parameters.AddWithValue("@CreatedAt", DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task UpdateOrganizationAsync(OrganizationItem org)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                UPDATE Organizations
                SET Name = @Name, Code = @Code, Description = @Description, ContactPerson = @ContactPerson, Phone = @Phone, Email = @Email
                WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", org.Id);
            cmd.Parameters.AddWithValue("@Name", org.Name);
            cmd.Parameters.AddWithValue("@Code", org.Code);
            cmd.Parameters.AddWithValue("@Description", org.Description ?? "");
            cmd.Parameters.AddWithValue("@ContactPerson", org.ContactPerson ?? "");
            cmd.Parameters.AddWithValue("@Phone", org.Phone ?? "");
            cmd.Parameters.AddWithValue("@Email", org.Email ?? "");

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task DeleteOrganizationAsync(string orgId)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = "DELETE FROM Organizations WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", orgId);
            await cmd.ExecuteNonQueryAsync();
        }

        // =========================================================================
        // SITES CRUD
        // =========================================================================
        public async Task<List<SiteItem>> GetSitesAsync(string? organizationId = null)
        {
            var list = new List<SiteItem>();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            if (string.IsNullOrEmpty(organizationId) || organizationId == "ALL")
            {
                cmd.CommandText = @"
                    SELECT s.Id, s.OrganizationId, o.Name AS OrgName, s.Name, s.Code, s.Address, s.ContactPhone, s.CreatedAt,
                           (SELECT COUNT(*) FROM Robots r WHERE r.SiteId = s.Id) AS RobotCount
                    FROM Sites s
                    LEFT JOIN Organizations o ON s.OrganizationId = o.Id
                    ORDER BY s.Name ASC;";
            }
            else
            {
                cmd.CommandText = @"
                    SELECT s.Id, s.OrganizationId, o.Name AS OrgName, s.Name, s.Code, s.Address, s.ContactPhone, s.CreatedAt,
                           (SELECT COUNT(*) FROM Robots r WHERE r.SiteId = s.Id) AS RobotCount
                    FROM Sites s
                    LEFT JOIN Organizations o ON s.OrganizationId = o.Id
                    WHERE s.OrganizationId = @OrgId
                    ORDER BY s.Name ASC;";
                cmd.Parameters.AddWithValue("@OrgId", organizationId);
            }

            using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                list.Add(new SiteItem
                {
                    Id = reader.GetString(0),
                    OrganizationId = reader.GetString(1),
                    OrganizationName = reader.IsDBNull(2) ? "" : reader.GetString(2),
                    Name = reader.GetString(3),
                    Code = reader.GetString(4),
                    Address = reader.IsDBNull(5) ? "" : reader.GetString(5),
                    ContactPhone = reader.IsDBNull(6) ? "" : reader.GetString(6),
                    CreatedAt = DateTime.TryParse(reader.GetString(7), out var dt) ? dt : DateTime.Now,
                    RobotCount = reader.GetInt32(8)
                });
            }
            return list;
        }

        public async Task InsertSiteAsync(SiteItem site)
        {
            if (string.IsNullOrWhiteSpace(site.Id)) site.Id = "SITE-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                INSERT INTO Sites (Id, OrganizationId, Name, Code, Address, ContactPhone, CreatedAt)
                VALUES (@Id, @OrgId, @Name, @Code, @Address, @ContactPhone, @CreatedAt);";
            cmd.Parameters.AddWithValue("@Id", site.Id);
            cmd.Parameters.AddWithValue("@OrgId", site.OrganizationId);
            cmd.Parameters.AddWithValue("@Name", site.Name);
            cmd.Parameters.AddWithValue("@Code", site.Code);
            cmd.Parameters.AddWithValue("@Address", site.Address ?? "");
            cmd.Parameters.AddWithValue("@ContactPhone", site.ContactPhone ?? "");
            cmd.Parameters.AddWithValue("@CreatedAt", DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task UpdateSiteAsync(SiteItem site)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                UPDATE Sites
                SET OrganizationId = @OrgId, Name = @Name, Code = @Code, Address = @Address, ContactPhone = @ContactPhone
                WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", site.Id);
            cmd.Parameters.AddWithValue("@OrgId", site.OrganizationId);
            cmd.Parameters.AddWithValue("@Name", site.Name);
            cmd.Parameters.AddWithValue("@Code", site.Code);
            cmd.Parameters.AddWithValue("@Address", site.Address ?? "");
            cmd.Parameters.AddWithValue("@ContactPhone", site.ContactPhone ?? "");

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task DeleteSiteAsync(string siteId)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = "DELETE FROM Sites WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", siteId);
            await cmd.ExecuteNonQueryAsync();
        }

        // =========================================================================
        // ROBOTS CRUD
        // =========================================================================
        public async Task<List<EnterpriseRobotItem>> GetRobotsAsync(string? siteId = null, string? organizationId = null)
        {
            var list = new List<EnterpriseRobotItem>();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            string sql = @"
                SELECT r.Id, r.SiteId, s.Name AS SiteName, r.OrganizationId, o.Name AS OrgName,
                       r.Name, r.Model, r.Ip, r.Port, r.BatteryLevel, r.Status, r.AssignedMap, r.CreatedAt, r.LastSeen
                FROM Robots r
                LEFT JOIN Sites s ON r.SiteId = s.Id
                LEFT JOIN Organizations o ON r.OrganizationId = o.Id
                WHERE 1=1 ";

            if (!string.IsNullOrEmpty(siteId) && siteId != "ALL")
            {
                sql += " AND r.SiteId = @SiteId ";
                cmd.Parameters.AddWithValue("@SiteId", siteId);
            }
            if (!string.IsNullOrEmpty(organizationId) && organizationId != "ALL")
            {
                sql += " AND r.OrganizationId = @OrgId ";
                cmd.Parameters.AddWithValue("@OrgId", organizationId);
            }
            sql += " ORDER BY r.Name ASC;";
            cmd.CommandText = sql;

            using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                list.Add(new EnterpriseRobotItem
                {
                    Id = reader.GetString(0),
                    SiteId = reader.GetString(1),
                    SiteName = reader.IsDBNull(2) ? "" : reader.GetString(2),
                    OrganizationId = reader.GetString(3),
                    OrganizationName = reader.IsDBNull(4) ? "" : reader.GetString(4),
                    Name = reader.GetString(5),
                    Model = reader.IsDBNull(6) ? "OrionStar Lucki" : reader.GetString(6),
                    Ip = reader.GetString(7),
                    Port = reader.GetInt32(8),
                    BatteryLevel = reader.GetInt32(9),
                    Status = reader.IsDBNull(10) ? "Online" : (reader.GetString(10).Equals("Available", StringComparison.OrdinalIgnoreCase) ? "Online" : reader.GetString(10)),
                    AssignedMap = reader.IsDBNull(11) ? "" : reader.GetString(11),
                    CreatedAt = DateTime.TryParse(reader.GetString(12), out var dt) ? dt : DateTime.Now,
                    LastSeen = reader.IsDBNull(13) ? DateTime.Now : (DateTime.TryParse(reader.GetString(13), out var ls) ? ls : DateTime.Now)
                });
            }
            return list;
        }

        public async Task InsertRobotAsync(EnterpriseRobotItem bot)
        {
            if (string.IsNullOrWhiteSpace(bot.Id)) bot.Id = "ROB-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                INSERT INTO Robots (Id, SiteId, OrganizationId, Name, Model, Ip, Port, BatteryLevel, Status, AssignedMap, CreatedAt, LastSeen)
                VALUES (@Id, @SiteId, @OrgId, @Name, @Model, @Ip, @Port, @BatteryLevel, @Status, @AssignedMap, @CreatedAt, @LastSeen);";
            cmd.Parameters.AddWithValue("@Id", bot.Id);
            cmd.Parameters.AddWithValue("@SiteId", bot.SiteId);
            cmd.Parameters.AddWithValue("@OrgId", bot.OrganizationId);
            cmd.Parameters.AddWithValue("@Name", bot.Name);
            cmd.Parameters.AddWithValue("@Model", bot.Model ?? "OrionStar Lucki");
            cmd.Parameters.AddWithValue("@Ip", bot.Ip);
            cmd.Parameters.AddWithValue("@Port", bot.Port);
            cmd.Parameters.AddWithValue("@BatteryLevel", bot.BatteryLevel);
            cmd.Parameters.AddWithValue("@Status", bot.Status ?? "Available");
            cmd.Parameters.AddWithValue("@AssignedMap", bot.AssignedMap ?? "");
            cmd.Parameters.AddWithValue("@CreatedAt", DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
            cmd.Parameters.AddWithValue("@LastSeen", DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task UpdateRobotAsync(EnterpriseRobotItem bot)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                UPDATE Robots
                SET SiteId = @SiteId, OrganizationId = @OrgId, Name = @Name, Model = @Model,
                    Ip = @Ip, Port = @Port, BatteryLevel = @BatteryLevel, Status = @Status, AssignedMap = @AssignedMap
                WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", bot.Id);
            cmd.Parameters.AddWithValue("@SiteId", bot.SiteId);
            cmd.Parameters.AddWithValue("@OrgId", bot.OrganizationId);
            cmd.Parameters.AddWithValue("@Name", bot.Name);
            cmd.Parameters.AddWithValue("@Model", bot.Model ?? "OrionStar Lucki");
            cmd.Parameters.AddWithValue("@Ip", bot.Ip);
            cmd.Parameters.AddWithValue("@Port", bot.Port);
            cmd.Parameters.AddWithValue("@BatteryLevel", bot.BatteryLevel);
            cmd.Parameters.AddWithValue("@Status", bot.Status ?? "Available");
            cmd.Parameters.AddWithValue("@AssignedMap", bot.AssignedMap ?? "");

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task DeleteRobotAsync(string robotId)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = "DELETE FROM Robots WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", robotId);
            await cmd.ExecuteNonQueryAsync();
        }

        // =========================================================================
        // USER ACCOUNTS CRUD & AUTH
        // =========================================================================
        public async Task<List<UserAccountItem>> GetUserAccountsAsync()
        {
            var list = new List<UserAccountItem>();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                SELECT u.Id, u.Username, u.Password, u.FullName, u.Email, u.Role,
                       u.OrganizationId, o.Name AS OrgName,
                       u.SiteId, s.Name AS SiteName,
                       u.IsActive, u.CreatedAt
                FROM UserAccounts u
                LEFT JOIN Organizations o ON u.OrganizationId = o.Id
                LEFT JOIN Sites s ON u.SiteId = s.Id
                ORDER BY u.Username ASC;";

            using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                list.Add(new UserAccountItem
                {
                    Id = reader.GetString(0),
                    Username = reader.GetString(1),
                    Password = reader.GetString(2),
                    FullName = reader.GetString(3),
                    Email = reader.IsDBNull(4) ? "" : reader.GetString(4),
                    Role = reader.GetString(5),
                    OrganizationId = reader.IsDBNull(6) ? null : reader.GetString(6),
                    OrganizationName = reader.IsDBNull(7) ? "Tất cả Tổ chức (Toàn quyền)" : reader.GetString(7),
                    SiteId = reader.IsDBNull(8) ? null : reader.GetString(8),
                    SiteName = reader.IsDBNull(9) ? "Tất cả Sites" : reader.GetString(9),
                    IsActive = reader.GetInt32(10) == 1,
                    CreatedAt = DateTime.TryParse(reader.GetString(11), out var dt) ? dt : DateTime.Now
                });
            }
            return list;
        }

        public async Task InsertUserAccountAsync(UserAccountItem user)
        {
            if (string.IsNullOrWhiteSpace(user.Id)) user.Id = "USR-" + Guid.NewGuid().ToString("N")[..6].ToUpper();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                INSERT INTO UserAccounts (Id, Username, Password, FullName, Email, Role, OrganizationId, SiteId, IsActive, CreatedAt)
                VALUES (@Id, @Username, @Password, @FullName, @Email, @Role, @OrgId, @SiteId, @IsActive, @CreatedAt);";
            cmd.Parameters.AddWithValue("@Id", user.Id);
            cmd.Parameters.AddWithValue("@Username", user.Username);
            cmd.Parameters.AddWithValue("@Password", user.Password);
            cmd.Parameters.AddWithValue("@FullName", user.FullName);
            cmd.Parameters.AddWithValue("@Email", user.Email ?? "");
            cmd.Parameters.AddWithValue("@Role", user.Role);
            cmd.Parameters.AddWithValue("@OrgId", (object?)user.OrganizationId ?? DBNull.Value);
            cmd.Parameters.AddWithValue("@SiteId", (object?)user.SiteId ?? DBNull.Value);
            cmd.Parameters.AddWithValue("@IsActive", user.IsActive ? 1 : 0);
            cmd.Parameters.AddWithValue("@CreatedAt", DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task UpdateUserAccountAsync(UserAccountItem user)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                UPDATE UserAccounts
                SET Username = @Username, Password = @Password, FullName = @FullName, Email = @Email,
                    Role = @Role, OrganizationId = @OrgId, SiteId = @SiteId, IsActive = @IsActive
                WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", user.Id);
            cmd.Parameters.AddWithValue("@Username", user.Username);
            cmd.Parameters.AddWithValue("@Password", user.Password);
            cmd.Parameters.AddWithValue("@FullName", user.FullName);
            cmd.Parameters.AddWithValue("@Email", user.Email ?? "");
            cmd.Parameters.AddWithValue("@Role", user.Role);
            cmd.Parameters.AddWithValue("@OrgId", (object?)user.OrganizationId ?? DBNull.Value);
            cmd.Parameters.AddWithValue("@SiteId", (object?)user.SiteId ?? DBNull.Value);
            cmd.Parameters.AddWithValue("@IsActive", user.IsActive ? 1 : 0);

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task DeleteUserAccountAsync(string userId)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = "DELETE FROM UserAccounts WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", userId);
            await cmd.ExecuteNonQueryAsync();
        }

        // =========================================================================
        // DELIVERY ORDERS & ALERTS
        // =========================================================================
        public async Task<List<DeliveryOrderItem>> GetDeliveryOrdersAsync(string? siteId = null)
        {
            var list = new List<DeliveryOrderItem>();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            string sql = @"
                SELECT d.Id, d.OrderCode, d.OrganizationId, d.SiteId, s.Name AS SiteName,
                       d.RobotId, r.Name AS RobotName, d.Tray1Dest, d.Tray2Dest, d.Tray3Dest,
                       d.SpeedMode, d.Status, d.CreatedAt, d.CompletedAt
                FROM DeliveryOrders d
                LEFT JOIN Sites s ON d.SiteId = s.Id
                LEFT JOIN Robots r ON d.RobotId = r.Id ";
            if (!string.IsNullOrEmpty(siteId) && siteId != "ALL")
            {
                sql += " WHERE d.SiteId = @SiteId ";
                cmd.Parameters.AddWithValue("@SiteId", siteId);
            }
            sql += " ORDER BY d.CreatedAt DESC LIMIT 50;";
            cmd.CommandText = sql;

            using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                list.Add(new DeliveryOrderItem
                {
                    Id = reader.GetString(0),
                    OrderCode = reader.GetString(1),
                    OrganizationId = reader.GetString(2),
                    SiteId = reader.GetString(3),
                    SiteName = reader.IsDBNull(4) ? "" : reader.GetString(4),
                    RobotId = reader.GetString(5),
                    RobotName = reader.IsDBNull(6) ? "" : reader.GetString(6),
                    Tray1Dest = reader.IsDBNull(7) ? "" : reader.GetString(7),
                    Tray2Dest = reader.IsDBNull(8) ? "" : reader.GetString(8),
                    Tray3Dest = reader.IsDBNull(9) ? "" : reader.GetString(9),
                    SpeedMode = reader.IsDBNull(10) ? "Nhanh" : reader.GetString(10),
                    Status = reader.GetString(11),
                    CreatedAt = DateTime.TryParse(reader.GetString(12), out var dt) ? dt : DateTime.Now,
                    CompletedAt = reader.IsDBNull(13) ? null : (DateTime.TryParse(reader.GetString(13), out var comp) ? comp : null)
                });
            }
            return list;
        }

        public async Task InsertDeliveryOrderAsync(DeliveryOrderItem order)
        {
            if (string.IsNullOrWhiteSpace(order.Id)) order.Id = "ORD-" + Guid.NewGuid().ToString("N")[..8].ToUpper();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                INSERT INTO DeliveryOrders (Id, OrderCode, OrganizationId, SiteId, RobotId, Tray1Dest, Tray2Dest, Tray3Dest, SpeedMode, Status, CreatedAt, CompletedAt)
                VALUES (@Id, @OrderCode, @OrgId, @SiteId, @RobotId, @Tray1Dest, @Tray2Dest, @Tray3Dest, @SpeedMode, @Status, @CreatedAt, @CompletedAt);";
            cmd.Parameters.AddWithValue("@Id", order.Id);
            cmd.Parameters.AddWithValue("@OrderCode", order.OrderCode);
            cmd.Parameters.AddWithValue("@OrgId", order.OrganizationId);
            cmd.Parameters.AddWithValue("@SiteId", order.SiteId);
            cmd.Parameters.AddWithValue("@RobotId", order.RobotId);
            cmd.Parameters.AddWithValue("@Tray1Dest", order.Tray1Dest ?? "");
            cmd.Parameters.AddWithValue("@Tray2Dest", order.Tray2Dest ?? "");
            cmd.Parameters.AddWithValue("@Tray3Dest", order.Tray3Dest ?? "");
            cmd.Parameters.AddWithValue("@SpeedMode", order.SpeedMode ?? "Nhanh");
            cmd.Parameters.AddWithValue("@Status", order.Status ?? "Hoàn thành");
            cmd.Parameters.AddWithValue("@CreatedAt", order.CreatedAt.ToString("yyyy-MM-dd HH:mm:ss"));
            cmd.Parameters.AddWithValue("@CompletedAt", order.CompletedAt?.ToString("yyyy-MM-dd HH:mm:ss") ?? (object)DBNull.Value);

            await cmd.ExecuteNonQueryAsync();
        }

        public async Task<List<AlertLogItem>> GetAlertLogsAsync(string? siteId = null)
        {
            var list = new List<AlertLogItem>();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            string sql = @"
                SELECT a.Id, a.OrganizationId, a.SiteId, s.Name AS SiteName,
                       a.RobotId, a.Title, a.Message, a.Severity, a.CreatedAt, a.IsRead
                FROM AlertLogs a
                LEFT JOIN Sites s ON a.SiteId = s.Id ";
            if (!string.IsNullOrEmpty(siteId) && siteId != "ALL")
            {
                sql += " WHERE a.SiteId = @SiteId ";
                cmd.Parameters.AddWithValue("@SiteId", siteId);
            }
            sql += " ORDER BY a.CreatedAt DESC LIMIT 50;";
            cmd.CommandText = sql;

            using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                list.Add(new AlertLogItem
                {
                    Id = reader.GetString(0),
                    OrganizationId = reader.GetString(1),
                    SiteId = reader.GetString(2),
                    SiteName = reader.IsDBNull(3) ? "" : reader.GetString(3),
                    RobotId = reader.IsDBNull(4) ? "" : reader.GetString(4),
                    Title = reader.GetString(5),
                    Message = reader.GetString(6),
                    Severity = reader.GetString(7),
                    CreatedAt = DateTime.TryParse(reader.GetString(8), out var dt) ? dt : DateTime.Now,
                    IsRead = reader.GetInt32(9) == 1
                });
            }
            return list;
        }

        public async Task InsertAlertLogAsync(AlertLogItem alert)
        {
            if (string.IsNullOrWhiteSpace(alert.Id)) alert.Id = "ALT-" + Guid.NewGuid().ToString("N")[..8].ToUpper();
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                INSERT INTO AlertLogs (Id, OrganizationId, SiteId, RobotId, Title, Message, Severity, CreatedAt, IsRead)
                VALUES (@Id, @OrgId, @SiteId, @RobotId, @Title, @Message, @Severity, @CreatedAt, @IsRead);";
            cmd.Parameters.AddWithValue("@Id", alert.Id);
            cmd.Parameters.AddWithValue("@OrgId", alert.OrganizationId);
            cmd.Parameters.AddWithValue("@SiteId", alert.SiteId);
            cmd.Parameters.AddWithValue("@RobotId", alert.RobotId ?? "");
            cmd.Parameters.AddWithValue("@Title", alert.Title);
            cmd.Parameters.AddWithValue("@Message", alert.Message);
            cmd.Parameters.AddWithValue("@Severity", alert.Severity);
            cmd.Parameters.AddWithValue("@CreatedAt", alert.CreatedAt.ToString("yyyy-MM-dd HH:mm:ss"));
            cmd.Parameters.AddWithValue("@IsRead", alert.IsRead ? 1 : 0);

            await cmd.ExecuteNonQueryAsync();
        }

        // ==========================================
        // UPSERT CONVENIENCE HELPERS
        // ==========================================
        public async Task SaveOrganizationAsync(OrganizationItem org)
        {
            var existing = await GetOrganizationsAsync();
            if (existing.Exists(o => o.Id == org.Id))
                await UpdateOrganizationAsync(org);
            else
                await InsertOrganizationAsync(org);
        }

        public async Task SaveSiteAsync(SiteItem site)
        {
            var existing = await GetSitesAsync();
            if (existing.Exists(s => s.Id == site.Id))
                await UpdateSiteAsync(site);
            else
                await InsertSiteAsync(site);
        }

        public async Task SaveRobotAsync(EnterpriseRobotItem bot)
        {
            var existing = await GetRobotsAsync();
            if (existing.Exists(r => r.Id == bot.Id))
                await UpdateRobotAsync(bot);
            else
                await InsertRobotAsync(bot);
        }

        public async Task SaveUserAccountAsync(UserAccountItem user)
        {
            var existing = await GetUserAccountsAsync();
            if (existing.Exists(u => u.Id == user.Id))
                await UpdateUserAccountAsync(user);
            else
                await InsertUserAccountAsync(user);
        }

        public async Task<UserAccountItem?> AuthenticateUserAsync(string username, string password)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                SELECT u.Id, u.Username, u.Password, u.FullName, u.Email, u.Role,
                       u.OrganizationId, o.Name AS OrgName,
                       u.SiteId, s.Name AS SiteName,
                       u.IsActive, u.CreatedAt
                FROM UserAccounts u
                LEFT JOIN Organizations o ON u.OrganizationId = o.Id
                LEFT JOIN Sites s ON u.SiteId = s.Id
                WHERE LOWER(u.Username) = LOWER(@Username) AND u.Password = @Password AND u.IsActive = 1
                LIMIT 1;";
            cmd.Parameters.AddWithValue("@Username", username.Trim());
            cmd.Parameters.AddWithValue("@Password", password);

            using var reader = await cmd.ExecuteReaderAsync();
            if (await reader.ReadAsync())
            {
                return new UserAccountItem
                {
                    Id = reader.GetString(0),
                    Username = reader.GetString(1),
                    Password = reader.GetString(2),
                    FullName = reader.GetString(3),
                    Email = reader.IsDBNull(4) ? "" : reader.GetString(4),
                    Role = reader.GetString(5),
                    OrganizationId = reader.IsDBNull(6) ? null : reader.GetString(6),
                    OrganizationName = reader.IsDBNull(7) ? "Tất cả Tổ chức (Toàn quyền)" : reader.GetString(7),
                    SiteId = reader.IsDBNull(8) ? null : reader.GetString(8),
                    SiteName = reader.IsDBNull(9) ? "Tất cả Sites" : reader.GetString(9),
                    IsActive = reader.GetInt32(10) == 1,
                    CreatedAt = DateTime.TryParse(reader.GetString(11), out var dt) ? dt : DateTime.Now
                };
            }
            return null;
        }

        public async Task<bool> UpdateUserPasswordAsync(string userId, string newPassword)
        {
            using var conn = new SqliteConnection(_connectionString);
            await conn.OpenAsync();

            using var cmd = conn.CreateCommand();
            cmd.CommandText = @"
                UPDATE UserAccounts
                SET Password = @Password
                WHERE Id = @Id;";
            cmd.Parameters.AddWithValue("@Id", userId);
            cmd.Parameters.AddWithValue("@Password", newPassword);

            int rows = await cmd.ExecuteNonQueryAsync();
            return rows > 0;
        }
    }
}

