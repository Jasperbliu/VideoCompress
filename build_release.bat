@echo off
echo [*] Starting Build Release APK...
echo.

call gradlew.bat :app:assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Build Successful!
    echo.
    echo [*] Renaming APK...
    if exist "app\build\outputs\apk\release\VideoCompress.apk" del "app\build\outputs\apk\release\VideoCompress.apk"
    ren "app\build\outputs\apk\release\app-release.apk" "VideoCompress.apk"

    echo [*] APK Path: app\build\outputs\apk\release\VideoCompress.apk
    explorer "app\build\outputs\apk\release\"
) else (
    echo.
    echo [ERROR] Build Failed! Check logs above.
)

pause
