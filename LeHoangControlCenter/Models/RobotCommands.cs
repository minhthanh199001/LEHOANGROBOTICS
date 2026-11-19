using System;
using System.Text.Json.Serialization;

namespace LeHoangControlCenter.Models
{
    public abstract class BaseRobotCommand
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = string.Empty;

        [JsonPropertyName("msgId")]
        public string MsgId { get; set; } = Guid.NewGuid().ToString("N");
    }

    public class MoveCommand : BaseRobotCommand
    {
        public MoveCommand()
        {
            Type = "CMD_MOVE";
        }

        [JsonPropertyName("action")]
        public string Action { get; set; } = "STOP"; // FORWARD, BACKWARD, TURN_LEFT, TURN_RIGHT, STOP

        [JsonPropertyName("linearSpeed")]
        public double LinearSpeed { get; set; } = 0.4;

        [JsonPropertyName("angularSpeed")]
        public double AngularSpeed { get; set; } = 25.0;
    }

    public class HeadCommand : BaseRobotCommand
    {
        public HeadCommand()
        {
            Type = "CMD_HEAD";
        }

        [JsonPropertyName("horizontalAngle")]
        public int HorizontalAngle { get; set; }

        [JsonPropertyName("verticalAngle")]
        public int VerticalAngle { get; set; }
    }

    public class EmergencyStopCommand : BaseRobotCommand
    {
        public EmergencyStopCommand()
        {
            Type = "CMD_EMERGENCY_STOP";
        }
    }

    public class NavigateCommand : BaseRobotCommand
    {
        public NavigateCommand()
        {
            Type = "CMD_NAVIGATE";
        }

        [JsonPropertyName("destination")]
        public string Destination { get; set; } = string.Empty;

        [JsonPropertyName("mapName")]
        public string? MapName { get; set; }

        [JsonPropertyName("x")]
        public double? X { get; set; }

        [JsonPropertyName("y")]
        public double? Y { get; set; }

        [JsonPropertyName("theta")]
        public double? Theta { get; set; }

        [JsonPropertyName("maxDistance")]
        public double MaxDistance { get; set; } = 1.0;

        [JsonPropertyName("timeoutSeconds")]
        public int TimeoutSeconds { get; set; } = 60;
    }

    public class StopNavigateCommand : BaseRobotCommand
    {
        public StopNavigateCommand()
        {
            Type = "CMD_STOP_NAVIGATE";
        }
    }

    public class SpeakCommand : BaseRobotCommand
    {
        public SpeakCommand()
        {
            Type = "CMD_SPEAK";
        }

        [JsonPropertyName("text")]
        public string Text { get; set; } = string.Empty;
    }

    public class ChargeCommand : BaseRobotCommand
    {
        public ChargeCommand()
        {
            Type = "CMD_CHARGE";
        }

        [JsonPropertyName("action")]
        public string Action { get; set; } = "START_CHARGE"; // START_CHARGE, STOP_CHARGE, LEAVE_PILE
    }

    public class DoorCommand : BaseRobotCommand
    {
        public DoorCommand()
        {
            Type = "CMD_DOOR";
        }

        [JsonPropertyName("doorId")]
        public int DoorId { get; set; } = 1;

        [JsonPropertyName("action")]
        public string Action { get; set; } = "OPEN"; // OPEN, CLOSE
    }

    public class SwitchMapCommand : BaseRobotCommand
    {
        public SwitchMapCommand()
        {
            Type = "CMD_SWITCH_MAP";
        }

        [JsonPropertyName("mapName")]
        public string MapName { get; set; } = string.Empty;
    }

    public class GetMapDataCommand : BaseRobotCommand
    {
        public GetMapDataCommand()
        {
            Type = "CMD_GET_MAP_DATA";
        }

        [JsonPropertyName("mapName")]
        public string MapName { get; set; } = string.Empty;
    }

    public class SetPoseEstimateCommand : BaseRobotCommand
    {
        public SetPoseEstimateCommand()
        {
            Type = "CMD_SET_POSE_ESTIMATE";
        }

        [JsonPropertyName("x")]
        public double X { get; set; }

        [JsonPropertyName("y")]
        public double Y { get; set; }

        [JsonPropertyName("theta")]
        public double Theta { get; set; }
    }

    public class SetLocationCommand : BaseRobotCommand
    {
        public SetLocationCommand()
        {
            Type = "CMD_SET_LOCATION";
        }

        [JsonPropertyName("placeName")]
        public string PlaceName { get; set; } = string.Empty;

        [JsonPropertyName("x")]
        public double? X { get; set; }

        [JsonPropertyName("y")]
        public double? Y { get; set; }

        [JsonPropertyName("theta")]
        public double? Theta { get; set; }
    }

    public class ResetEstimateCommand : BaseRobotCommand
    {
        public ResetEstimateCommand()
        {
            Type = "CMD_RESET_ESTIMATE";
        }
    }

    public class SwitchMapAndRelocalizeCommand : BaseRobotCommand
    {
        public SwitchMapAndRelocalizeCommand()
        {
            Type = "CMD_SWITCH_MAP_AND_RELOCALIZE";
        }

        [JsonPropertyName("mapName")]
        public string MapName { get; set; } = string.Empty;

        [JsonPropertyName("placeName")]
        public string? PlaceName { get; set; }

        [JsonPropertyName("x")]
        public double? X { get; set; }

        [JsonPropertyName("y")]
        public double? Y { get; set; }

        [JsonPropertyName("theta")]
        public double? Theta { get; set; }
    }

    public class AddPlaceCommand : BaseRobotCommand
    {
        public AddPlaceCommand()
        {
            Type = "CMD_ADD_PLACE";
        }

        [JsonPropertyName("mapName")]
        public string MapName { get; set; } = string.Empty;

        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;

        [JsonPropertyName("x")]
        public double X { get; set; }

        [JsonPropertyName("y")]
        public double Y { get; set; }

        [JsonPropertyName("theta")]
        public double Theta { get; set; }
    }

    public class DeletePlaceCommand : BaseRobotCommand
    {
        public DeletePlaceCommand()
        {
            Type = "CMD_DELETE_PLACE";
        }

        [JsonPropertyName("mapName")]
        public string MapName { get; set; } = string.Empty;

        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;
    }
}

