using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using LeHoangControlCenter.ViewModels;

namespace LeHoangControlCenter;

/// <summary>
/// Interaction logic for MainWindow.xaml
/// </summary>
public partial class MainWindow : Window
{
    private const int WM_GETMINMAXINFO = 0x0024;
    private const uint MONITOR_DEFAULTTONEAREST = 0x00000002;

    [DllImport("user32.dll")]
    private static extern IntPtr MonitorFromWindow(IntPtr handle, uint flags);

    [DllImport("user32.dll")]
    private static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO lpmi);

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT
    {
        public int x;
        public int y;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MINMAXINFO
    {
        public POINT ptReserved;
        public POINT ptMaxSize;
        public POINT ptMaxPosition;
        public POINT ptMinTrackSize;
        public POINT ptMaxTrackSize;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct MONITORINFO
    {
        public int cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public int dwFlags;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT
    {
        public int left, top, right, bottom;
    }

    public MainWindow()
    {
        InitializeComponent();
        this.KeyDown += MainWindow_KeyDown;
        this.StateChanged += MainWindow_StateChanged;
        this.Loaded += (s, e) => UpdateLayoutForState();
    }

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        IntPtr handle = new WindowInteropHelper(this).Handle;
        HwndSource.FromHwnd(handle)?.AddHook(WindowProc);
    }

    private IntPtr WindowProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_GETMINMAXINFO)
        {
            WmGetMinMaxInfo(hwnd, lParam);
            handled = true;
        }
        return IntPtr.Zero;
    }

    private static void WmGetMinMaxInfo(IntPtr hwnd, IntPtr lParam)
    {
        var mmi = (MINMAXINFO)Marshal.PtrToStructure(lParam, typeof(MINMAXINFO))!;
        IntPtr monitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
        if (monitor != IntPtr.Zero)
        {
            var monitorInfo = new MONITORINFO();
            monitorInfo.cbSize = Marshal.SizeOf(typeof(MONITORINFO));
            if (GetMonitorInfo(monitor, ref monitorInfo))
            {
                RECT rcWorkArea = monitorInfo.rcWork;
                RECT rcMonitorArea = monitorInfo.rcMonitor;
                mmi.ptMaxPosition.x = Math.Abs(rcWorkArea.left - rcMonitorArea.left);
                mmi.ptMaxPosition.y = Math.Abs(rcWorkArea.top - rcMonitorArea.top);
                mmi.ptMaxSize.x = Math.Abs(rcWorkArea.right - rcWorkArea.left);
                mmi.ptMaxSize.y = Math.Abs(rcWorkArea.bottom - rcWorkArea.top);
            }
        }
        Marshal.StructureToPtr(mmi, lParam, true);
    }

    private void MainWindow_StateChanged(object? sender, EventArgs e)
    {
        UpdateLayoutForState();
    }

    private void UpdateLayoutForState()
    {
        if (WindowState == WindowState.Maximized)
        {
            // Compensate for WindowChrome overflow when maximized to avoid clipping at taskbar across all DPI scales
            MainRootGrid.Margin = new Thickness(8, 8, 8, 16);
            if (MaximizeBtnIcon != null) MaximizeBtnIcon.Text = "❐";
            if (MaximizeBtn != null) MaximizeBtn.ToolTip = "Khôi phục kích thước cửa sổ";
        }
        else
        {
            MainRootGrid.Margin = new Thickness(0);
            if (MaximizeBtnIcon != null) MaximizeBtnIcon.Text = "🗖";
            if (MaximizeBtn != null) MaximizeBtn.ToolTip = "Phóng to toàn màn hình";
        }
    }

    private void MinimizeBtn_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void MaximizeBtn_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void CloseBtn_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void MainWindow_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Space)
        {
            if (DataContext is MainViewModel vm)
            {
                _ = vm.EmergencyStopAsync();
            }
            e.Handled = true;
        }
    }

    private void OnDirectionButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is Button btn && btn.CommandParameter is string dir && DataContext is MainViewModel vm)
        {
            vm.StartHoldMove(dir);
        }
    }

    private void OnDirectionButtonUp(object sender, MouseEventArgs e)
    {
        if (DataContext is MainViewModel vm)
        {
            vm.StopHoldMove();
        }
    }

    // --- Interactive Map Zoom, Pan & Click Navigation Handlers ---
    private Point _lastPanPoint;
    private Point _mouseDownPos;
    private bool _isPanning = false;
    private bool _hasDragged = false;

    private void MapViewport_PreviewMouseWheel(object sender, MouseWheelEventArgs e)
    {
        if (DataContext is MainViewModel vm)
        {
            double delta = e.Delta > 0 ? 0.2 : -0.2;
            vm.ApplyZoom(delta);
            e.Handled = true;
        }
    }

    private void MapViewport_MouseDown(object sender, MouseButtonEventArgs e)
    {
        // 1. If clicked on a Button (e.g. Station Pin Badge), let the Button handle its Click event!
        if (e.OriginalSource is DependencyObject dep && FindVisualParent<Button>(dep) != null)
        {
            return;
        }

        if ((e.LeftButton == MouseButtonState.Pressed || e.RightButton == MouseButtonState.Pressed) && sender is IInputElement element)
        {
            _isPanning = true;
            _hasDragged = false;
            _mouseDownPos = e.GetPosition(element);
            _lastPanPoint = _mouseDownPos;
            element.CaptureMouse();
        }
    }

    private void MapViewport_MouseMove(object sender, MouseEventArgs e)
    {
        if (_isPanning && DataContext is MainViewModel vm && sender is IInputElement element)
        {
            var current = e.GetPosition(element);
            var deltaX = current.X - _lastPanPoint.X;
            var deltaY = current.Y - _lastPanPoint.Y;

            if (Math.Abs(current.X - _mouseDownPos.X) > 4 || Math.Abs(current.Y - _mouseDownPos.Y) > 4)
            {
                _hasDragged = true;
            }

            _lastPanPoint = current;
            vm.MapPanX += deltaX;
            vm.MapPanY += deltaY;
        }
    }

    private void MapViewport_MouseUp(object sender, MouseButtonEventArgs e)
    {
        if (_isPanning && sender is IInputElement element)
        {
            _isPanning = false;
            element.ReleaseMouseCapture();

            // If user clicked without dragging, treat this as a Click-to-Navigate goal point!
            if (!_hasDragged && e.ChangedButton == MouseButton.Left && DataContext is MainViewModel vm)
            {
                Canvas? canvas = null;
                if (sender is FrameworkElement fe)
                {
                    canvas = fe.FindName("MapTransformCanvas") as Canvas 
                          ?? fe.FindName("MapStudioTransformCanvas") as Canvas;
                }
                canvas ??= FindName("MapTransformCanvas") as Canvas 
                       ?? FindName("MapStudioTransformCanvas") as Canvas;

                if (canvas != null)
                {
                    Point clickOnCanvas = e.GetPosition(canvas);
                    if (vm.IsAddPointMode)
                    {
                        vm.CreatePointAtCanvas(clickOnCanvas.X, clickOnCanvas.Y);
                    }
                    else if (vm.IsRelocalizeMode)
                    {
                        vm.RelocalizeToCanvasPoint(clickOnCanvas.X, clickOnCanvas.Y);
                    }
                    else
                    {
                        vm.NavigateToCanvasPoint(clickOnCanvas.X, clickOnCanvas.Y);
                    }
                }
            }
        }
    }

    private static T? FindVisualParent<T>(DependencyObject child) where T : DependencyObject
    {
        DependencyObject? parentObject = VisualTreeHelper.GetParent(child);
        if (parentObject == null) return null;
        if (parentObject is T parent) return parent;
        return FindVisualParent<T>(parentObject);
    }
}
