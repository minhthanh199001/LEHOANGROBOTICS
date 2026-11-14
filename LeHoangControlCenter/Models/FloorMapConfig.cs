using CommunityToolkit.Mvvm.ComponentModel;
using System;
using System.Linq;
using System.Text.Json.Serialization;

namespace LeHoangControlCenter.Models
{
    public partial class FloorMapConfig : ObservableObject
    {
        [ObservableProperty]
        [property: JsonPropertyName("floorNumber")]
        private string _floorNumber = "1";

        [ObservableProperty]
        [property: JsonPropertyName("floorLabel")]
        private string _floorLabel = "Tầng 1";

        [ObservableProperty]
        [property: JsonPropertyName("mapName")]
        private string _mapName = "";

        // 1. Điểm trong buồng thang máy (Dùng để định vị lại khi đến tầng)
        [ObservableProperty]
        [property: JsonPropertyName("insideCabinPoint")]
        private string _insideCabinPoint = "TrongThangMay_T1";

        // 2. Điểm chờ ngoài sảnh hành lang (Dùng để đứng chờ gọi thang & lăn bánh ra khỏi thang)
        [ObservableProperty]
        [property: JsonPropertyName("outsideWaitingPoint")]
        private string _outsideWaitingPoint = "ChoThangMay_T1";

        // Backward compatibility for older config files
        [JsonPropertyName("elevatorPoint")]
        public string? ElevatorPoint
        {
            get => InsideCabinPoint;
            set
            {
                if (!string.IsNullOrEmpty(value) && (string.IsNullOrEmpty(InsideCabinPoint) || InsideCabinPoint == "TrongThangMay_T1"))
                {
                    InsideCabinPoint = value;
                }
            }
        }

        [ObservableProperty]
        [property: JsonPropertyName("elevatorX")]
        private double? _elevatorX;

        [ObservableProperty]
        [property: JsonPropertyName("elevatorY")]
        private double? _elevatorY;

        [ObservableProperty]
        [property: JsonPropertyName("elevatorTheta")]
        private double? _elevatorTheta;

        partial void OnFloorLabelChanged(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return;
            // Extract digits if present, e.g. "Tầng 2" -> "2"
            var digits = new string(value.Where(char.IsDigit).ToArray());
            if (!string.IsNullOrEmpty(digits))
            {
                FloorNumber = digits;
            }
            else
            {
                FloorNumber = value.Trim();
            }
        }
    }
}
