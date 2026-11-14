using System;
using System.Collections.Generic;
using CommunityToolkit.Mvvm.ComponentModel;

namespace LeHoangControlCenter.Models
{
    public partial class TrayDeliveryConfig : ObservableObject
    {
        [ObservableProperty]
        private int _trayNumber = 1;

        [ObservableProperty]
        private string _trayLabel = "Khay 1 (Trên)";

        [ObservableProperty]
        private string _destination = "";

        [ObservableProperty]
        private string _dishName = "";

        [ObservableProperty]
        private string _note = "";

        [ObservableProperty]
        private bool _hasDish = true;

        [ObservableProperty]
        private string _status = "Chờ giao";

        [ObservableProperty]
        private string _statusColor = "#F59E0B";

        public string DisplaySummary
        {
            get
            {
                if (!HasDish) return "Khay trống";
                if (string.IsNullOrWhiteSpace(Destination)) return "Chưa chọn bàn";
                return string.IsNullOrWhiteSpace(DishName) ? Destination : $"{Destination}: {DishName}";
            }
        }
    }

    public partial class FoodDeliveryTask : ObservableObject
    {
        [ObservableProperty]
        private string _id = "TASK-" + DateTime.Now.ToString("HHmmss");

        [ObservableProperty]
        private string _taskName = "Giao món ăn";

        [ObservableProperty]
        private string _assignedRobot = "Robot 01";

        [ObservableProperty]
        private string _returnStation = "Stand-by Point";

        [ObservableProperty]
        private int _waitTimeSeconds = 30;

        [ObservableProperty]
        private string _greetingSpeech = "Kính chào quý khách! Món ăn đã sẵn sàng trên khay của robot, chúc quý khách ngon miệng!";

        [ObservableProperty]
        private string _status = "Chờ thực hiện";

        [ObservableProperty]
        private string _statusColor = "#F59E0B";

        [ObservableProperty]
        private DateTime _createdAt = DateTime.Now;

        [ObservableProperty]
        private int _currentActiveTray = 0; // 0: chưa chạy, 1: khay 1, 2: khay 2, 3: khay 3, 4: về trạm

        [ObservableProperty]
        private TrayDeliveryConfig _tray1 = new() { TrayNumber = 1, TrayLabel = "Khay 1 (Trên)" };

        [ObservableProperty]
        private TrayDeliveryConfig _tray2 = new() { TrayNumber = 2, TrayLabel = "Khay 2 (Giữa)" };

        [ObservableProperty]
        private TrayDeliveryConfig _tray3 = new() { TrayNumber = 3, TrayLabel = "Khay 3 (Dưới)" };

        public int ActiveTrayCount => (Tray1.HasDish ? 1 : 0) + (Tray2.HasDish ? 1 : 0) + (Tray3.HasDish ? 1 : 0);

        public string TrayRouteOverview
        {
            get
            {
                var list = new List<string>();
                if (Tray1.HasDish && !string.IsNullOrWhiteSpace(Tray1.Destination)) list.Add($"K1: {Tray1.Destination}");
                if (Tray2.HasDish && !string.IsNullOrWhiteSpace(Tray2.Destination)) list.Add($"K2: {Tray2.Destination}");
                if (Tray3.HasDish && !string.IsNullOrWhiteSpace(Tray3.Destination)) list.Add($"K3: {Tray3.Destination}");
                return list.Count > 0 ? string.Join(" ➔ ", list) : "Chưa cấu hình bàn";
            }
        }
    }
}

