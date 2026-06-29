@echo off
REM ========================================================
REM Med-RMS CI 一键测试脚本（v2.4 — W6-D3 定型）
REM 用法：tools\run-tests.cmd [phase]
REM phase: all（默认）/ unit / integration / contract / smoke
REM 输出：tools\reports\<YYYYMMDD>\
REM 退出码：0 成功 / 1 失败
REM ========================================================
set PHASE=%1
if "%PHASE%"=="" set PHASE=all

set BASE=D:\zhutao\MED_RMS_PMS\Code\backend
set DATE_DIR=%date:~0,4%%date:~5,2%%date:~8,2%
set TS=%time:~0,2%%time:~3,2%%time:~6,2%
set REPORT_DIR=%BASE%\tools\reports\%DATE_DIR%
if not exist %REPORT_DIR% mkdir %REPORT_DIR%

echo ============================================================
echo Med-RMS Test Runner — phase=%PHASE%  %DATE_DIR% %TS%
echo ============================================================

REM 各阶段测试匹配模式（基于包路径）
set TEST_PATTERN=
if "%PHASE%"=="unit" set TEST_PATTERN=*service*Test,*controller*Test
if "%PHASE%"=="integration" set TEST_PATTERN=*IntegrationTest,*FlowIntegrationTest
if "%PHASE%"=="contract" set TEST_PATTERN=*ContractTest
if "%PHASE%"=="smoke" set TEST_PATTERN=ApiIntegrationTest
if "%PHASE%"=="all" set TEST_PATTERN=*Test

cd /d %BASE%

REM 阶段 1：单元测试
echo.
echo [1/4] 单元测试（Service + Controller）
call mvn test -Dtest="*service*Test,*controller*Test" -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.javadoc.skip=true 2>&1 | tee "%REPORT_DIR%\unit-%TS%.log" | findstr /R "Tests run:.*Failures.*Errors.*Skipped"
if errorlevel 1 (
    echo [FAIL] 单元测试阶段失败
    goto :fail
)
if "%PHASE%"=="unit" goto :end

REM 阶段 2：集成测试
echo.
echo [2/4] 集成测试（跨模块 + SpringBoot）
call mvn test -Dtest="*IntegrationTest" -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.javadoc.skip=true 2>&1 | tee "%REPORT_DIR%\integration-%TS%.log" | findstr /R "Tests run:.*Failures.*Errors.*Skipped"
if errorlevel 1 (
    echo [FAIL] 集成测试阶段失败
    goto :fail
)
if "%PHASE%"=="integration" goto :end

REM 阶段 3：契约 + 冒烟
echo.
echo [3/4] 契约 + 冒烟
call mvn test -Dtest="*ContractTest,ApiIntegrationTest" -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.javadoc.skip=true 2>&1 | tee "%REPORT_DIR%\contract-%TS%.log" | findstr /R "Tests run:.*Failures.*Errors.*Skipped"
if errorlevel 1 (
    echo [FAIL] 契约/冒烟测试阶段失败
    goto :fail
)

REM 阶段 4：JaCoCo 报告
echo.
echo [4/4] JaCoCo 报告
call mvn jacoco:report -Dmaven.javadoc.skip=true 2>&1 | tee "%REPORT_DIR%\jacoco-%TS%.log" | findstr /R "Analyzed bundle"

:ok
echo.
echo ============================================================
echo 测试完成 (%PHASE%)
echo 报告目录：%REPORT_DIR%
echo ============================================================
exit /b 0

:fail
echo.
echo ============================================================
echo [FAIL] 测试在 %PHASE% 阶段失败
echo 详细日志：%REPORT_DIR%
echo ============================================================
exit /b 1

:end
echo.
echo 单阶段完成 (%PHASE%)
exit /b 0
