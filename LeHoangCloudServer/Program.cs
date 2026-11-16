using LeHoangCloudServer.Hubs;
using LeHoangCloudServer.Services;

var builder = WebApplication.CreateBuilder(args);

// Configure port 5050 for Cloud Server (distinct from robot ports 8899 and apk server 8000)
builder.WebHost.UseUrls("http://0.0.0.0:5050");

// 1. Core Services
builder.Services.AddSingleton<CloudDatabaseService>();
builder.Services.AddControllers();
builder.Services.AddSignalR();

// 2. Cross-Origin Resource Sharing (CORS) for Web & Mobile clients
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAll", policy =>
    {
        policy.SetIsOriginAllowed(_ => true)
              .AllowAnyHeader()
              .AllowAnyMethod()
              .AllowCredentials();
    });
});

var app = builder.Build();

app.UseCors("AllowAll");
app.UseDefaultFiles();
app.UseStaticFiles();

app.UseRouting();

app.MapControllers();
app.MapHub<FleetHub>("/fleetHub");

// Fallback route to serve index.html for SPA
app.MapFallbackToFile("index.html");

Console.WriteLine("==================================================");
Console.WriteLine("  LÊ HOÀNG ROBOTICS - CLOUD FLEET SERVER v1.0    ");
Console.WriteLine("  Web Cloud Dashboard: http://localhost:5050     ");
Console.WriteLine("  SignalR Realtime Hub: http://localhost:5050/fleetHub");
Console.WriteLine("  REST API Endpoint:   http://localhost:5050/api/fleet ");
Console.WriteLine("==================================================");

app.Run();
