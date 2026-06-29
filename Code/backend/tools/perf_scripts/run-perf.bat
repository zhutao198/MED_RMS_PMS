@echo off
REM Med-RMS 性能压测执行器（W5-D1~3）
REM 用法：tools\perf_scripts\run-perf.bat [scenario]
REM scenario: all（默认）/ list / matrix / impact / jmeter

setlocal EnableDelayedExpansion
set BASE=D:\zhutao\MED_RMS_PMS\Code\backend\tools
set SCENARIO=%1
if "%SCENARIO%"=="" set SCENARIO=all

set DATE_DIR=%date:~0,4%%date:~5,2%%date:~8,2%
set TS=%time:~0,2%%time:~3,2%%time:~6,2%
set REPORT_DIR=%BASE%\perf_reports\%DATE_DIR%
if not exist %REPORT_DIR% mkdir %REPORT_DIR%

echo ============================================================
echo Med-RMS 性能压测 — scenario=%SCENARIO%  %DATE_DIR% %TS%
echo ============================================================

REM 检查后端可达
curl -s -m 3 http://localhost:8080/auth/login -o nul -w "后端状态: HTTP %%{http_code}\n" 2>&1
if errorlevel 1 (
    echo [WARN] 后端可能未启动，将尝试启动
    call :start_backend
)

if "%SCENARIO%"=="list" goto list
if "%SCENARIO%"=="matrix" goto matrix
if "%SCENARIO%"=="impact" goto impact
if "%SCENARIO%"=="jmeter" goto jmeter
goto all

:all
echo.
echo [1/3] 需求列表 5000 条
call :list
echo.
echo [2/3] 追溯矩阵
call :matrix
echo.
echo [3/3] 影响评估
call :impact
goto end

:list
echo   - 需求列表 5000 条 P95 ≤3s
call %BASE%\bin\run.cmd k6 run %BASE%\perf_scripts\baseline-requirements-list.js --out json=%REPORT_DIR%\requirements-list-%TS%.json 2>&1 | tee %REPORT_DIR%\requirements-list-%TS%.log
goto :eof

:matrix
echo   - 追溯矩阵 P95 ≤5s
call %BASE%\bin\run.cmd k6 run %BASE%\perf_scripts\baseline-traceability-matrix.js --out json=%REPORT_DIR%\traceability-matrix-%TS%.json 2>&1 | tee %REPORT_DIR%\traceability-matrix-%TS%.log
goto :eof

:impact
echo   - 影响评估 P95 ≤10s
call %BASE%\bin\run.cmd k6 run %BASE%\perf_scripts\baseline-impact-assessment.js --out json=%REPORT_DIR%\impact-assessment-%TS%.json 2>&1 | tee %REPORT_DIR%\impact-assessment-%TS%.log
goto :eof

:jmeter
echo   - JMeter 登录并发 100 / 需求创建 50
call :check_jmeter
goto :eof

:check_jmeter
if not exist %BASE%\perf_scripts\jmeter-scenarios.jmx (
    echo [ERROR] JMeter 场景文件不存在: %BASE%\perf_scripts\jmeter-scenarios.jmx
    echo 请先用 JMeter GUI 创建场景
    exit /b 1
)
call %BASE%\bin\run.cmd jmeter -n -t %BASE%\perf_scripts\jmeter-scenarios.jmx -l %REPORT_DIR%\jmeter-results-%TS%.jtl -e -o %REPORT_DIR%\jmeter-html-%TS% 2>&1 | tee %REPORT_DIR%\jmeter-%TS%.log
goto :eof

:start_backend
echo   - 启动后端...
cd %BASE%\..\..\backend
start "Med-RMS Backend" mvn spring-boot:run -pl med-rms-web -Dspring-boot.run.profiles=dev
timeout /t 60 /nobreak > nul
curl -s -m 3 http://localhost:8080/auth/login -o nul -w "启动状态: HTTP %%{http_code}\n"
cd %BASE%\..\..

:end
echo.
echo ============================================================
echo 压测完成 — 报告归档 %REPORT_DIR%
echo ============================================================
exit /b 0
