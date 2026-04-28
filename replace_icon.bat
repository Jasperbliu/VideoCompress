@echo off
setlocal
set ICON_DEST=app\src\main\res\drawable\app_icon.png

:: 检查是否有拖拽文件，没有则默认使用根目录的 VideoCompress.png
if "%~1"=="" (
    set SOURCE=VideoCompress.png
) else (
    set SOURCE=%~1
)

if not exist "%SOURCE%" (
    echo [ERROR] File not found: %SOURCE%
    echo.
    echo Usage:
    echo 1. Place "VideoCompress.png" in this folder and run script.
    echo 2. OR Drag and drop any image file onto this .bat file.
    pause
    exit /b
)

echo [*] Replacing icon using: %SOURCE%

:: 创建目录（如果不存在）
if not exist "app\src\main\res\drawable" mkdir "app\src\main\res\drawable"

:: 执行替换
copy /Y "%SOURCE%" "%ICON_DEST%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Icon updated successfully!
    echo [*] Target: %ICON_DEST%
) else (
    echo.
    echo [ERROR] Failed to update icon.
)

pause
