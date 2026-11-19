using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace LeHoangControlCenter.Helpers
{
    public enum ElevatorSourceChoice
    {
        ClickOnMap,
        RobotPose,
        ManualCoords,
        Cancel
    }

    public class ElevatorSourceResult
    {
        public ElevatorSourceChoice Choice { get; set; } = ElevatorSourceChoice.Cancel;
        public string PointName { get; set; } = "";
        public double X { get; set; }
        public double Y { get; set; }
    }

    public static class CyberElevatorSourceDialog
    {
        public static ElevatorSourceResult Show(
            string title, 
            string subtitle, 
            string defaultPointName, 
            double robotX, 
            double robotY,
            double defaultX = 0.0,
            double defaultY = 0.0)
        {
            if (Application.Current == null)
            {
                return new ElevatorSourceResult { Choice = ElevatorSourceChoice.Cancel };
            }

            if (!Application.Current.Dispatcher.CheckAccess())
            {
                return Application.Current.Dispatcher.Invoke(() => 
                    Show(title, subtitle, defaultPointName, robotX, robotY, defaultX, defaultY));
            }

            var result = new ElevatorSourceResult
            {
                PointName = defaultPointName,
                X = defaultX,
                Y = defaultY,
                Choice = ElevatorSourceChoice.Cancel
            };

            var win = new Window
            {
                Title = title,
                Width = 510,
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
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0284C7")),
                BorderThickness = new Thickness(1.5),
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(22)
            };

            var stack = new StackPanel();

            // Header Title
            var titleText = new TextBlock
            {
                Text = title,
                FontSize = 15,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                Margin = new Thickness(0, 0, 0, 4)
            };
            stack.Children.Add(titleText);

            // Subtitle
            var subText = new TextBlock
            {
                Text = subtitle,
                FontSize = 11,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")),
                Margin = new Thickness(0, 0, 0, 14)
            };
            stack.Children.Add(subText);

            // Name edit row
            var nameRow = new DockPanel { Margin = new Thickness(0, 0, 0, 14) };
            var nameLabel = new TextBlock
            {
                Text = "Tên điểm:",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#E2E8F0")),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 8, 0)
            };
            DockPanel.SetDock(nameLabel, Dock.Left);
            nameRow.Children.Add(nameLabel);

            var nameInput = new TextBox
            {
                Text = defaultPointName,
                FontSize = 12.5,
                FontWeight = FontWeights.Bold,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F8FAFC")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(8, 4, 8, 4)
            };
            nameRow.Children.Add(nameInput);
            stack.Children.Add(nameRow);

            // Choice prompt
            var prompt = new TextBlock
            {
                Text = "Chọn phương thức lấy tọa độ (không cần đẩy Robot):",
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#CBD5E1")),
                Margin = new Thickness(0, 0, 0, 10)
            };
            stack.Children.Add(prompt);

            // OPTION 1: Click on map (Recommended)
            var btnMap = new Button
            {
                HorizontalContentAlignment = HorizontalAlignment.Left,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0369A1")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 0, 0, 10),
                Cursor = Cursors.Hand
            };
            var mapContent = new StackPanel();
            var mapTitle = new TextBlock
            {
                Text = "🎯 1. Chấm trực tiếp trên bản đồ (Từ xa - Khuyên dùng)",
                FontWeight = FontWeights.Bold,
                FontSize = 12.5,
                Foreground = Brushes.White
            };
            var mapDesc = new TextBlock
            {
                Text = "Click chuột lên bản đồ để chọn ngay tọa độ mong muốn, hoàn toàn từ xa.",
                FontSize = 10.5,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#BAE6FD")),
                Margin = new Thickness(0, 2, 0, 0)
            };
            mapContent.Children.Add(mapTitle);
            mapContent.Children.Add(mapDesc);
            btnMap.Content = mapContent;

            btnMap.Click += (s, e) =>
            {
                result.Choice = ElevatorSourceChoice.ClickOnMap;
                result.PointName = !string.IsNullOrWhiteSpace(nameInput.Text) ? nameInput.Text.Trim() : defaultPointName;
                win.Close();
            };
            stack.Children.Add(btnMap);

            // OPTION 2: Use Robot current pose
            var btnRobot = new Button
            {
                HorizontalContentAlignment = HorizontalAlignment.Left,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E1B4B")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#6366F1")),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 0, 0, 10),
                Cursor = Cursors.Hand
            };
            var robotContent = new StackPanel();
            var robotTitle = new TextBlock
            {
                Text = "📍 2. Lấy vị trí Robot đang đứng",
                FontWeight = FontWeights.Bold,
                FontSize = 12.5,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#E0E7FF"))
            };
            var robotDesc = new TextBlock
            {
                Text = $"Tọa độ Robot hiện tại: X={robotX:F2}m, Y={robotY:F2}m (nếu Robot đã ở đúng chỗ)",
                FontSize = 10.5,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#A5B4FC")),
                Margin = new Thickness(0, 2, 0, 0)
            };
            robotContent.Children.Add(robotTitle);
            robotContent.Children.Add(robotDesc);
            btnRobot.Content = robotContent;

            btnRobot.Click += (s, e) =>
            {
                result.Choice = ElevatorSourceChoice.RobotPose;
                result.PointName = !string.IsNullOrWhiteSpace(nameInput.Text) ? nameInput.Text.Trim() : defaultPointName;
                result.X = Math.Round(robotX, 2);
                result.Y = Math.Round(robotY, 2);
                win.Close();
            };
            stack.Children.Add(btnRobot);

            // OPTION 3: Manual coordinates input
            var manualBorder = new Border
            {
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0F172A")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#334155")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(12, 8, 12, 8),
                Margin = new Thickness(0, 0, 0, 12)
            };
            var manualGrid = new Grid();
            manualGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            manualGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            manualGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var xStack = new StackPanel { Margin = new Thickness(0, 0, 6, 0) };
            xStack.Children.Add(new TextBlock { Text = "Tọa độ X (m):", FontSize = 10.5, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")) });
            var txtX = new TextBox
            {
                Text = (defaultX != 0 ? defaultX : robotX).ToString("F2", System.Globalization.CultureInfo.InvariantCulture),
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = Brushes.White,
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#475569")),
                Padding = new Thickness(4, 3, 4, 3),
                FontSize = 11.5
            };
            xStack.Children.Add(txtX);
            Grid.SetColumn(xStack, 0);
            manualGrid.Children.Add(xStack);

            var yStack = new StackPanel { Margin = new Thickness(6, 0, 6, 0) };
            yStack.Children.Add(new TextBlock { Text = "Tọa độ Y (m):", FontSize = 10.5, Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")) });
            var txtY = new TextBox
            {
                Text = (defaultY != 0 ? defaultY : robotY).ToString("F2", System.Globalization.CultureInfo.InvariantCulture),
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = Brushes.White,
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#475569")),
                Padding = new Thickness(4, 3, 4, 3),
                FontSize = 11.5
            };
            yStack.Children.Add(txtY);
            Grid.SetColumn(yStack, 1);
            manualGrid.Children.Add(yStack);

            var btnManualSave = new Button
            {
                Content = "✏️ Lưu tọa độ tay",
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#334155")),
                Foreground = Brushes.White,
                BorderThickness = new Thickness(0),
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Padding = new Thickness(10, 6, 10, 6),
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(6, 0, 0, 0),
                Cursor = Cursors.Hand
            };
            btnManualSave.Click += (s, e) =>
            {
                double.TryParse(txtX.Text, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double parsedX);
                double.TryParse(txtY.Text, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double parsedY);
                result.Choice = ElevatorSourceChoice.ManualCoords;
                result.PointName = !string.IsNullOrWhiteSpace(nameInput.Text) ? nameInput.Text.Trim() : defaultPointName;
                result.X = Math.Round(parsedX, 2);
                result.Y = Math.Round(parsedY, 2);
                win.Close();
            };
            Grid.SetColumn(btnManualSave, 2);
            manualGrid.Children.Add(btnManualSave);

            manualBorder.Child = manualGrid;
            stack.Children.Add(manualBorder);

            // Cancel button
            var btnCancel = new Button
            {
                Content = "✖ Đóng / Hủy",
                HorizontalAlignment = HorizontalAlignment.Right,
                Width = 100,
                Height = 30,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")),
                BorderThickness = new Thickness(0),
                FontSize = 11,
                Cursor = Cursors.Hand
            };
            btnCancel.Click += (s, e) =>
            {
                result.Choice = ElevatorSourceChoice.Cancel;
                win.Close();
            };
            stack.Children.Add(btnCancel);

            outerBorder.Child = stack;
            win.Content = outerBorder;

            win.KeyDown += (s, e) =>
            {
                if (e.Key == Key.Escape)
                {
                    result.Choice = ElevatorSourceChoice.Cancel;
                    win.Close();
                }
            };

            win.ShowDialog();
            return result;
        }
    }
}
