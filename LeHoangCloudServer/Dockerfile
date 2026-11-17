# Stage 1: Build & Publish
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

COPY ["LeHoangCloudServer/LeHoangCloudServer.csproj", "LeHoangCloudServer/"]
RUN dotnet restore "LeHoangCloudServer/LeHoangCloudServer.csproj"

COPY . .
WORKDIR "/src/LeHoangCloudServer"
RUN dotnet publish "LeHoangCloudServer.csproj" -c Release -o /app/publish /p:UseAppHost=false

# Stage 2: Runtime Image
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app

# Install curl for container health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .

# Directory for persistent database
RUN mkdir -p /app/data
ENV DATABASE_PATH=/app/data/cloud_fleet.db
ENV PORT=5050
ENV ASPNETCORE_URLS=http://0.0.0.0:5050

EXPOSE 5050

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:5050/api/fleet/summary || exit 1

ENTRYPOINT ["dotnet", "LeHoangCloudServer.dll"]
