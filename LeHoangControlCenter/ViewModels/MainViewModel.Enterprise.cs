using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LeHoangControlCenter.Models;
using LeHoangControlCenter.Services;

namespace LeHoangControlCenter.ViewModels
{
    public partial class MainViewModel
    {
        // =========================================================
        // ENTERPRISE DATA COLLECTIONS (STORED IN SQLITE DATABASE)
        // =========================================================
        public ObservableCollection<OrganizationItem> Organizations { get; } = new();
        public ObservableCollection<SiteItem> Sites { get; } = new();
        public ObservableCollection<EnterpriseRobotItem> EnterpriseRobots { get; } = new();

        // Filtered Collections (Hierarchical 3-column drill-down: Org -> Sites -> Robots)
        public ObservableCollection<OrganizationItem> FilteredOrganizations { get; } = new();
        public ObservableCollection<SiteItem> FilteredSites { get; } = new();
        public ObservableCollection<EnterpriseRobotItem> FilteredRobots { get; } = new();

        [ObservableProperty]
        private bool _hasNoFilteredOrganizations;

        [ObservableProperty]
        private bool _hasNoFilteredSites;

        [ObservableProperty]
        private bool _hasNoFilteredRobots;
        public ObservableCollection<UserAccountItem> UserAccounts { get; } = new();
        public ObservableCollection<DeliveryOrderItem> EnterpriseDeliveryOrders { get; } = new();
        public ObservableCollection<AlertLogItem> EnterpriseAlertLogs { get; } = new();

        // Scope Dropdown Filter Options
        public ObservableCollection<string> OrganizationFilterOptions { get; } = new();
        public ObservableCollection<string> SiteFilterOptions { get; } = new();

        [ObservableProperty]
        private string _selectedOrgFilter = "Tất cả tổ chức";

        [ObservableProperty]
        private string _selectedSiteFilter = "Tất cả site";

        // Current Active User (Profile)
        [ObservableProperty]
        private UserAccountItem? _currentUser;

        [ObservableProperty]
        private string _currentUserBadge = "👑 SuperAdmin";

        // =========================================================
        // USER AUTHENTICATION, PROFILE & SESSION
        // =========================================================
        [ObservableProperty]
        private bool _isLoggedIn = false;

        [ObservableProperty]
        private bool _isLoginScreenVisible = true;

        [ObservableProperty]
        private string _loginUsername = "admin";

        [ObservableProperty]
        private string _loginPassword = "123456";

        [ObservableProperty]
        private string _loginErrorMessage = string.Empty;

        [ObservableProperty]
        private bool _hasLoginError = false;

        [ObservableProperty]
        private string _userAvatarInitials = "LH";

        [ObservableProperty]
        private bool _isProfileMenuOpen = false;

        // Change Password Modal
        [ObservableProperty]
        private bool _isChangePasswordDialogVisible = false;

        [ObservableProperty]
        private string _oldPassword = string.Empty;

        [ObservableProperty]
        private string _newPassword = string.Empty;

        [ObservableProperty]
        private string _confirmNewPassword = string.Empty;

        [ObservableProperty]
        private string _changePasswordErrorMessage = string.Empty;

        [ObservableProperty]
        private bool _hasChangePasswordError = false;

        [ObservableProperty]
        private string _changePasswordSuccessMessage = string.Empty;

        [ObservableProperty]
        private bool _hasChangePasswordSuccess = false;

        // Selected Items in DataGrids
        [ObservableProperty]
        private OrganizationItem? _selectedOrganization;

        [ObservableProperty]
        private SiteItem? _selectedSite;

        [ObservableProperty]
        private EnterpriseRobotItem? _selectedEnterpriseRobot;

        [ObservableProperty]
        private UserAccountItem? _selectedUserAccount;

        // =========================================================
        // DIALOG STATES & EDITING OBJECTS FOR CRUD
        // =========================================================
        [ObservableProperty]
        private bool _isOrgDialogVisible;

        [ObservableProperty]
        private bool _isSiteDialogVisible;

        [ObservableProperty]
        private bool _isRobotDialogVisible;

        [ObservableProperty]
        private bool _isUserDialogVisible;

        [ObservableProperty]
        private string _crudDialogTitle = string.Empty;

        [ObservableProperty]
        private bool _isEditMode;

        // Form Binding Models
        [ObservableProperty]
        private OrganizationItem _editingOrg = new();

        [ObservableProperty]
        private SiteItem _editingSite = new();

        [ObservableProperty]
        private EnterpriseRobotItem _editingRobot = new();

        [ObservableProperty]
        private UserAccountItem _editingUser = new();

        // User Account Organization & Site Selection
        [ObservableProperty]
        private OrganizationItem? _editingUserSelectedOrg;

        [ObservableProperty]
        private SiteItem? _editingUserSelectedSite;

        public ObservableCollection<SiteItem> UserDialogAvailableSites { get; } = new();

        partial void OnEditingUserSelectedOrgChanged(OrganizationItem? value)
        {
            UpdateUserDialogSites();
        }

        public void UpdateUserDialogSites()
        {
            UserDialogAvailableSites.Clear();
            if (EditingUserSelectedOrg != null)
            {
                var matchingSites = Sites.Where(s => s.OrganizationId == EditingUserSelectedOrg.Id).ToList();
                foreach (var s in matchingSites)
                {
                    UserDialogAvailableSites.Add(s);
                }
            }

            if (EditingUserSelectedSite != null && !UserDialogAvailableSites.Contains(EditingUserSelectedSite))
            {
                EditingUserSelectedSite = UserDialogAvailableSites.FirstOrDefault();
            }
            else if (EditingUserSelectedSite == null && UserDialogAvailableSites.Count > 0)
            {
                EditingUserSelectedSite = UserDialogAvailableSites.FirstOrDefault();
            }
        }

        // =========================================================
        // INITIALIZATION
        // =========================================================
        public async Task InitializeEnterpriseDataAsync()
        {
            try
            {
                var db = DatabaseService.Instance;

                // 1. Tải Organizations
                var orgs = await db.GetOrganizationsAsync();
                Organizations.Clear();
                OrganizationFilterOptions.Clear();
                OrganizationFilterOptions.Add("Tất cả tổ chức");
                foreach (var org in orgs)
                {
                    Organizations.Add(org);
                    OrganizationFilterOptions.Add(org.Name);
                }

                // 2. Tải Sites
                var sites = await db.GetSitesAsync();
                Sites.Clear();
                SiteFilterOptions.Clear();
                SiteFilterOptions.Add("Tất cả site");
                foreach (var site in sites)
                {
                    Sites.Add(site);
                    SiteFilterOptions.Add(site.Name);
                }

                // 3. Tải Enterprise Robots
                var robots = await db.GetRobotsAsync();
                EnterpriseRobots.Clear();
                foreach (var robot in robots)
                {
                    if (IsConnected && string.Equals(robot.Ip?.Trim(), RobotIp?.Trim(), StringComparison.OrdinalIgnoreCase))
                    {
                        robot.Status = IsCharging ? "Charging" : "Online";
                        robot.BatteryLevel = BatteryLevel;
                        robot.IsCharging = IsCharging;
                    }
                    EnterpriseRobots.Add(robot);
                }

                // Tự động chọn Robot khả dụng (ưu tiên 192.168.0.44 hoặc robot đầu tiên)
                var defaultBot = EnterpriseRobots.FirstOrDefault(r => r.Ip == "192.168.0.44") ?? EnterpriseRobots.FirstOrDefault();
                if (defaultBot != null)
                {
                    SelectedEnterpriseRobot = defaultBot;
                    RobotIp = defaultBot.Ip;
                    RobotPort = defaultBot.Port;
                    RobotId = defaultBot.Name;
                }

                _ = RefreshAllRobotsOnlineStatusAsync();

                // 4. Tải Users
                var users = await db.GetUserAccountsAsync();
                UserAccounts.Clear();
                foreach (var user in users)
                {
                    UserAccounts.Add(user);
                }

                // Chỉ thiết lập người dùng mặc định khi chưa có phiên đăng nhập
                if (CurrentUser == null && !IsLoggedIn)
                {
                    CurrentUser = users.FirstOrDefault(u => u.Role == "SuperAdmin") ?? users.FirstOrDefault();
                    if (CurrentUser != null)
                    {
                        CurrentUserBadge = $"👑 {CurrentUser.FullName} ({CurrentUser.Role})";
                        UpdateUserAvatarInitials(CurrentUser.FullName);
                    }
                }
                else if (CurrentUser != null)
                {
                    // Giữ nguyên tài khoản đang đăng nhập, làm mới thông tin nếu có
                    var match = users.FirstOrDefault(u => u.Id == CurrentUser.Id || string.Equals(u.Username, CurrentUser.Username, StringComparison.OrdinalIgnoreCase));
                    if (match != null)
                    {
                        CurrentUser.Role = match.Role;
                        CurrentUser.OrganizationId = match.OrganizationId;
                        CurrentUser.OrganizationName = match.OrganizationName;
                        CurrentUser.SiteId = match.SiteId;
                        CurrentUser.SiteName = match.SiteName;
                        CurrentUser.FullName = match.FullName;
                        CurrentUserBadge = $"👑 {CurrentUser.FullName} ({CurrentUser.Role})";
                        UpdateUserAvatarInitials(CurrentUser.FullName);
                    }
                }

                // 5. Tải Delivery Orders & Alerts
                var orders = await db.GetDeliveryOrdersAsync();
                EnterpriseDeliveryOrders.Clear();
                foreach (var o in orders)
                {
                    EnterpriseDeliveryOrders.Add(o);
                }

                var alerts = await db.GetAlertLogsAsync();
                EnterpriseAlertLogs.Clear();
                foreach (var a in alerts)
                {
                    EnterpriseAlertLogs.Add(a);
                }

                AddLog("[CƠ SỞ DỮ LIỆU] Đã nạp thành công dữ liệu Doanh nghiệp từ lehoang_fleet.db SQLite.");

                // Cập nhật phân cấp Org -> Sites -> Robots theo phân quyền tài khoản
                UpdateFilteredOrganizations();
            }
            catch (Exception ex)
            {
                AddLog($"[LỖI CƠ SỞ DỮ LIỆU] Không thể tải dữ liệu: {ex.Message}");
            }
        }

        // =========================================================
        // HIERARCHICAL SELECTION DRILL-DOWN (ORG -> SITES -> ROBOTS)
        // =========================================================
        partial void OnCurrentUserChanged(UserAccountItem? value)
        {
            UpdateFilteredOrganizations();
            ApplyScopeFilter();
        }

        partial void OnSelectedOrganizationChanged(OrganizationItem? value)
        {
            UpdateFilteredSites();
        }

        partial void OnSelectedSiteChanged(SiteItem? value)
        {
            UpdateFilteredRobots();
        }

        partial void OnSelectedEnterpriseRobotChanged(EnterpriseRobotItem? value)
        {
            if (value != null)
            {
                RobotIp = value.Ip;
                RobotPort = value.Port;
                RobotId = value.Name;
                AddLog($"[CHỌN ROBOT] Đã chọn robot: {value.Name} (IP: {value.Ip}:{value.Port}).");
                _ = ConnectEnterpriseRobotAsync(value);
            }
        }

        public void UpdateFilteredOrganizations()
        {
            FilteredOrganizations.Clear();

            // Phân quyền theo vai trò và phạm vi tài khoản:
            // - SuperAdmin (hoặc chưa đăng nhập / không gán tổ chức): xem toàn bộ tổ chức
            // - OrgAdmin / SiteManager / Operator: chỉ xem tổ chức mà tài khoản được gán
            if (CurrentUser == null || 
                string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase) || 
                (string.IsNullOrEmpty(CurrentUser.OrganizationId) && string.IsNullOrEmpty(CurrentUser.SiteId)))
            {
                foreach (var org in Organizations)
                {
                    FilteredOrganizations.Add(org);
                }
            }
            else
            {
                // Tìm tổ chức theo OrganizationId hoặc OrganizationName của CurrentUser
                var userOrg = Organizations.FirstOrDefault(o => 
                    (!string.IsNullOrEmpty(CurrentUser.OrganizationId) && string.Equals(o.Id, CurrentUser.OrganizationId, StringComparison.OrdinalIgnoreCase)) ||
                    (!string.IsNullOrEmpty(CurrentUser.OrganizationName) && string.Equals(o.Name, CurrentUser.OrganizationName, StringComparison.OrdinalIgnoreCase)));

                // Nếu user chỉ có SiteId mà chưa có OrganizationId, suy ra Organization từ Site
                if (userOrg == null && !string.IsNullOrEmpty(CurrentUser.SiteId))
                {
                    var userSite = Sites.FirstOrDefault(s => string.Equals(s.Id, CurrentUser.SiteId, StringComparison.OrdinalIgnoreCase));
                    if (userSite != null)
                    {
                        userOrg = Organizations.FirstOrDefault(o => string.Equals(o.Id, userSite.OrganizationId, StringComparison.OrdinalIgnoreCase));
                    }
                }

                if (userOrg != null)
                {
                    FilteredOrganizations.Add(userOrg);
                }
                else if (Organizations.Count > 0)
                {
                    FilteredOrganizations.Add(Organizations[0]);
                }
            }

            HasNoFilteredOrganizations = FilteredOrganizations.Count == 0;

            // Cập nhật các lựa chọn cho thanh ComboBox Tổ chức trên Dashboard
            OrganizationFilterOptions.Clear();
            if (CurrentUser == null || string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase))
            {
                OrganizationFilterOptions.Add("Tất cả tổ chức");
                foreach (var org in FilteredOrganizations)
                {
                    OrganizationFilterOptions.Add(org.Name);
                }
            }
            else
            {
                foreach (var org in FilteredOrganizations)
                {
                    OrganizationFilterOptions.Add(org.Name);
                }
            }

            if (FilteredOrganizations.Count > 0)
            {
                SelectedOrgFilter = FilteredOrganizations[0].Name;
            }
            else
            {
                SelectedOrgFilter = "Tất cả tổ chức";
            }

            if (SelectedOrganization == null || !FilteredOrganizations.Any(o => o.Id == SelectedOrganization.Id))
            {
                SelectedOrganization = FilteredOrganizations.FirstOrDefault();
            }
            else
            {
                UpdateFilteredSites();
            }
        }

        public void UpdateFilteredSites()
        {
            FilteredSites.Clear();
            if (SelectedOrganization == null)
            {
                SelectedSite = null;
                UpdateFilteredRobots();
                HasNoFilteredSites = true;
                SiteFilterOptions.Clear();
                SiteFilterOptions.Add("Tất cả site");
                SelectedSiteFilter = "Tất cả site";
                return;
            }

            var matching = Sites.Where(s => string.Equals(s.OrganizationId, SelectedOrganization.Id, StringComparison.OrdinalIgnoreCase));

            // Phân quyền theo Site: Nếu tài khoản là SiteManager hoặc Operator và có gán SiteId cụ thể
            if (CurrentUser != null && 
                !string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase) && 
                !string.Equals(CurrentUser.Role, "OrgAdmin", StringComparison.OrdinalIgnoreCase) && 
                !string.IsNullOrEmpty(CurrentUser.SiteId))
            {
                matching = matching.Where(s => 
                    string.Equals(s.Id, CurrentUser.SiteId, StringComparison.OrdinalIgnoreCase) ||
                    (!string.IsNullOrEmpty(CurrentUser.SiteName) && string.Equals(s.Name, CurrentUser.SiteName, StringComparison.OrdinalIgnoreCase)));
            }

            foreach (var site in matching)
            {
                FilteredSites.Add(site);
            }
            HasNoFilteredSites = FilteredSites.Count == 0;

            // Cập nhật các lựa chọn cho thanh ComboBox Chi nhánh trên Dashboard
            SiteFilterOptions.Clear();
            if (CurrentUser == null || string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase) || string.Equals(CurrentUser.Role, "OrgAdmin", StringComparison.OrdinalIgnoreCase))
            {
                SiteFilterOptions.Add("Tất cả site");
                foreach (var site in FilteredSites)
                {
                    SiteFilterOptions.Add(site.Name);
                }
            }
            else
            {
                foreach (var site in FilteredSites)
                {
                    SiteFilterOptions.Add(site.Name);
                }
            }

            if (FilteredSites.Count > 0)
            {
                SelectedSiteFilter = FilteredSites[0].Name;
            }
            else
            {
                SelectedSiteFilter = "Tất cả site";
            }

            // Nếu site hiện tại không thuộc tổ chức mới, tự động chọn site đầu tiên
            if (SelectedSite == null || !FilteredSites.Any(s => s.Id == SelectedSite.Id))
            {
                SelectedSite = FilteredSites.FirstOrDefault();
            }
            else
            {
                UpdateFilteredRobots();
            }
        }

        public void UpdateFilteredRobots()
        {
            FilteredRobots.Clear();

            // Lọc theo Site đang chọn và phân quyền tài khoản (RBAC Multi-tenant)
            IEnumerable<EnterpriseRobotItem> source = EnterpriseRobots;

            if (CurrentUser != null && !string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase))
            {
                if (!string.IsNullOrEmpty(CurrentUser.SiteId) && !string.Equals(CurrentUser.Role, "OrgAdmin", StringComparison.OrdinalIgnoreCase))
                {
                    source = source.Where(r => string.Equals(r.SiteId, CurrentUser.SiteId, StringComparison.OrdinalIgnoreCase));
                }
                else if (!string.IsNullOrEmpty(CurrentUser.OrganizationId))
                {
                    source = source.Where(r => string.Equals(r.OrganizationId, CurrentUser.OrganizationId, StringComparison.OrdinalIgnoreCase));
                }
            }

            if (SelectedSite != null)
            {
                source = source.Where(r => string.Equals(r.SiteId, SelectedSite.Id, StringComparison.OrdinalIgnoreCase));
            }

            var matching = source.ToList();
            foreach (var robot in matching)
            {
                FilteredRobots.Add(robot);
            }
            HasNoFilteredRobots = FilteredRobots.Count == 0;

            if (SelectedEnterpriseRobot == null || !matching.Any(r => r.Id == SelectedEnterpriseRobot.Id))
            {
                SelectedEnterpriseRobot = FilteredRobots.FirstOrDefault();
            }
        }

        // =========================================================
        // FILTERING LOGIC WHEN DROPDOWNS CHANGE
        // =========================================================
        partial void OnSelectedOrgFilterChanged(string value)
        {
            ApplyScopeFilter();
        }

        partial void OnSelectedSiteFilterChanged(string value)
        {
            ApplyScopeFilter();
        }

        private async void ApplyScopeFilter()
        {
            try
            {
                var db = DatabaseService.Instance;
                string? orgId = null;
                string? siteId = null;

                if (!string.IsNullOrEmpty(SelectedOrgFilter) && SelectedOrgFilter != "Tất cả tổ chức")
                {
                    var org = Organizations.FirstOrDefault(o => o.Name == SelectedOrgFilter);
                    if (org != null) orgId = org.Id;
                }

                if (!string.IsNullOrEmpty(SelectedSiteFilter) && SelectedSiteFilter != "Tất cả site")
                {
                    var site = Sites.FirstOrDefault(s => s.Name == SelectedSiteFilter);
                    if (site != null) siteId = site.Id;
                }

                // Bắt buộc tuân thủ phân quyền của tài khoản hiện tại (RBAC Multi-tenant)
                if (CurrentUser != null && !string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase))
                {
                    if (!string.IsNullOrEmpty(CurrentUser.OrganizationId))
                    {
                        orgId = CurrentUser.OrganizationId;
                    }
                    if (!string.IsNullOrEmpty(CurrentUser.SiteId) && !string.Equals(CurrentUser.Role, "OrgAdmin", StringComparison.OrdinalIgnoreCase))
                    {
                        siteId = CurrentUser.SiteId;
                    }
                }

                // Cập nhật danh sách Site trong filter theo Org đã chọn
                if (orgId != null)
                {
                    var sitesInOrg = await db.GetSitesAsync(orgId);
                    if (CurrentUser != null && !string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(CurrentUser.SiteId))
                    {
                        sitesInOrg = sitesInOrg.Where(s => s.Id == CurrentUser.SiteId).ToList();
                    }

                    SiteFilterOptions.Clear();
                    if (CurrentUser == null || string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase) || string.Equals(CurrentUser.Role, "OrgAdmin", StringComparison.OrdinalIgnoreCase))
                    {
                        SiteFilterOptions.Add("Tất cả site");
                    }
                    foreach (var s in sitesInOrg)
                    {
                        SiteFilterOptions.Add(s.Name);
                    }
                }
                else
                {
                    var allSites = await db.GetSitesAsync();
                    SiteFilterOptions.Clear();
                    SiteFilterOptions.Add("Tất cả site");
                    foreach (var s in allSites)
                    {
                        SiteFilterOptions.Add(s.Name);
                    }
                }

                // Cập nhật danh sách Robot hiển thị
                var robots = await db.GetRobotsAsync(siteId, orgId);
                EnterpriseRobots.Clear();
                foreach (var r in robots)
                {
                    if (IsConnected && string.Equals(r.Ip?.Trim(), RobotIp?.Trim(), StringComparison.OrdinalIgnoreCase))
                    {
                        r.Status = IsCharging ? "Charging" : "Online";
                        r.BatteryLevel = BatteryLevel;
                        r.IsCharging = IsCharging;
                    }
                    EnterpriseRobots.Add(r);
                }

                // Đồng bộ FilteredRobots cho cả màn hình Dashboard & Quản lý Robots
                UpdateFilteredRobots();

                _ = RefreshAllRobotsOnlineStatusAsync();

                AddLog($"[BỘ LỌC PHÂN QUYỀN] Đang xem phạm vi: Tổ chức [{SelectedOrgFilter}] / Site [{SelectedSiteFilter}] ({EnterpriseRobots.Count} Robot)");
            }
            catch (Exception ex)
            {
                AddLog($"[LỖI LỌC] {ex.Message}");
            }
        }

        public void ApplyCloudCatalog(List<OrganizationItem> orgs, List<SiteItem> sites)
        {
            if (orgs == null || sites == null) return;
            try
            {
                // Lưu vào local SQLite để offline vẫn có dữ liệu và dọn dẹp các tổ chức/chi nhánh cũ không còn trên Cloud
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var db = DatabaseService.Instance;
                        var localOrgs = await db.GetOrganizationsAsync();
                        var cloudOrgIds = new System.Collections.Generic.HashSet<string>(orgs.Select(o => o.Id), StringComparer.OrdinalIgnoreCase);
                        foreach (var lo in localOrgs)
                        {
                            if (!cloudOrgIds.Contains(lo.Id))
                            {
                                await db.DeleteOrganizationAsync(lo.Id);
                            }
                        }

                        var localSites = await db.GetSitesAsync();
                        var cloudSiteIds = new System.Collections.Generic.HashSet<string>(sites.Select(s => s.Id), StringComparer.OrdinalIgnoreCase);
                        foreach (var ls in localSites)
                        {
                            if (!cloudSiteIds.Contains(ls.Id))
                            {
                                await db.DeleteSiteAsync(ls.Id);
                            }
                        }

                        foreach (var o in orgs) await db.SaveOrganizationAsync(o);
                        foreach (var s in sites) await db.SaveSiteAsync(s);
                    }
                    catch { }
                });

                Organizations.Clear();
                foreach (var o in orgs) Organizations.Add(o);

                Sites.Clear();
                foreach (var s in sites) Sites.Add(s);

                UpdateFilteredOrganizations();

                AddLog($"☁️ [ĐỒNG BỘ CLOUD] Đã nhận {orgs.Count} Tổ chức & {sites.Count} Chi nhánh từ Cloud Server.");
            }
            catch (Exception ex)
            {
                AddLog($"[LỖI ĐỒNG BỘ CLOUD] {ex.Message}");
            }
        }

        public void ApplyCloudUsers(List<UserAccountItem> cloudUsers)
        {
            if (cloudUsers == null || cloudUsers.Count == 0) return;
            try
            {
                foreach (var cu in cloudUsers)
                {
                    var existing = UserAccounts.FirstOrDefault(u => u.Id == cu.Id || string.Equals(u.Username, cu.Username, StringComparison.OrdinalIgnoreCase));
                    if (existing != null)
                    {
                        existing.FullName = cu.FullName;
                        existing.Role = cu.Role;
                        existing.OrganizationId = cu.OrganizationId;
                        existing.OrganizationName = cu.OrganizationName;
                        existing.SiteId = cu.SiteId;
                        existing.SiteName = cu.SiteName;
                        existing.IsActive = cu.IsActive;
                    }
                    else
                    {
                        UserAccounts.Add(cu);
                    }
                }

                // Cập nhật phân quyền ngay cho phiên đăng nhập hiện tại nếu có thay đổi từ Cloud
                if (CurrentUser != null)
                {
                    var updatedCurrent = cloudUsers.FirstOrDefault(u => u.Id == CurrentUser.Id || string.Equals(u.Username, CurrentUser.Username, StringComparison.OrdinalIgnoreCase));
                    if (updatedCurrent != null)
                    {
                        CurrentUser.Role = updatedCurrent.Role;
                        CurrentUser.OrganizationId = updatedCurrent.OrganizationId;
                        CurrentUser.OrganizationName = updatedCurrent.OrganizationName;
                        CurrentUser.SiteId = updatedCurrent.SiteId;
                        CurrentUser.SiteName = updatedCurrent.SiteName;
                        CurrentUser.FullName = updatedCurrent.FullName;
                        CurrentUserBadge = $"👑 {CurrentUser.FullName} ({CurrentUser.Role})";
                        UpdateFilteredOrganizations();
                        ApplyScopeFilter();
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[ApplyCloudUsers] error: {ex.Message}");
            }
        }

        public void ApplyCloudFleet(List<EnterpriseRobotItem> cloudRobots)
        {
            if (cloudRobots == null) return;
            try
            {
                // 1. Lưu đồng bộ vào SQLite cục bộ để hỗ trợ làm việc offline
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var db = DatabaseService.Instance;
                        var existingLocal = await db.GetRobotsAsync();
                        var cloudIds = new HashSet<string>(cloudRobots.Select(r => r.Id), StringComparer.OrdinalIgnoreCase);

                        // Dọn dẹp dữ liệu mock cũ không còn tồn tại trên Cloud
                        foreach (var localBot in existingLocal)
                        {
                            if (!cloudIds.Contains(localBot.Id))
                            {
                                await db.DeleteRobotAsync(localBot.Id);
                            }
                        }

                        foreach (var bot in cloudRobots)
                        {
                            await db.SaveRobotAsync(bot);
                        }
                    }
                    catch (Exception ex)
                    {
                        Debug.WriteLine($"[SQLite Save Robots Error] {ex.Message}");
                    }
                });

                // 2. Cập nhật EnterpriseRobots trên UI theo phạm vi phân quyền
                var scopedRobots = cloudRobots;
                if (CurrentUser != null && !string.Equals(CurrentUser.Role, "SuperAdmin", StringComparison.OrdinalIgnoreCase))
                {
                    if (!string.IsNullOrEmpty(CurrentUser.OrganizationId))
                    {
                        scopedRobots = scopedRobots.Where(r => string.Equals(r.OrganizationId, CurrentUser.OrganizationId, StringComparison.OrdinalIgnoreCase)).ToList();
                    }
                    if (!string.IsNullOrEmpty(CurrentUser.SiteId) && !string.Equals(CurrentUser.Role, "OrgAdmin", StringComparison.OrdinalIgnoreCase))
                    {
                        scopedRobots = scopedRobots.Where(r => string.Equals(r.SiteId, CurrentUser.SiteId, StringComparison.OrdinalIgnoreCase)).ToList();
                    }
                }

                EnterpriseRobots.Clear();
                foreach (var bot in scopedRobots)
                {
                    if (IsConnected && string.Equals(bot.Ip?.Trim(), RobotIp?.Trim(), StringComparison.OrdinalIgnoreCase))
                    {
                        bot.Status = IsCharging ? "Charging" : "Online";
                        bot.BatteryLevel = BatteryLevel;
                        bot.IsCharging = IsCharging;
                    }
                    EnterpriseRobots.Add(bot);
                }

                // 3. Cập nhật bộ lọc danh sách robot theo Site đang chọn
                UpdateFilteredRobots();

                AddLog($"☁️ [ĐỒNG BỘ CLOUD] Đã đồng bộ {cloudRobots.Count} Robot thực tế từ Cloud Server về Client (Hiển thị {scopedRobots.Count} Robot theo phân quyền).");
            }
            catch (Exception ex)
            {
                AddLog($"[LỖI ĐỒNG BỘ FLEET CLOUD] {ex.Message}");
            }
        }

        [RelayCommand]
        public async Task SyncFromCloudAsync()
        {
            if (_cloudBridge != null)
            {
                AddLog("☁️ Đang đồng bộ dữ liệu thực tế từ Cloud Server...");
                await _cloudBridge.SyncCatalogFromCloudAsync();
            }
        }

        // =========================================================
        // CRUD COMMANDS: ORGANIZATION
        // =========================================================
        [RelayCommand]
        public void OpenAddOrg()
        {
            IsEditMode = false;
            CrudDialogTitle = "Thêm Tổ Chức Mới (Organization)";
            EditingOrg = new OrganizationItem
            {
                Id = Guid.NewGuid().ToString("N")[..8].ToUpper(),
                CreatedAt = DateTime.Now
            };
            IsOrgDialogVisible = true;
        }

        [RelayCommand]
        public void OpenEditOrg(OrganizationItem? org = null)
        {
            var target = org ?? SelectedOrganization;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn một tổ chức để chỉnh sửa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            IsEditMode = true;
            CrudDialogTitle = $"Chỉnh Sửa Tổ Chức: {target.Name}";
            EditingOrg = new OrganizationItem
            {
                Id = target.Id,
                Name = target.Name,
                Code = target.Code,
                Description = target.Description,
                ContactPerson = target.ContactPerson,
                Phone = target.Phone,
                Email = target.Email,
                CreatedAt = target.CreatedAt,
                SiteCount = target.SiteCount,
                RobotCount = target.RobotCount
            };
            IsOrgDialogVisible = true;
        }

        [RelayCommand]
        public async Task SaveOrgAsync()
        {
            if (string.IsNullOrWhiteSpace(EditingOrg.Name))
            {
                MessageBox.Show("Tên tổ chức không được để trống.", "Thiếu thông tin", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            try
            {
                var db = DatabaseService.Instance;
                await db.SaveOrganizationAsync(EditingOrg);
                IsOrgDialogVisible = false;
                await InitializeEnterpriseDataAsync();
                AddLog($"[DOANH NGHIỆP] Đã lưu thành công tổ chức: {EditingOrg.Name}");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Lỗi khi lưu tổ chức: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        [RelayCommand]
        public async Task DeleteOrgAsync(OrganizationItem? org = null)
        {
            var target = org ?? SelectedOrganization;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn tổ chức cần xóa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            var confirm = MessageBox.Show(
                $"Bạn có chắc chắn muốn xóa tổ chức '{target.Name}'?\nLưu ý: Tất cả Site và Robot thuộc tổ chức này cũng sẽ bị xóa khỏi cơ sở dữ liệu!",
                "Xác nhận xóa tổ chức",
                MessageBoxButton.YesNo,
                MessageBoxImage.Warning);

            if (confirm == MessageBoxResult.Yes)
            {
                try
                {
                    var db = DatabaseService.Instance;
                    await db.DeleteOrganizationAsync(target.Id);
                    await InitializeEnterpriseDataAsync();
                    AddLog($"[DOANH NGHIỆP] Đã xóa tổ chức '{target.Name}'.");
                }
                catch (Exception ex)
                {
                    MessageBox.Show($"Lỗi khi xóa: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
                }
            }
        }

        [RelayCommand]
        public void CloseOrgDialog()
        {
            IsOrgDialogVisible = false;
        }

        // =========================================================
        // CRUD COMMANDS: SITE
        // =========================================================
        [RelayCommand]
        public void OpenAddSite()
        {
            if (Organizations.Count == 0)
            {
                MessageBox.Show("Cần tạo ít nhất một Tổ chức trước khi tạo Site!", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            IsEditMode = false;
            CrudDialogTitle = "Thêm Chi Nhánh / Site Mới";
            var defaultOrg = SelectedOrganization ?? Organizations.First();
            EditingSite = new SiteItem
            {
                Id = Guid.NewGuid().ToString("N")[..8].ToUpper(),
                OrganizationId = defaultOrg.Id,
                OrganizationName = defaultOrg.Name,
                CreatedAt = DateTime.Now
            };
            IsSiteDialogVisible = true;
        }

        [RelayCommand]
        public void OpenEditSite(SiteItem? site = null)
        {
            var target = site ?? SelectedSite;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn một Site để chỉnh sửa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            IsEditMode = true;
            CrudDialogTitle = $"Chỉnh Sửa Site: {target.Name}";
            EditingSite = new SiteItem
            {
                Id = target.Id,
                OrganizationId = target.OrganizationId,
                OrganizationName = target.OrganizationName,
                Name = target.Name,
                Code = target.Code,
                Address = target.Address,
                ContactPhone = target.ContactPhone,
                CreatedAt = target.CreatedAt,
                RobotCount = target.RobotCount
            };
            IsSiteDialogVisible = true;
        }

        [RelayCommand]
        public async Task SaveSiteAsync()
        {
            if (string.IsNullOrWhiteSpace(EditingSite.Name))
            {
                MessageBox.Show("Tên Site không được để trống.", "Thiếu thông tin", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            try
            {
                // Cập nhật tên tổ chức tương ứng
                var org = Organizations.FirstOrDefault(o => o.Id == EditingSite.OrganizationId);
                if (org != null) EditingSite.OrganizationName = org.Name;

                var db = DatabaseService.Instance;
                await db.SaveSiteAsync(EditingSite);
                IsSiteDialogVisible = false;
                await InitializeEnterpriseDataAsync();
                AddLog($"[DOANH NGHIỆP] Đã lưu thành công Site: {EditingSite.Name}");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Lỗi khi lưu Site: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        [RelayCommand]
        public async Task DeleteSiteAsync(SiteItem? site = null)
        {
            var target = site ?? SelectedSite;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn Site cần xóa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            var confirm = MessageBox.Show(
                $"Bạn có chắc chắn muốn xóa Site '{target.Name}'?\nTất cả Robot gán với Site này sẽ bị xóa!",
                "Xác nhận xóa Site",
                MessageBoxButton.YesNo,
                MessageBoxImage.Warning);

            if (confirm == MessageBoxResult.Yes)
            {
                try
                {
                    var db = DatabaseService.Instance;
                    await db.DeleteSiteAsync(target.Id);
                    await InitializeEnterpriseDataAsync();
                    AddLog($"[DOANH NGHIỆP] Đã xóa Site '{target.Name}'.");
                }
                catch (Exception ex)
                {
                    MessageBox.Show($"Lỗi khi xóa: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
                }
            }
        }

        [RelayCommand]
        public void CloseSiteDialog()
        {
            IsSiteDialogVisible = false;
        }

        // =========================================================
        // CRUD COMMANDS: ROBOTS
        // =========================================================
        [RelayCommand]
        public void OpenAddRobot()
        {
            if (FilteredSites.Count == 0 && Sites.Count == 0)
            {
                MessageBox.Show("Chưa có Chi nhánh (Site) nào được phân quyền hoặc tải từ Cloud Server!", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            IsEditMode = false;
            CrudDialogTitle = "Thêm Robot Mới Vào Đội Xe";
            var defaultSite = SelectedSite ?? FilteredSites.FirstOrDefault() ?? Sites.First();
            EditingRobot = new EnterpriseRobotItem
            {
                Id = Guid.NewGuid().ToString("N")[..8].ToUpper(),
                SiteId = defaultSite.Id,
                SiteName = defaultSite.Name,
                OrganizationId = defaultSite.OrganizationId,
                OrganizationName = Organizations.FirstOrDefault(o => o.Id == defaultSite.OrganizationId)?.Name ?? "",
                Name = "LH-ORIONSTAR-" + (EnterpriseRobots.Count + 1).ToString("D2"),
                Model = "OrionStar Lucki",
                Ip = "192.168.0." + (120 + EnterpriseRobots.Count),
                Port = 8899,
                Status = "Online",
                BatteryLevel = 100,
                AssignedMap = "tăng 2-1110154527"
            };
            IsRobotDialogVisible = true;
        }

        [RelayCommand]
        public void OpenEditRobot(EnterpriseRobotItem? robot = null)
        {
            var target = robot ?? SelectedEnterpriseRobot;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn một Robot để chỉnh sửa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            IsEditMode = true;
            CrudDialogTitle = $"Chỉnh Sửa Robot: {target.Name}";
            EditingRobot = new EnterpriseRobotItem
            {
                Id = target.Id,
                SiteId = target.SiteId,
                SiteName = target.SiteName,
                OrganizationId = target.OrganizationId,
                OrganizationName = target.OrganizationName,
                Name = target.Name,
                Model = target.Model,
                Ip = target.Ip,
                Port = target.Port,
                Status = target.Status,
                BatteryLevel = target.BatteryLevel,
                AssignedMap = target.AssignedMap
            };
            IsRobotDialogVisible = true;
        }

        [RelayCommand]
        public async Task SaveRobotAsync()
        {
            if (string.IsNullOrWhiteSpace(EditingRobot.Name) || string.IsNullOrWhiteSpace(EditingRobot.Ip))
            {
                MessageBox.Show("Tên Robot và IP không được để trống.", "Thiếu thông tin", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            try
            {
                var site = Sites.FirstOrDefault(s => s.Id == EditingRobot.SiteId);
                if (site != null)
                {
                    EditingRobot.SiteName = site.Name;
                    EditingRobot.OrganizationId = site.OrganizationId;
                    var org = Organizations.FirstOrDefault(o => o.Id == site.OrganizationId);
                    if (org != null) EditingRobot.OrganizationName = org.Name;
                }

                var db = DatabaseService.Instance;
                await db.SaveRobotAsync(EditingRobot);

                // Tự động đẩy thông tin Robot lên Cloud Server trung tâm
                if (_cloudBridge != null)
                {
                    _ = _cloudBridge.PushRobotToCloudAsync(EditingRobot);
                }

                IsRobotDialogVisible = false;
                await InitializeEnterpriseDataAsync();
                AddLog($"[DOANH NGHIỆP] Đã lưu và đẩy Robot lên Cloud: {EditingRobot.Name} (IP: {EditingRobot.Ip})");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Lỗi khi lưu Robot: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        [RelayCommand]
        public async Task DeleteRobotAsync(EnterpriseRobotItem? robot = null)
        {
            var target = robot ?? SelectedEnterpriseRobot;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn Robot cần xóa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            var confirm = MessageBox.Show(
                $"Bạn có chắc chắn muốn xóa Robot '{target.Name}' (IP: {target.Ip}) khỏi hệ thống?\nRobot sẽ được gỡ bỏ khỏi cơ sở dữ liệu và đồng bộ xóa khỏi Cloud Server.",
                "Xác nhận xóa Robot",
                MessageBoxButton.YesNo,
                MessageBoxImage.Warning);

            if (confirm == MessageBoxResult.Yes)
            {
                try
                {
                    var db = DatabaseService.Instance;
                    await db.DeleteRobotAsync(target.Id, target.Ip);

                    // Xóa Robot khỏi Cloud Server (chờ hoàn tất để không bị Cloud đồng bộ ngược lại)
                    if (_cloudBridge != null)
                    {
                        await _cloudBridge.DeleteRobotFromCloudAsync(target.Id);
                        if (!string.IsNullOrEmpty(target.Ip) && target.Ip != target.Id)
                        {
                            await _cloudBridge.DeleteRobotFromCloudAsync(target.Ip);
                        }
                    }

                    // Gỡ bỏ ngay khỏi danh sách hiển thị
                    EnterpriseRobots.Remove(target);
                    FilteredRobots.Remove(target);
                    var inFleet = FleetRobots.FirstOrDefault(r => r.Ip == target.Ip);
                    if (inFleet != null) FleetRobots.Remove(inFleet);

                    if (SelectedEnterpriseRobot?.Id == target.Id)
                    {
                        SelectedEnterpriseRobot = FilteredRobots.FirstOrDefault();
                    }

                    // Nếu robot đang xóa là robot đang kết nối hiện tại -> ngắt kết nối
                    if (string.Equals(RobotIp?.Trim(), target.Ip?.Trim(), StringComparison.OrdinalIgnoreCase))
                    {
                        if (IsConnected)
                        {
                            await _robotService.DisconnectAsync();
                            IsConnected = false;
                            RobotState = "OFFLINE";
                        }
                    }

                    await InitializeEnterpriseDataAsync();
                    AddLog($"[DOANH NGHIỆP] Đã xóa thành công Robot '{target.Name}' (IP: {target.Ip}).");
                    MessageBox.Show($"Đã xóa thành công Robot '{target.Name}' (IP: {target.Ip}) khỏi hệ thống!", "Thành công", MessageBoxButton.OK, MessageBoxImage.Information);
                }
                catch (Exception ex)
                {
                    MessageBox.Show($"Lỗi khi xóa: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
                }
            }
        }

        [RelayCommand]
        public void CloseRobotDialog()
        {
            IsRobotDialogVisible = false;
        }

        [RelayCommand]
        public async Task ConnectEnterpriseRobotAsync(EnterpriseRobotItem? robot = null)
        {
            var target = robot ?? SelectedEnterpriseRobot;
            if (target == null) return;

            if (SelectedEnterpriseRobot != target)
            {
                SelectedEnterpriseRobot = target;
            }

            RobotIp = target.Ip;
            RobotPort = target.Port;
            RobotId = target.Name;

            if (!string.IsNullOrEmpty(target.AssignedMap) && target.AssignedMap != CurrentMap)
            {
                SwitchToMapLocal(target.AssignedMap);
            }

            AddLog($"[ĐIỀU KHIỂN ĐỘI XE] Đang chuyển điều khiển sang Robot: {target.Name} ({target.Ip}:{target.Port})...");
            try
            {
                if (IsConnected)
                {
                    await _robotService.DisconnectAsync();
                }
                await _robotService.ConnectAsync(RobotIp, RobotPort);
                target.Status = "Online";
                target.LastSeen = DateTime.Now;
            }
            catch (Exception ex)
            {
                AddLog($"[KẾT NỐI THẤT BẠI] {ex.Message}");
            }
        }

        public async Task RefreshAllRobotsOnlineStatusAsync()
        {
            try
            {
                var bots = EnterpriseRobots.ToList();
                if (bots.Count == 0) return;

                var tasks = bots.Select(async bot =>
                {
                    if (IsConnected && string.Equals(bot.Ip?.Trim(), RobotIp?.Trim(), StringComparison.OrdinalIgnoreCase))
                    {
                        Application.Current?.Dispatcher?.Invoke(() =>
                        {
                            bot.Status = IsCharging ? "Charging" : "Online";
                            bot.BatteryLevel = BatteryLevel;
                            bot.IsCharging = IsCharging;
                            bot.LastSeen = DateTime.Now;
                        });
                        return;
                    }

                    int port = bot.Port > 0 ? bot.Port : 8899;
                    bool isOnline = await NetworkDiagnosticService.QuickCheckRobotOnlineAsync(bot.Ip ?? "", port, 400);
                    Application.Current?.Dispatcher?.Invoke(() =>
                    {
                        bot.Status = isOnline ? "Online" : "Offline";
                        if (isOnline)
                        {
                            bot.LastSeen = DateTime.Now;
                        }
                    });
                });

                await Task.WhenAll(tasks);

                Application.Current?.Dispatcher?.Invoke(() =>
                {
                    AvailableFleetCount = EnterpriseRobots.Count(r => r.IsOnline);
                });
            }
            catch { }
        }

        // =========================================================
        // CRUD COMMANDS: USER ACCOUNTS & PERMISSIONS
        // =========================================================
        [RelayCommand]
        public void OpenAddUser()
        {
            IsEditMode = false;
            CrudDialogTitle = "Thêm Tài Khoản Người Dùng Mới";
            EditingUser = new UserAccountItem
            {
                Id = Guid.NewGuid().ToString("N")[..8].ToUpper(),
                Password = "123456",
                Role = "SiteManager",
                IsActive = true,
                CreatedAt = DateTime.Now
            };

            EditingUserSelectedOrg = SelectedOrganization ?? Organizations.FirstOrDefault();
            UpdateUserDialogSites();
            EditingUserSelectedSite = SelectedSite ?? UserDialogAvailableSites.FirstOrDefault();

            IsUserDialogVisible = true;
        }

        [RelayCommand]
        public void OpenEditUser(UserAccountItem? user = null)
        {
            var target = user ?? SelectedUserAccount;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn tài khoản để chỉnh sửa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            IsEditMode = true;
            CrudDialogTitle = $"Chỉnh Sửa Người Dùng: {target.FullName}";
            EditingUser = new UserAccountItem
            {
                Id = target.Id,
                Username = target.Username,
                Password = target.Password,
                FullName = target.FullName,
                Email = target.Email,
                Phone = target.Phone,
                Role = target.Role,
                OrganizationId = target.OrganizationId,
                OrganizationName = target.OrganizationName,
                SiteId = target.SiteId,
                SiteName = target.SiteName,
                IsActive = target.IsActive,
                CreatedAt = target.CreatedAt
            };

            EditingUserSelectedOrg = Organizations.FirstOrDefault(o => o.Id == target.OrganizationId) ?? Organizations.FirstOrDefault();
            UpdateUserDialogSites();
            EditingUserSelectedSite = UserDialogAvailableSites.FirstOrDefault(s => s.Id == target.SiteId) ?? UserDialogAvailableSites.FirstOrDefault();

            IsUserDialogVisible = true;
        }

        [RelayCommand]
        public async Task SaveUserAsync()
        {
            if (string.IsNullOrWhiteSpace(EditingUser.Username) || string.IsNullOrWhiteSpace(EditingUser.FullName))
            {
                MessageBox.Show("Tên đăng nhập và Họ tên không được để trống.", "Thiếu thông tin", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            if (string.IsNullOrWhiteSpace(EditingUser.Password))
            {
                EditingUser.Password = "123456";
            }

            // Gán Phân quyền Tổ chức và Site theo lựa chọn
            if (EditingUser.Role == "SuperAdmin")
            {
                EditingUser.OrganizationId = null;
                EditingUser.OrganizationName = "Tất cả Tổ chức (Toàn quyền)";
                EditingUser.SiteId = null;
                EditingUser.SiteName = "Tất cả Sites";
            }
            else
            {
                if (EditingUserSelectedOrg != null)
                {
                    EditingUser.OrganizationId = EditingUserSelectedOrg.Id;
                    EditingUser.OrganizationName = EditingUserSelectedOrg.Name;
                }
                else
                {
                    EditingUser.OrganizationId = null;
                    EditingUser.OrganizationName = "Chưa gán Tổ chức";
                }

                if (EditingUserSelectedSite != null)
                {
                    EditingUser.SiteId = EditingUserSelectedSite.Id;
                    EditingUser.SiteName = EditingUserSelectedSite.Name;
                }
                else
                {
                    EditingUser.SiteId = null;
                    EditingUser.SiteName = "Tất cả Sites";
                }
            }

            try
            {
                var db = DatabaseService.Instance;
                await db.SaveUserAccountAsync(EditingUser);
                IsUserDialogVisible = false;
                await InitializeEnterpriseDataAsync();
                AddLog($"[PHÂN QUYỀN] Đã lưu thông tin người dùng: {EditingUser.FullName} (Vai trò: {EditingUser.Role}, Tổ chức: {EditingUser.OrganizationName}, Site: {EditingUser.SiteName})");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Lỗi khi lưu tài khoản: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        [RelayCommand]
        public async Task DeleteUserAsync(UserAccountItem? user = null)
        {
            var target = user ?? SelectedUserAccount;
            if (target == null)
            {
                MessageBox.Show("Vui lòng chọn tài khoản cần xóa.", "Thông báo", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            if (target.Role == "SuperAdmin" && UserAccounts.Count(u => u.Role == "SuperAdmin") <= 1)
            {
                MessageBox.Show("Không thể xóa tài khoản SuperAdmin duy nhất của hệ thống!", "Từ chối thao tác", MessageBoxButton.OK, MessageBoxImage.Stop);
                return;
            }

            var confirm = MessageBox.Show(
                $"Bạn có chắc chắn muốn xóa tài khoản '{target.FullName}' ({target.Username})?",
                "Xác nhận xóa người dùng",
                MessageBoxButton.YesNo,
                MessageBoxImage.Warning);

            if (confirm == MessageBoxResult.Yes)
            {
                try
                {
                    var db = DatabaseService.Instance;
                    await db.DeleteUserAccountAsync(target.Id);
                    await InitializeEnterpriseDataAsync();
                    AddLog($"[PHÂN QUYỀN] Đã xóa người dùng '{target.FullName}'.");
                }
                catch (Exception ex)
                {
                    MessageBox.Show($"Lỗi khi xóa: {ex.Message}", "Lỗi", MessageBoxButton.OK, MessageBoxImage.Error);
                }
            }
        }

        [RelayCommand]
        public void CloseUserDialog()
        {
            IsUserDialogVisible = false;
        }

        // =========================================================
        // VIEW NAVIGATION & VISIBILITY FOR ENTERPRISE MODULES
        // =========================================================
        [ObservableProperty]
        private Visibility _enterpriseVisibility = Visibility.Collapsed;

        [ObservableProperty]
        private Visibility _usersVisibility = Visibility.Collapsed;

        [ObservableProperty]
        private Visibility _ordersVisibility = Visibility.Collapsed;

        [ObservableProperty]
        private Visibility _alertsVisibility = Visibility.Collapsed;

        [ObservableProperty]
        private string _enterpriseNavBg = "Transparent";
        [ObservableProperty]
        private string _enterpriseNavFg = "#94A3B8";

        [ObservableProperty]
        private string _usersNavBg = "Transparent";
        [ObservableProperty]
        private string _usersNavFg = "#94A3B8";

        [ObservableProperty]
        private string _ordersNavBg = "Transparent";
        [ObservableProperty]
        private string _ordersNavFg = "#94A3B8";

        [ObservableProperty]
        private string _alertsNavBg = "Transparent";
        [ObservableProperty]
        private string _alertsNavFg = "#94A3B8";

        [ObservableProperty]
        private Visibility _rightPanelVisibility = Visibility.Collapsed;

        
        [RelayCommand]
        public void ToggleRightPanel()
        {
            RightPanelVisibility = RightPanelVisibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible;
            AddLog(RightPanelVisibility == Visibility.Visible ? "[GIAO DIỆN] Đã mở bảng điều khiển phụ bên phải." : "[GIAO DIỆN] Đã thu gọn bảng điều khiển phụ.");
        }

        public void HideAllViews()
        {
            DashboardVisibility = Visibility.Collapsed;
            MapsVisibility = Visibility.Collapsed;
            TasksVisibility = Visibility.Collapsed;
            EnterpriseVisibility = Visibility.Collapsed;
            UsersVisibility = Visibility.Collapsed;
            OrdersVisibility = Visibility.Collapsed;
            AlertsVisibility = Visibility.Collapsed;

            DashboardNavBg = "Transparent"; DashboardNavFg = "#94A3B8";
            MapsNavBg = "Transparent"; MapsNavFg = "#94A3B8";
            TasksNavBg = "Transparent"; TasksNavFg = "#94A3B8";
            EnterpriseNavBg = "Transparent"; EnterpriseNavFg = "#94A3B8";
            UsersNavBg = "Transparent"; UsersNavFg = "#94A3B8";
            OrdersNavBg = "Transparent"; OrdersNavFg = "#94A3B8";
            AlertsNavBg = "Transparent"; AlertsNavFg = "#94A3B8";
        }

        [RelayCommand]
        public void ShowEnterprise()
        {
            HideAllViews();
            EnterpriseVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            EnterpriseNavBg = "#162034";
            EnterpriseNavFg = "#00E5FF";
            SelectedNavMenu = "Enterprise";
            AddLog("[ĐIỀU HƯỚNG] Mở màn hình Quản lý Doanh nghiệp: Tổ chức & Chi nhánh (Multi-Site).");
        }

        [RelayCommand]
        public void ShowUsers()
        {
            HideAllViews();
            UsersVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            UsersNavBg = "#162034";
            UsersNavFg = "#00E5FF";
            SelectedNavMenu = "Users";
            AddLog("[ĐIỀU HƯỚNG] Mở màn hình Phân quyền & Quản lý Tài khoản người dùng.");
        }

        [RelayCommand]
        public void ShowOrders()
        {
            HideAllViews();
            OrdersVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            OrdersNavBg = "#162034";
            OrdersNavFg = "#00E5FF";
            SelectedNavMenu = "Orders";
            AddLog("[ĐIỀU HƯỚNG] Mở màn hình Lịch sử Đơn hàng Giao thức ăn.");
        }

        [RelayCommand]
        public void ShowAlerts()
        {
            HideAllViews();
            AlertsVisibility = Visibility.Visible;
            RightPanelVisibility = Visibility.Collapsed;
            AlertsNavBg = "#162034";
            AlertsNavFg = "#00E5FF";
            SelectedNavMenu = "Alerts";
            AddLog("[ĐIỀU HƯỚNG] Mở màn hình Nhật ký Cảnh báo hệ thống.");
        }

        // =========================================================
        // QUICK RELOCALIZE AT STATIONS (CHARGING DOCK / STAND-BY POINT)
        // =========================================================
        [RelayCommand]
        public async Task RelocalizeAtStationAsync(string stationName)
        {
            if (!IsConnected)
            {
                MessageBox.Show("Robot hiện CHƯA KẾT NỐI! Vui lòng kết nối robot trước khi định vị.", "Chưa kết nối", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            AddLog($"[ĐỊNH VỊ NHANH 1-CHẠM] Đang định vị robot tại trạm: '{stationName}'...");
            await _robotService.SendCommandAsync(new SetLocationCommand { PlaceName = stationName });
        }

        // =========================================================
        // USER AUTHENTICATION & PROFILE / PASSWORD COMMANDS
        // =========================================================
        private void UpdateUserAvatarInitials(string fullName)
        {
            if (string.IsNullOrWhiteSpace(fullName))
            {
                UserAvatarInitials = "LH";
                return;
            }
            var parts = fullName.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length >= 2)
                UserAvatarInitials = $"{parts[0][0]}{parts[^1][0]}".ToUpper();
            else if (parts.Length == 1 && parts[0].Length >= 2)
                UserAvatarInitials = parts[0][..2].ToUpper();
            else if (parts.Length == 1)
                UserAvatarInitials = parts[0][..1].ToUpper();
            else
                UserAvatarInitials = "LH";
        }

        [RelayCommand]
        public void ToggleProfileMenu()
        {
            IsProfileMenuOpen = !IsProfileMenuOpen;
        }

        [RelayCommand]
        public void CloseProfileMenu()
        {
            IsProfileMenuOpen = false;
        }

        [RelayCommand]
        public void Logout()
        {
            IsProfileMenuOpen = false;
            IsLoggedIn = false;
            IsLoginScreenVisible = true;
            LoginPassword = string.Empty;
            LoginErrorMessage = string.Empty;
            HasLoginError = false;
            AddLog($"[ĐĂNG XUẤT] Người dùng '{CurrentUser?.Username ?? "N/A"}' đã đăng xuất khỏi hệ thống.");
        }

        [RelayCommand]
        public void OpenChangePasswordDialog()
        {
            IsProfileMenuOpen = false;
            OldPassword = string.Empty;
            NewPassword = string.Empty;
            ConfirmNewPassword = string.Empty;
            ChangePasswordErrorMessage = string.Empty;
            HasChangePasswordError = false;
            ChangePasswordSuccessMessage = string.Empty;
            HasChangePasswordSuccess = false;
            IsChangePasswordDialogVisible = true;
        }

        [RelayCommand]
        public void CloseChangePasswordDialog()
        {
            IsChangePasswordDialogVisible = false;
            ChangePasswordErrorMessage = string.Empty;
            HasChangePasswordError = false;
            ChangePasswordSuccessMessage = string.Empty;
            HasChangePasswordSuccess = false;
        }

        [RelayCommand]
        public async Task SaveChangePasswordAsync()
        {
            if (CurrentUser == null)
            {
                ChangePasswordErrorMessage = "Không tìm thấy phiên đăng nhập!";
                HasChangePasswordError = true;
                return;
            }

            if (string.IsNullOrEmpty(OldPassword))
            {
                ChangePasswordErrorMessage = "Vui lòng nhập mật khẩu hiện tại!";
                HasChangePasswordError = true;
                return;
            }

            if (OldPassword != CurrentUser.Password)
            {
                ChangePasswordErrorMessage = "Mật khẩu hiện tại không chính xác!";
                HasChangePasswordError = true;
                return;
            }

            if (string.IsNullOrWhiteSpace(NewPassword) || NewPassword.Length < 4)
            {
                ChangePasswordErrorMessage = "Mật khẩu mới phải có ít nhất 4 ký tự!";
                HasChangePasswordError = true;
                return;
            }

            if (NewPassword != ConfirmNewPassword)
            {
                ChangePasswordErrorMessage = "Mật khẩu xác nhận không khớp!";
                HasChangePasswordError = true;
                return;
            }

            try
            {
                var db = DatabaseService.Instance;
                bool ok = await db.UpdateUserPasswordAsync(CurrentUser.Id, NewPassword);
                if (ok)
                {
                    CurrentUser.Password = NewPassword;
                    var localAcc = UserAccounts.FirstOrDefault(u => u.Id == CurrentUser.Id);
                    if (localAcc != null) localAcc.Password = NewPassword;

                    HasChangePasswordError = false;
                    ChangePasswordErrorMessage = string.Empty;
                    ChangePasswordSuccessMessage = "✅ Đổi mật khẩu thành công!";
                    HasChangePasswordSuccess = true;
                    AddLog($"[TÀI KHOẢN] Người dùng '{CurrentUser.Username}' đã cập nhật mật khẩu mới.");

                    await Task.Delay(1000);
                    IsChangePasswordDialogVisible = false;
                }
                else
                {
                    ChangePasswordErrorMessage = "Lỗi khi cập nhật cơ sở dữ liệu!";
                    HasChangePasswordError = true;
                }
            }
            catch (Exception ex)
            {
                ChangePasswordErrorMessage = $"Lỗi: {ex.Message}";
                HasChangePasswordError = true;
            }
        }

        [RelayCommand]
        public async Task LoginAsync()
        {
            if (string.IsNullOrWhiteSpace(LoginUsername))
            {
                LoginErrorMessage = "Vui lòng nhập tên đăng nhập!";
                HasLoginError = true;
                return;
            }

            if (string.IsNullOrEmpty(LoginPassword))
            {
                LoginErrorMessage = "Vui lòng nhập mật khẩu!";
                HasLoginError = true;
                return;
            }

            try
            {
                var db = DatabaseService.Instance;
                UserAccountItem? user = null;

                // 1. Thử xác thực trực tuyến qua Cloud Server trước (Online Authentication)
                if (_cloudBridge != null)
                {
                    user = await _cloudBridge.AuthenticateOnCloudAsync(LoginUsername, LoginPassword);
                    if (user != null)
                    {
                        // Lưu / cập nhật tài khoản vào SQLite cục bộ để hỗ trợ đăng nhập offline sau này
                        try
                        {
                            await db.SaveUserAccountAsync(user);
                            ApplyCloudUsers(new List<UserAccountItem> { user });
                        }
                        catch (Exception ex)
                        {
                            Debug.WriteLine($"[Login] Cache user to local DB error: {ex.Message}");
                        }
                    }
                }

                // 2. Nếu Cloud chưa phản hồi (offline) hoặc không tìm thấy, thử xác thực bằng Database cục bộ (Offline Mode)
                if (user == null)
                {
                    user = await db.AuthenticateUserAsync(LoginUsername, LoginPassword);
                }

                if (user != null)
                {
                    CurrentUser = user;
                    CurrentUserBadge = $"👑 {user.FullName} ({user.Role})";
                    UpdateUserAvatarInitials(user.FullName);

                    IsLoggedIn = true;
                    IsLoginScreenVisible = false;
                    LoginErrorMessage = string.Empty;
                    HasLoginError = false;
                    AddLog($"[ĐĂNG NHẬP] Chào mừng '{user.FullName}' ({user.Role}) đã đăng nhập vào hệ thống.");

                    _ = Task.Run(async () =>
                    {
                        if (_cloudBridge != null)
                        {
                            await _cloudBridge.SyncCurrentUserAsync();
                            await _cloudBridge.SyncCatalogFromCloudAsync();
                        }
                    });
                }
                else
                {
                    LoginErrorMessage = "Tên đăng nhập hoặc mật khẩu không chính xác!";
                    HasLoginError = true;
                }
            }
            catch (Exception ex)
            {
                LoginErrorMessage = $"Lỗi đăng nhập: {ex.Message}";
                HasLoginError = true;
            }
        }

        [RelayCommand]
        public void QuickFillAccount(string? roleType)
        {
            switch (roleType?.ToLower())
            {
                case "admin":
                    LoginUsername = "admin";
                    LoginPassword = "123456";
                    break;
                case "vinpearl":
                    LoginUsername = "vinpearl_admin";
                    LoginPassword = "123456";
                    break;
                case "landmark":
                    LoginUsername = "vlm81_manager";
                    LoginPassword = "123456";
                    break;
                case "operator":
                    LoginUsername = "operator01";
                    LoginPassword = "123456";
                    break;
                default:
                    LoginUsername = "admin";
                    LoginPassword = "123456";
                    break;
            }
            LoginErrorMessage = string.Empty;
            HasLoginError = false;
        }
    }
}
