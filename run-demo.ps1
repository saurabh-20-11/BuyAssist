# BuyAssist one-click demo launcher (Windows PowerShell).
#
# Starts the Python AP2 service (port 8081) and the Java backend (port 8080),
# waits for both to be healthy, then opens the web console in your browser.
#
# Usage:  right-click -> "Run with PowerShell", or from a terminal:  .\run-demo.ps1
# Stop:   close the two service windows it opens, or press Ctrl+C in each.

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$jdk  = "C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot"

Write-Host "`n=== BuyAssist demo launcher ===`n" -ForegroundColor Cyan

# --- preflight checks ---
if (-not (Test-Path $jdk)) {
    Write-Host "JDK 21 not found at:`n  $jdk" -ForegroundColor Red
    Write-Host "Edit the `$jdk path at the top of this script to match your install." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"; exit 1
}
$jar = Join-Path $root "java-backend\build\libs\buyassist-backend-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    Write-Host "Backend jar not found. Building it now (one-time, ~1 min)..." -ForegroundColor Yellow
    Push-Location (Join-Path $root "java-backend")
    $env:JAVA_HOME = $jdk
    & ".\gradlew.bat" bootJar --no-daemon
    Pop-Location
}
$venvPy = Join-Path $root "ap2-service\.venv\Scripts\python.exe"
if (-not (Test-Path $venvPy)) {
    Write-Host "AP2 service venv not found at ap2-service\.venv." -ForegroundColor Red
    Write-Host "Set it up once (see ap2-service\README.md):" -ForegroundColor Yellow
    Write-Host "  cd ap2-service; python -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt; .\.venv\Scripts\python.exe -m pip install `"git+https://github.com/google-agentic-commerce/AP2.git`"" -ForegroundColor Gray
    Read-Host "Press Enter to exit"; exit 1
}

# --- start the AP2 service in its own window ---
Write-Host "Starting AP2 service on http://localhost:8081 ..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "cd '$root\ap2-service'; .\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8081"
) -WindowStyle Minimized

# --- start the Java backend in its own window ---
Write-Host "Starting backend on http://localhost:8080 ..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "`$env:JAVA_HOME='$jdk'; cd '$root\java-backend'; & '$jdk\bin\java.exe' -jar '$jar'"
) -WindowStyle Minimized

# --- wait for both to be healthy ---
function Wait-Up($url, $name, $timeoutSec = 60) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        try { Invoke-RestMethod -Uri $url -TimeoutSec 3 | Out-Null; return $true } catch { Start-Sleep -Seconds 2 }
        Write-Host "  waiting for $name ..." -ForegroundColor DarkGray
    }
    return $false
}

Write-Host "`nWaiting for services to come up..." -ForegroundColor Cyan
$ap2Ok = Wait-Up "http://localhost:8081/health" "AP2 service"
$beOk  = Wait-Up "http://localhost:8080/api/procurement/products" "backend"

if ($ap2Ok -and $beOk) {
    Write-Host "`nBoth services are up. Opening the console..." -ForegroundColor Green
    Start-Process "http://localhost:8080"
    Write-Host "`nDemo is live at http://localhost:8080" -ForegroundColor Cyan
    Write-Host "Use Chrome or Edge for the microphone." -ForegroundColor Yellow
    Write-Host "To stop: close the two minimized PowerShell windows.`n" -ForegroundColor Gray
} else {
    Write-Host "`nA service did not come up in time." -ForegroundColor Red
    if (-not $ap2Ok) { Write-Host "  - AP2 service (8081) not healthy" -ForegroundColor Red }
    if (-not $beOk)  { Write-Host "  - backend (8080) not healthy" -ForegroundColor Red }
    Write-Host "Check the two service windows for errors." -ForegroundColor Yellow
}
Read-Host "Press Enter to close this launcher window"
