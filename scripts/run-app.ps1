<#
.SYNOPSIS
  Runs the Certificate Expiry Monitor locally (Windows PowerShell).
.EXAMPLE
  .\scripts\run-app.ps1
  .\scripts\run-app.ps1 -Port 9090 -SpringProfile postgres
#>
param(
    [int]$Port = 8080,
    [string]$SpringProfile = '',
    [switch]$DemoData = $true
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$env:SERVER_PORT = "$Port"
$env:DEMO_DATA_ENABLED = if ($DemoData) { 'true' } else { 'false' }
if ($SpringProfile) { $env:SPRING_PROFILES_ACTIVE = $SpringProfile }

Write-Host "Starting Certificate Expiry Monitor on http://localhost:$Port" -ForegroundColor Green
& .\mvnw.cmd -B spring-boot:run
