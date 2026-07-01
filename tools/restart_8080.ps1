# 8080 重启脚本 — R133 验证
# 1. 停止旧 8080 实例
# 2. 启动新 8080 实例（含 R133 修复）
# 3. 验证 R133 编译错误已修复

$ErrorActionPreference = "Stop"
$BackendDir = "D:\zhutao\MED_RMS_PMS\Code\backend"

# 1. 检查权限
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[ERROR] 需要管理员权限" -ForegroundColor Red
    exit 1
}

# 2. 查找并停止 8080 进程
Write-Host "[1/4] 查找 8080 进程..." -ForegroundColor Cyan
$port8080 = netstat -ano | Select-String ":8080\s.*LISTENING" | ForEach-Object { ($_ -split '\s+')[-1] } | Select-Object -First 1
if ($port8080) {
    Write-Host "  发现 PID $port8080"
    Write-Host "[2/4] 停止 8080 (PID $port8080)..." -ForegroundColor Cyan
    try {
        Stop-Process -Id $port8080 -Force -ErrorAction Stop
        Start-Sleep -Seconds 5
        $stillRunning = netstat -ano | Select-String ":8080\s.*LISTENING"
        if ($stillRunning) {
            Write-Host "  [ERROR] 进程仍在" -ForegroundColor Red
            exit 1
        }
        Write-Host "  OK 8080 已停止" -ForegroundColor Green
    } catch {
        Write-Host "  [ERROR] $_" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  8080 端口空闲" -ForegroundColor Yellow
}

# 3. 启动新实例
Write-Host "[3/4] 启动新实例在 8080 (R133 修复)..." -ForegroundColor Cyan
Set-Location $BackendDir

# 重新 build 验证编译
Write-Host "  编译验证 (mvn compile)..." -NoNewline
$buildOutput = mvn -B -DskipTests -pl med-rms-web -am compile 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host " FAIL" -ForegroundColor Red
    Write-Host $buildOutput | Select-Object -Last 20
    exit 1
}
Write-Host " OK" -ForegroundColor Green

# 启动 mvn spring-boot:run（避免 jar lock 问题）
$logFile = "C:\temp\medrms-8080-r133-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
Write-Host "  启动 mvn spring-boot:run (日志: $logFile)..."

# R133 修复：必须在 med-rms-web 子目录执行（parent pom 无 main class）
$mvnProcess = Start-Process -FilePath "mvn" `
    -ArgumentList @("spring-boot:run", "-Dspring-boot.run.arguments=--server.port=8080", "-Dspring-boot.run.fork=true") `
    -WorkingDirectory "$BackendDir\med-rms-web" `
    -RedirectStandardOutput $logFile `
    -WindowStyle Hidden `
    -PassThru

Write-Host "  启动进程 PID=$($mvnProcess.Id)"

# 4. 等待就绪
Write-Host "[4/4] 等待 8080 就绪 (60s)..." -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
            -Method Post -ContentType "application/json" `
            -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 2
        if ($health.code -eq 200) {
            Write-Host "  OK 8080 已就绪 (${i}s)" -ForegroundColor Green
            $ready = $true
            break
        }
    } catch {
        # 继续等待
    }
}

if (-not $ready) {
    Write-Host "  [ERROR] 启动超时" -ForegroundColor Red
    Write-Host "  查看日志: $logFile" -ForegroundColor Yellow
    exit 1
}

# 5. 验证 R133 + R143 修复（关键端点）
Write-Host ""
Write-Host "=== R133 + R143 修复验证 ===" -ForegroundColor Cyan
$token = $health.data.token
$endpoints = @(
    @{Path="/api/auth/me"; Name="/auth/me (R120)"},
    @{Path="/api/requirements/stats"; Name="/requirements/stats (R115)"},
    @{Path="/api/compliance/audit-logs/verify/detailed"; Name="verifyChain (R115)"},
    @{Path="/api/requirements/quality?projectId=8"; Name="质量评分 (R143 性能)"},
    @{Path="/api/gantt/milestones/project/8"; Name="里程碑列表 (R143 修复)"}
)
foreach ($ep in $endpoints) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080$($ep.Path)" `
            -Method Get -Headers @{Authorization = "Bearer $token"} -TimeoutSec 5
        $code = $r.code
        $sym = if ($code -eq 200) { "OK" } else { "WARN" }
        Write-Host "  [$sym] $($ep.Name): code=$code"
    } catch {
        Write-Host "  [FAIL] $($ep.Name): $_" -ForegroundColor Red
    }
}

# R143 性能测试：质量评分应在 200ms 内
Write-Host ""
Write-Host "  性能测试: /requirements/quality?projectId=8"
$perfTimes = @()
for ($i = 1; $i -le 3; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/requirements/quality?projectId=8" `
            -Method Get -Headers @{Authorization = "Bearer $token"} -TimeoutSec 5 | Out-Null
    } catch {}
    $sw.Stop()
    $perfTimes += $sw.ElapsedMilliseconds
}
$avgPerf = ($perfTimes | Measure-Object -Average).Average
Write-Host "    平均: $([int]$avgPerf)ms (R143 目标: <500ms)"

# R143 功能测试：创建里程碑
Write-Host ""
Write-Host "  功能测试: POST /gantt/milestones (不传 milestoneNo)"
try {
    $body = @{
        projectId = 8
        name = "M-Restart-$((Get-Date).Ticks)"
        plannedDate = "2026-07-25"
        gateType = "DCP1"
    } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "http://localhost:8080/api/gantt/milestones" `
        -Method Post -Headers @{Authorization = "Bearer $token"; "Content-Type" = "application/json"} `
        -Body $body -TimeoutSec 5
    $code = $r.code
    $msNo = if ($r.data.milestoneNo) { $r.data.milestoneNo } else { "(无)" }
    Write-Host "    HTTP=$code milestoneNo=$msNo (R143 目标: 自动生成 MS-XXXXXX)"
} catch {
    Write-Host "    [FAIL] $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== R133 重启完成 ===" -ForegroundColor Green
Write-Host "  8080 运行: mvn spring-boot:run (R133 修复代码)"
Write-Host "  日志: $logFile"