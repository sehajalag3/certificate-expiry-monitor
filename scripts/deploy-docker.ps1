<#
.SYNOPSIS
  Builds the image and starts the app with Docker Compose (Windows PowerShell).
.EXAMPLE
  .\scripts\deploy-docker.ps1
  .\scripts\deploy-docker.ps1 -Port 8081
#>
param([int]$Port = 8080)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose config -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$healthUrl = "http://localhost:$Port/actuator/health"
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            Write-Host "Application is up: $healthUrl" -ForegroundColor Green
            exit 0
        }
    } catch { }
}
Write-Error 'Application did not become healthy in time. Check: docker compose logs certificate-monitor'
exit 1
