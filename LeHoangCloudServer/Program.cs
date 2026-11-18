using LeHoangCloudServer.Hubs;
using LeHoangCloudServer.Services;

var builder = WebApplication.CreateBuilder(args);

// Configure listening URLs (supports dynamic Cloud environment variables like PORT or ASPNETCORE_URLS)
var port = Environment.GetEnvironmentVariable("PORT") ?? "5050";
var defaultUrl = Environment.GetEnvironmentVariable("ASPNETCORE_URLS") ?? $"http://0.0.0.0:{port}";
builder.WebHost.UseUrls(defaultUrl);

// 1. Core Services
builder.Services.AddSingleton<CloudDatabaseService>();
builder.Services.AddSingleton<GreetingService>();
// Reuse one transcription worker per server to bound uploads and API usage.
builder.Services.AddSingleton<GreetingSpeechService>();
builder.Services.AddSingleton(new HttpClient { Timeout = TimeSpan.FromSeconds(15) });
builder.Services.AddSingleton<MicroArrayTelemetry>();
builder.Services.AddHostedService(services => services.GetRequiredService<MicroArrayTelemetry>());
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
