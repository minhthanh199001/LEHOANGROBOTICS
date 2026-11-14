using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using LeHoangControlCenter.ViewModels;

namespace LeHoangControlCenter;

/// <summary>
/// Interaction logic for MainWindow.xaml
/// </summary>
public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        this.KeyDown += MainWindow_KeyDown;
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
