using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace LeHoangControlCenter.Helpers
{
    public static class CyberInputDialog
    {
        public static string? Show(string title, string prompt, string defaultText = "")
        {
            if (Application.Current == null) return null;

            if (!Application.Current.Dispatcher.CheckAccess())
            {
                return Application.Current.Dispatcher.Invoke(() => Show(title, prompt, defaultText));
            }

            var win = new Window
            {
                Title = title,
                Width = 440,
                Height = 220,
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
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0F172A")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                BorderThickness = new Thickness(1.5),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(20)
            };

            var grid = new Grid();
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // 0: Title
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // 1: Prompt
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // 2: TextBox
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // 3: Buttons

            // Title
            var titleText = new TextBlock
            {
                Text = title,
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                Margin = new Thickness(0, 0, 0, 10)
            };
            Grid.SetRow(titleText, 0);
            grid.Children.Add(titleText);

            // Prompt
            var promptText = new TextBlock
            {
                Text = prompt,
                FontSize = 11.5,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#94A3B8")),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 12)
            };
            Grid.SetRow(promptText, 1);
            grid.Children.Add(promptText);

            // TextBox
            var textBox = new TextBox
            {
                Text = defaultText,
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#1E293B")),
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F8FAFC")),
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0284C7")),
                BorderThickness = new Thickness(1),
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 16)
            };
            Grid.SetRow(textBox, 2);
            grid.Children.Add(textBox);

            // Buttons container
            var buttonPanel = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right
            };
            Grid.SetRow(buttonPanel, 3);
            grid.Children.Add(buttonPanel);

            var btnCancel = new Button
            {
                Content = "✖ Hủy",
                Width = 85,
                Height = 32,
                Margin = new Thickness(0, 0, 10, 0),
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#334155")),
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#CBD5E1")),
                BorderThickness = new Thickness(0),
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Cursor = Cursors.Hand
            };

            var btnOk = new Button
            {
                Content = "✔ Xác nhận",
                Width = 105,
                Height = 32,
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#0284C7")),
                Foreground = Brushes.White,
                BorderThickness = new Thickness(0),
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Cursor = Cursors.Hand
            };

            string? confirmedValue = null;

            btnOk.Click += (s, e) =>
            {
                confirmedValue = textBox.Text?.Trim();
                win.Close();
            };

            btnCancel.Click += (s, e) =>
            {
                confirmedValue = null;
                win.Close();
            };

            textBox.KeyDown += (s, e) =>
            {
                if (e.Key == Key.Enter)
                {
                    confirmedValue = textBox.Text?.Trim();
                    win.Close();
                }
                else if (e.Key == Key.Escape)
                {
                    confirmedValue = null;
                    win.Close();
                }
            };

            buttonPanel.Children.Add(btnCancel);
            buttonPanel.Children.Add(btnOk);

            outerBorder.Child = grid;
            win.Content = outerBorder;

            win.Loaded += (s, e) =>
            {
                textBox.Focus();
                textBox.SelectAll();
            };

            win.ShowDialog();
            return confirmedValue;
        }
    }
}
