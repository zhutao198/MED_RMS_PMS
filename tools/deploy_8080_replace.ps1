# 8080 实例替换脚本 — R127
# 解决后端 8080 端口仍运行旧代码的问题
# 用法（需管理员 PowerShell）：
#   1. 右键 PowerShell → "以管理员身份运行"
#   2. cd D:\zhutao\MED_RMS_PMS
#   3. .\tools\deploy_8080_replace.ps1

param(
    [int]$NewPort = 8080,
    [int]$OldPort = 8088,
    [string]$BackendDir = "D:\zhutao\MED_RMS_PMS\Code\backend"
)

# ============================================================
# R127 部署脚本：8080 实例替换
# ============================================================

$ErrorActionPreference = "Stop"

Write-Host "=== R127 部署脚本：后端 8080 实例替换 ===" -ForegroundColor Cyan
Write-Host ""

# 1. 检查权限
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[ERROR] 需要管理员权限运行 PowerShell" -ForegroundColor Red
    Write-Host "  右键 PowerShell → 以管理员身份运行" -ForegroundColor Yellow
    exit 1
}
Write-Host "[1/6] 管理员权限检查通过" -ForegroundColor Green

# 2. 检查端口占用
$port8080 = netstat -ano | Select-String ":$NewPort\s.*LISTENING" | ForEach-Object { ($_ -split '\s+')[-1] }
$port8088 = netstat -ano | Select-String ":$OldPort\s.*LISTENING" | ForEach-Object { ($_ -split '\s+')[-1] }
Write-Host "[2/6] 端口状态: $NewPort=PID $port8080, $OldPort=PID $port8088" -ForegroundColor Green

if (-not $port8080) {
    Write-Host "[ERROR] $NewPort 端口无进程，无需替换" -ForegroundColor Red
    exit 1
}

# 3. 验证 8088 是新代码（含 R115 stats 端点）
Write-Host "[3/6] 验证 $OldPort 是新代码..." -ForegroundColor Cyan
try {
    $loginResp = Invoke-RestMethod -Uri "http://localhost:$OldPort/api/auth/login" `
        -Method Post -ContentType "application/json" `
        -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 5
    $token = $loginResp.data.token

    $statsResp = Invoke-RestMethod -Uri "http://localhost:$OldPort/api/requirements/stats" `
        -Method Get -Headers @{Authorization = "Bearer $token"} -TimeoutSec 5

    if ($statsResp.code -eq 200 -and $statsResp.data.total -gt 0) {
        Write-Host "  ✅ $OldPort 是新代码（stats total=$($statsResp.data.total)）" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] $OldPort stats 端点异常，不应替换" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  [ERROR] $OldPort 验证失败: $_" -ForegroundColor Red
    exit 1
}

# 4. 杀掉 8080 进程
Write-Host "[4/6] 停止 $NewPort 进程 (PID $port8080)..." -ForegroundColor Cyan
try {
    Stop-Process -Id $port8080 -Force -ErrorAction Stop
    Start-Sleep -Seconds 3
    $stillRunning = netstat -ano | Select-String ":$NewPort\s.*LISTENING"
    if ($stillRunning) {
        Write-Host "  [ERROR] 进程仍在运行" -ForegroundColor Red
        exit 1
    }
    Write-Host "  ✅ $NewPort 已停止" -ForegroundColor Green
} catch {
    Write-Host "  [ERROR] 停止失败: $_" -ForegroundColor Red
    exit 1
}

# 5. 启动新实例在 8080
Write-Host "[5/6] 启动新实例在 $NewPort..." -ForegroundColor Cyan
$logFile = "C:\temp\medrms-8080-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
$startArgs = @{
    FilePath = "java"
    ArgumentList = @("-jar", "$BackendDir\med-rms-web\target\med-rms-web-1.0.0-SNAPSHOT.jar", "--server.port=$NewPort", "--spring.profiles.active=dev")
    RedirectStandardOutput = $logFile
    WindowStyle = "Hidden"
}
Start-Process @startArgs

# 等待就绪
Write-Host "  等待启动..." -NoNewline
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:$NewPort/api/auth/login" `
            -Method Post -ContentType "application/json" `
            -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 2
        if ($health.code -eq 200) {
            Write-Host ""
            Write-Host "  ✅ $NewPort 已就绪 ($i 秒)" -ForegroundColor Green
            break
        }
    } catch {
        # 继续等待
    }
    Write-Host "." -NoNewline
    if ($i -eq 60) {
        Write-Host ""
        Write-Host "  [ERROR] 启动超时（60 秒）" -ForegroundColor Red
        Write-Host "  查看日志: $logFile" -ForegroundColor Yellow
        exit 1
    }
}

# 6. 停止 8088（验证后）
Write-Host "[6/6] 停止 $OldPort 进程 (PID $port8088)..." -ForegroundColor Cyan
if ($port8088) {
    try {
        Stop-Process -Id $port8088 -Force -ErrorAction Stop
        Start-Sleep -Seconds 2
        Write-Host "  ✅ $OldPort 已停止" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] 停止 $OldPort 失败（可手动 kill）" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== ✅ R127 部署完成 ===" -ForegroundColor Green
Write-Host "  后端运行在: http://localhost:$NewPort"
Write-Host "  启动日志: $logFile"
Write-Host ""
Write-Host "验证 R115 修复："
Write-Host "  curl -H 'Authorization: Bearer TOKEN' http://localhost:$NewPort/api/requirements/stats"