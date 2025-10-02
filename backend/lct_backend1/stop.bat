@echo off
echo ========================================
echo   LCT Backend1 - Остановка сервисов
echo ========================================
echo.

echo [INFO] Остановка всех сервисов...
docker-compose down

if errorlevel 1 (
    echo [ERROR] Ошибка при остановке сервисов!
    pause
    exit /b 1
)

echo.
echo [INFO] Сервисы успешно остановлены!
echo.
echo Для полной очистки (удаление volumes): docker-compose down -v
echo Для удаления образов: docker-compose down --rmi all
echo.
pause
