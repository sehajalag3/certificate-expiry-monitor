<#
.SYNOPSIS
  Runs the full test suite (Windows PowerShell).
  Default run = 37 unit + integration tests. -Ui adds the 5 Selenium journeys.
.EXAMPLE
  .\scripts\run-tests.ps1
  .\scripts\run-tests.ps1 -Ui
  .\scripts\run-tests.ps1 -Ui -Visible   # watch Chrome run
#>
param(
    [switch]$Ui,
    [switch]$Visible
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host 'Running unit and integration tests...' -ForegroundColor Green
& .\mvnw.cmd -B test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Ui) {
    Write-Host 'Running Selenium journeys (requires Google Chrome)...' -ForegroundColor Green
    $headless = if ($Visible) { 'false' } else { 'true' }
    & .\mvnw.cmd -B test -Pui "-Dui.headless=$headless"
    exit $LASTEXITCODE
}
