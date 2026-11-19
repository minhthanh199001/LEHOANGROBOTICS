using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using LeHoangControlCenter.Models;
using LeHoangControlCenter.ViewModels;

namespace LeHoangControlCenter.Helpers
{
    public class MultiFloorMissionSetupResult
    {
        public bool IsConfirmed { get; set; } = false;
        public FloorMapConfig? TargetFloor { get; set; }
        public string TargetStationName { get; set; } = "";
    }

    public static class CyberMultiFloorMissionSetupDialog
    {
        public static MultiFloorMissionSetupResult Show(
            FloorMapConfig? currentFloor,
            IList<FloorMapConfig> allFloorConfigs,
            Dictionary<string, List<SavedStationRecord>> allSavedStations)
        {
            var result = new MultiFloorMissionSetupResult();

            if (Application.Current == null) return result;

            if (!Application.Current.Dispatcher.CheckAccess())
            {
                return Application.Current.Dispatcher.Invoke(() =>
                    Show(currentFloor, allFloorConfigs, allSavedStations));
            }

            var win = new Window
            {
                Title = "Kịch bản giao hàng đa tầng",
                Width = 530,
                SizeToContent = SizeToContent.Height,
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                WindowStyle = WindowStyle.None,
                AllowsTransparency = true,
                Background = Brushes.Transparent,
                ResizeMode = ResizeMode.NoResize,
                Topmost = true
            };

            if (Application.Current.MainWindow != null && Application.Current.MainWindow.IsVisible)
            {
                win.Owner = Application.Current.MainWindow;
                win.WindowStartupLocation = WindowStartupLocation.CenterOwner;
            }

            var outerBorder = new Border
            {
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0B1120")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#10B981")),
                BorderThickness = new Thickness(1.8),
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(24)
            };

            var stack = new StackPanel();

            // 1. Header Title
            var title = new TextBlock
            {
                Text = "🚀 KỊCH BẢN GIAO HÀNG ĐA TẦNG (MULTI-FLOOR MISSION)",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#34D399")),
                Margin = new Thickness(0, 0, 0, 4)
            };
            stack.Children.Add(title);

            var subtitle = new TextBlock
            {
                Text = "Quy trình tự động di chuyển robot xuyên tầng qua thang máy đến bàn phục vụ",
                FontSize = 11,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")),
                Margin = new Thickness(0, 0, 0, 16)
            };
            stack.Children.Add(subtitle);

            // 2. Current Floor Info Box
            var currentBorder = new Border
            {
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E1B4B")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#6366F1")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(12, 8, 12, 8),
                Margin = new Thickness(0, 0, 0, 14)
            };
            var curStack = new StackPanel();
            var curTitle = new TextBlock
            {
                Text = $"📍 TẦNG XUẤT PHÁT HIỆN TẠI: {(currentFloor != null ? currentFloor.FloorLabel : "Tầng hiện tại")}",
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#E0E7FF"))
            };
            var curMap = new TextBlock
            {
                Text = $"Bản đồ: '{(currentFloor != null ? currentFloor.MapName : "Chưa xác định")}' | Sảnh chờ: '{(currentFloor?.OutsideWaitingPoint ?? "ChoThangMay")}' | Buồng thang: '{(currentFloor?.InsideCabinPoint ?? "TrongThangMay")}'",
                FontSize = 10,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#A5B4FC")),
                Margin = new Thickness(0, 2, 0, 0)
            };
            curStack.Children.Add(curTitle);
            curStack.Children.Add(curMap);
            currentBorder.Child = curStack;
            stack.Children.Add(currentBorder);

            // 3. Selection: Target Floor
            var lblTargetFloor = new TextBlock
            {
                Text = "1. Chọn Tầng đích cần giao món đến:",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F8FAFC")),
                Margin = new Thickness(0, 0, 0, 6)
            };
            stack.Children.Add(lblTargetFloor);

            var cboTargetFloor = new ComboBox
            {
                Height = 32,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = Brushes.White,
                FontSize = 12,
                FontWeight = FontWeights.Bold,
                Margin = new Thickness(0, 0, 0, 14)
            };

            // Available floors (preferably other floors than current)
            var availableFloors = allFloorConfigs.Where(f => !string.IsNullOrEmpty(f.MapName)).ToList();
            if (availableFloors.Count == 0) availableFloors = allFloorConfigs.ToList();

            foreach (var f in availableFloors)
            {
                cboTargetFloor.Items.Add(f);
            }
            cboTargetFloor.ItemTemplate = CreateFloorDataTemplate();

            // Select default: first floor different from current, or first floor
            var defaultTarget = availableFloors.FirstOrDefault(f => currentFloor == null || f.FloorNumber != currentFloor.FloorNumber)
                             ?? availableFloors.FirstOrDefault();
            if (defaultTarget != null) cboTargetFloor.SelectedItem = defaultTarget;
            stack.Children.Add(cboTargetFloor);

            // 4. Selection: Target Station (Table)
            var lblTargetStation = new TextBlock
            {
                Text = "2. Chọn Bàn / Điểm đích cần phục vụ tại tầng đích:",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F8FAFC")),
                Margin = new Thickness(0, 0, 0, 6)
            };
            stack.Children.Add(lblTargetStation);

            var cboTargetStation = new ComboBox
            {
                Height = 32,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                FontSize = 12,
                FontWeight = FontWeights.Bold,
                Margin = new Thickness(0, 0, 0, 16)
            };

            void RefreshStationsForSelectedFloor()
            {
                cboTargetStation.Items.Clear();
                if (cboTargetFloor.SelectedItem is FloorMapConfig selFloor && !string.IsNullOrEmpty(selFloor.MapName))
                {
                    List<SavedStationRecord>? stList = null;
                    if (allSavedStations.TryGetValue(selFloor.MapName, out var directList))
                    {
                        stList = directList;
                    }
                    else
                    {
                        string norm = selFloor.MapName.Trim().ToLowerInvariant();
                        var match = allSavedStations.FirstOrDefault(kvp => kvp.Key.Trim().ToLowerInvariant() == norm);
                        stList = match.Value;
                    }

                    if (stList != null)
                    {
                        foreach (var st in stList)
                        {
                            // Filter out technical elevator points, keep tables and delivery spots
                            string lower = st.Name.ToLowerInvariant();
                            if (lower.Contains("cho") && lower.Contains("thang")) continue;
                            if (lower.Contains("trong") && lower.Contains("thang")) continue;
                            cboTargetStation.Items.Add(st.Name);
                        }
                    }

                    if (cboTargetStation.Items.Count == 0 && stList != null)
                    {
                        foreach (var st in stList) cboTargetStation.Items.Add(st.Name);
                    }
                }

                if (cboTargetStation.Items.Count > 0)
                {
                    cboTargetStation.SelectedIndex = 0;
                }
                else
                {
                    cboTargetStation.Items.Add("Bàn 1");
                    cboTargetStation.SelectedIndex = 0;
                }
            }

            cboTargetFloor.SelectionChanged += (s, e) => RefreshStationsForSelectedFloor();
            RefreshStationsForSelectedFloor();
            stack.Children.Add(cboTargetStation);

            // 5. 5-Step Roadmap Preview Box
            var roadmapBorder = new Border
            {
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#090E1A")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 0, 0, 18)
            };
            var roadStack = new StackPanel();
            roadStack.Children.Add(new TextBlock
            {
                Text = "🗺️ LỘ TRÌNH 5 GIAI ĐOẠN TỰ ĐỘNG:",
                FontSize = 10.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                Margin = new Thickness(0, 0, 0, 4)
            });
            roadStack.Children.Add(new TextBlock { Text = "① Chạy tới điểm chờ sảnh tầng hiện tại (ChoThangMay)", FontSize = 10, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")) });
            roadStack.Children.Add(new TextBlock { Text = "② Thang mở cửa ➔ Đi vào buồng cabin (TrongThangMay) & hướng mặt ra cửa", FontSize = 10, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")) });
            roadStack.Children.Add(new TextBlock { Text = "③ Thang chạy đến tầng đích ➔ Chuyển map & Định vị lại buồng cabin", FontSize = 10, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")) });
            roadStack.Children.Add(new TextBlock { Text = "④ Thang mở cửa tầng đích ➔ Lăn bánh ra ngoài sảnh an toàn", FontSize = 10, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")) });
            roadStack.Children.Add(new TextBlock { Text = "⑤ Di chuyển từ sảnh tới bàn ăn phục vụ ➔ Báo khách nhận món!", FontSize = 10, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#34D399")) });
            roadmapBorder.Child = roadStack;
            stack.Children.Add(roadmapBorder);

            // 6. Action Buttons (Start / Cancel)
            var buttonPanel = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right
            };

            var btnCancel = new Button
            {
                Content = "✖ Đóng",
                Width = 90,
                Height = 34,
                Margin = new Thickness(0, 0, 10, 0),
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")),
                BorderThickness = new Thickness(0),
                FontSize = 11,
                Cursor = Cursors.Hand
            };
            btnCancel.Click += (s, e) => win.Close();

            var btnStart = new Button
            {
                Content = "🚀 BẮT ĐẦU CHẠY KỊCH BẢN",
                Width = 200,
                Height = 34,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#059669")),
                Foreground = Brushes.White,
                BorderThickness = new Thickness(0),
                FontSize = 11.5,
                FontWeight = FontWeights.Bold,
                Cursor = Cursors.Hand
            };
            btnStart.Click += (s, e) =>
            {
                result.IsConfirmed = true;
                result.TargetFloor = cboTargetFloor.SelectedItem as FloorMapConfig;
                result.TargetStationName = cboTargetStation.SelectedItem?.ToString() ?? "Bàn 1";
                win.Close();
            };

            buttonPanel.Children.Add(btnCancel);
            buttonPanel.Children.Add(btnStart);
            stack.Children.Add(buttonPanel);

            outerBorder.Child = stack;
            win.Content = outerBorder;

            win.KeyDown += (s, e) =>
            {
                if (e.Key == Key.Escape) win.Close();
            };

            win.ShowDialog();
            return result;
        }

        private static DataTemplate CreateFloorDataTemplate()
        {
            var dt = new DataTemplate();
            var fef = new FrameworkElementFactory(typeof(StackPanel));
            fef.SetValue(StackPanel.OrientationProperty, Orientation.Horizontal);

            var iconFef = new FrameworkElementFactory(typeof(TextBlock));
            iconFef.SetValue(TextBlock.TextProperty, "🏢 ");
            iconFef.SetValue(TextBlock.FontSizeProperty, 11.0);
            fef.AppendChild(iconFef);

            var labelFef = new FrameworkElementFactory(typeof(TextBlock));
            labelFef.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding("FloorLabel"));
            labelFef.SetValue(TextBlock.FontWeightProperty, FontWeights.Bold);
            labelFef.SetValue(TextBlock.ForegroundProperty, new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F8FAFC")));
            fef.AppendChild(labelFef);

            var mapFef = new FrameworkElementFactory(typeof(TextBlock));
            mapFef.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding("MapName") { StringFormat = " (Map: {0})" });
            mapFef.SetValue(TextBlock.FontSizeProperty, 10.0);
            mapFef.SetValue(TextBlock.ForegroundProperty, new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")));
            mapFef.SetValue(TextBlock.MarginProperty, new Thickness(6, 0, 0, 0));
            fef.AppendChild(mapFef);

            dt.VisualTree = fef;
            return dt;
        }
    }
}
