@echo off
echo ========================================
echo   LCT Backend1 - Docker Setup
echo ========================================
echo.

:: Проверка наличия Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker не установлен или недоступен!
    echo Установите Docker Desktop: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

:: Проверка наличия docker-compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose не установлен или недоступен!
    pause
    exit /b 1
)

echo [INFO] Docker и Docker Compose обнаружены
echo.

:: Создание .env файла если не существует
if not exist .env (
    echo [INFO] Создание .env файла из примера...
    copy env.docker.example .env >nul
    echo [WARNING] Не забудьте настроить .env файл с вашими паролями!
    echo.
)

echo [INFO] Сборка Docker образов...
docker-compose build

if errorlevel 1 (
    echo [ERROR] Ошибка при сборке образов!
    pause
    exit /b 1
)

echo.
echo [INFO] Запуск сервисов...
docker-compose up -d

if errorlevel 1 (
    echo [ERROR] Ошибка при запуске сервисов!
    pause
    exit /b 1
)

echo.
echo [INFO] Ожидание готовности сервисов...
timeout /t 10 /nobreak >nul

echo.
echo [INFO] Применение миграций базы данных...
docker-compose exec -T app alembic upgrade head

echo.
echo ========================================
echo   Сервисы успешно запущены!
echo ========================================
echo.
echo API приложения:     http://localhost:8000
echo Swagger UI:         http://localhost:8000/docs
echo MinIO Console:      http://localhost:9001
echo   Логин: minioadmin / Пароль: minioadmin123
echo Nginx:              http://localhost:80
echo.
echo Для просмотра логов: docker-compose logs -f
echo Для остановки:      docker-compose down
echo.
pause
