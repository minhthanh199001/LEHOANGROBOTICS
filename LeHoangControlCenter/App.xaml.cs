using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace LeHoangControlCenter;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            LogException("AppDomain.UnhandledException", args.ExceptionObject as Exception);
        };

        DispatcherUnhandledException += (s, args) =>
        {
            LogException("DispatcherUnhandledException", args.Exception);
            args.Handled = false;
        };

        base.OnStartup(e);
    }

    private static void LogException(string source, Exception? ex)
    {
        string logPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "crash_log.txt");
        string details = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] [{source}]\n";
        Exception? current = ex;
        while (current != null)
        {
            details += $"{current.GetType().FullName}: {current.Message}\n{current.StackTrace}\n\n";
            current = current.InnerException;
        }
        File.WriteAllText(logPath, details);
        MessageBox.Show(details, "Lỗi Khởi Động Ứng Dụng", MessageBoxButton.OK, MessageBoxImage.Error);
    }
}
