@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ================================================================
echo   MediStock Pro 生产环境打包
echo ================================================================
echo.

REM ---------- 配置区 ----------
set "ROOT=%~dp0"
set "WEB=%ROOT%MediStockPro-web"
set "SERVER=%ROOT%MediStockPro-server"
set "DESKTOP=%ROOT%MediStockPro-desktop"
set "OUTPUT=%ROOT%dist-prod"
REM ----------------------------

rmdir /s /q "%OUTPUT%" 2>nul
mkdir "%OUTPUT%"

echo [1/3] 打包前端 (生产包)
echo -----------------------------------------------
pushd "%WEB%"
call npm run build
if errorlevel 1 (
    echo [失败] 前端构建失败
    popd & exit /b 1
)
xcopy /e /i /y "%WEB%\dist" "%OUTPUT%\web" >nul
echo [完成] 前端 -> %OUTPUT%\web
popd
echo.

echo [2/3] 打包后端 (jar)
echo -----------------------------------------------
pushd "%SERVER%"
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [失败] 后端构建失败
    popd & exit /b 1
)
copy "%SERVER%\target\medistock-pro-server.jar" "%OUTPUT%\medistock-pro-server.jar" >nul
echo [完成] 后端 -> %OUTPUT%\medistock-pro-server.jar
popd
echo.

echo [3/3] 打包桌面端 (NSIS 安装包)
echo -----------------------------------------------
pushd "%DESKTOP%"
call npm run build:nsis
if errorlevel 1 (
    echo [警告] 桌面端构建失败, 跳过 (不影响 Web 部署包)
) else (
    for %%F in ("%DESKTOP%\release\*.exe") do (
        copy "%%F" "%OUTPUT%\%%~nxF" >nul 2>nul
    )
    echo [完成] 桌面端 -> %OUTPUT%
)
popd
echo.

echo ================================================================
echo   全部完成! 产物目录: %OUTPUT%
echo ================================================================
echo   web\                      前端静态资源 (部署到 Nginx root)
echo   medistock-pro-server.jar  后端 jar
echo   *.exe                     桌面端安装包 (如有)
echo ================================================================

endlocal
