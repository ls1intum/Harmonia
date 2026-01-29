# Harmonia Startup Script (Windows PowerShell)
# This script starts all services using Docker containers

$ErrorActionPreference = "Stop"

# Colors for output
function Write-Green { Write-Host $args -ForegroundColor Green }
function Write-Blue { Write-Host $args -ForegroundColor Blue }
function Write-Yellow { Write-Host $args -ForegroundColor Yellow }
function Write-Red { Write-Host $args -ForegroundColor Red }

Write-Blue "========================================"
Write-Blue "    Starting Harmonia Application"
Write-Blue "    (Containerized Version)"
Write-Blue "========================================"
Write-Host ""

# Step 1: Check Docker is running
Write-Green "[1/4] Checking Docker..."
try {
    docker info | Out-Null
    Write-Green "✓ Docker is running"
    Write-Host ""
} catch {
    Write-Red "✗ Docker is not running. Please start Docker Desktop."
    exit 1
}

# Step 2: Clean up any existing containers
Write-Green "[2/4] Cleaning up existing containers..."
docker compose -f docker/local-setup/docker-compose.yml down --remove-orphans 2>$null
Write-Green "✓ Cleanup completed"
Write-Host ""

# Step 3: Build and start all services
Write-Green "[3/4] Building and starting all services..."
Write-Yellow "This may take several minutes for the first run..."
Write-Yellow "Building Docker images and starting containers..."
Write-Host ""

docker compose -f docker/local-setup/docker-compose.yml up --build -d

if ($LASTEXITCODE -eq 0) {
    Write-Green "✓ All services started successfully"
    Write-Host ""
} else {
    Write-Red "✗ Failed to start services"
    Write-Yellow "Check logs with: docker compose -f docker/local-setup/docker-compose.yml logs"
    exit 1
}

# Step 4: Wait for services to be ready
Write-Green "[4/4] Waiting for services to be ready..."
Write-Yellow "Checking service health..."

# Wait for postgres to be healthy
Write-Yellow "• Waiting for PostgreSQL..."
$timeout = 60
$postgresReady = $false
while ($timeout -gt 0) {
    $status = docker compose -f docker/local-setup/docker-compose.yml ps postgres 2>$null | Select-String "healthy"
    if ($status) {
        Write-Green "  ✓ PostgreSQL is ready"
        $postgresReady = $true
        break
    }
    Start-Sleep -Seconds 2
    $timeout -= 2
}

if (-not $postgresReady) {
    Write-Red "  ✗ PostgreSQL failed to start within 60 seconds"
    exit 1
}

# Wait for server to be responding
Write-Yellow "• Waiting for Spring Boot server..."
$timeout = 120
$serverReady = $false
while ($timeout -gt 0) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 401) {
            Write-Green "  ✓ Server is ready"
            $serverReady = $true
            break
        }
    } catch {
        # Server not ready yet
    }
    Start-Sleep -Seconds 3
    $timeout -= 3
}

if (-not $serverReady) {
    Write-Yellow "  ⚠ Server may still be starting (timeout reached)"
}

# Check if client is responding
Write-Yellow "• Waiting for React client..."
$timeout = 30
$clientReady = $false
while ($timeout -gt 0) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            Write-Green "  ✓ Client is ready"
            $clientReady = $true
            break
        }
    } catch {
        # Client not ready yet
    }
    Start-Sleep -Seconds 2
    $timeout -= 2
}

if (-not $clientReady) {
    Write-Yellow "  ⚠ Client may still be starting (timeout reached)"
}

# Summary
Write-Host ""
Write-Blue "========================================"
Write-Green "✓ Harmonia is now running!"
Write-Blue "========================================"
Write-Yellow "Server:" -NoNewline
Write-Host " http://localhost:8080"
Write-Yellow "Client:" -NoNewline
Write-Host " http://localhost:5173"
Write-Yellow "Database:" -NoNewline
Write-Host " PostgreSQL on localhost:5432"
Write-Host ""
Write-Yellow "Useful commands:"
Write-Yellow "• View logs:" -NoNewline
Write-Host " docker compose -f docker/local-setup/docker-compose.yml logs -f"
Write-Yellow "• Stop services:" -NoNewline
Write-Host " docker compose -f docker/local-setup/docker-compose.yml down"
Write-Yellow "• Restart services:" -NoNewline
Write-Host " docker compose -f docker/local-setup/docker-compose.yml restart"
Write-Host ""
Write-Yellow "Press Ctrl+C to stop all services"
Write-Host ""

# Keep script running and handle Ctrl+C
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# Cleanup on exit
Write-Host ""
Write-Red "Shutting down all services..."
docker compose -f docker/local-setup/docker-compose.yml down
