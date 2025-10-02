# Docker Setup - Развертывание с S3 хранилищем

Этот документ описывает как развернуть LCT Backend1 с помощью Docker, включая встроенное S3 хранилище (MinIO).

## 🚀 Быстрый старт

### 1. Подготовка
```bash
# Клонируйте репозиторий
git clone <repository-url>
cd lct_backend1

# Создайте .env файл
cp env.docker.example .env
# Отредактируйте .env файл с вашими настройками
```

### 2. Запуск с помощью Make (рекомендуется)
```bash
# Первоначальная настройка и запуск
make setup

# Или по шагам:
make build    # Сборка образов
make up       # Запуск сервисов
```

### 3. Ручной запуск
```bash
# Сборка и запуск
docker-compose build
docker-compose up -d

# Применение миграций
docker-compose exec app alembic upgrade head
```

## 📦 Доступные сервисы

После запуска будут доступны:

- **🌐 API приложения**: http://localhost:8000
  - Swagger UI: http://localhost:8000/docs
  - Health check: http://localhost:8000/health

- **🗄️ MinIO Console**: http://localhost:9001
  - Логин: `minioadmin` / `minioadmin123`
  - Управление S3 bucket'ами

- **🔄 Nginx**: http://localhost:80
  - Reverse proxy для всех сервисов

- **📊 PostgreSQL**: localhost:5432
  - База данных приложения

## 🛠️ Управление через Make

```bash
# Основные команды
make help          # Показать все доступные команды
make up            # Запустить продакшн окружение
make down          # Остановить окружение
make logs          # Показать логи всех сервисов
make logs-app      # Показать логи только приложения

# Разработка
make dev-up        # Запустить окружение разработки
make dev-down      # Остановить окружение разработки

# Миграции
make migrate       # Применить миграции
make migrate-create MESSAGE="описание"  # Создать новую миграцию

# Подключение к контейнерам
make shell         # Подключиться к контейнеру приложения
make db-shell      # Подключиться к базе данных
make minio-shell   # Подключиться к MinIO

# Мониторинг
make status        # Статус всех сервисов
make monitor       # Мониторинг ресурсов
make health        # Проверка здоровья сервисов

# Тестирование
make test          # Запустить тесты в контейнере
make test-api      # Запустить тест API

# Бэкапы
make backup-db     # Создать бэкап базы данных
make restore-db BACKUP=backup_file.sql  # Восстановить из бэкапа

# Очистка
make clean         # Удалить все контейнеры и volumes
```

## 🔧 Конфигурации

### Основная конфигурация (docker-compose.yml)
- PostgreSQL база данных
- MinIO S3 хранилище
- FastAPI приложение
- Nginx reverse proxy

### Конфигурация для разработки (docker-compose.dev.yml)
- Только инфраструктурные сервисы
- Отдельные порты для избежания конфликтов
- Redis для кеширования

### Продакшн конфигурация (docker-compose.prod.yml)
- Дополнительная безопасность
- Мониторинг (Prometheus + Grafana)
- SSL поддержка
- Закрытые порты сервисов

## 🔐 Безопасность

### Переменные окружения
Обязательно измените пароли в `.env` файле:
```bash
DB_PASSWORD=your_secure_password_here
MINIO_ROOT_PASSWORD=your_secure_minio_password_here
GRAFANA_PASSWORD=your_secure_grafana_password_here
```

### Продакшн развертывание
```bash
# Использование продакшн конфигурации
docker-compose -f docker-compose.prod.yml up -d

# Или через Make (нужно добавить в Makefile)
make prod-up
```

## 🗂️ Структура volumes

```
volumes/
├── postgres_data/      # База данных PostgreSQL
├── minio_data/         # Файлы MinIO S3
├── nginx_logs/         # Логи Nginx
├── prometheus_data/    # Данные Prometheus
└── grafana_data/       # Данные Grafana
```

## 🐛 Отладка

### Просмотр логов
```bash
# Все сервисы
docker-compose logs -f

# Конкретный сервис
docker-compose logs -f app
docker-compose logs -f postgres
docker-compose logs -f minio
```

### Подключение к контейнерам
```bash
# Приложение
docker-compose exec app /bin/bash

# База данных
docker-compose exec postgres psql -U postgres -d lct_backend1

# MinIO
docker-compose exec minio /bin/sh
```

### Проверка состояния
```bash
# Статус контейнеров
docker-compose ps

# Использование ресурсов
docker stats

# Проверка сети
docker network ls
docker network inspect lct_backend1_lct_network
```

## 🔄 Обновление

### Обновление приложения
```bash
# Пересобрать только приложение
docker-compose build app
docker-compose up -d app

# Или через Make
make restart-app
```

### Полное обновление
```bash
# Остановить, пересобрать, запустить
make down
make build
make up
```

## 📊 Мониторинг

### Prometheus метрики
- Доступ: http://localhost:9090
- Метрики FastAPI приложения
- Метрики контейнеров
- Метрики PostgreSQL и MinIO

### Grafana дашборды
- Доступ: http://localhost:3000
- Логин: `admin` / пароль из `.env`
- Готовые дашборды для мониторинга

## 🆘 Решение проблем

### Проблема: Порты заняты
```bash
# Проверить какие порты используются
netstat -tulpn | grep :8000
netstat -tulpn | grep :5432

# Изменить порты в docker-compose.yml
```

### Проблема: Недостаточно места
```bash
# Очистить неиспользуемые образы и volumes
docker system prune -a --volumes

# Или через Make
make clean
```

### Проблема: MinIO не создает bucket
```bash
# Проверить логи minio-setup
docker-compose logs minio-setup

# Пересоздать bucket вручную
docker-compose exec minio-setup sh
mc alias set minio http://minio:9000 minioadmin minioadmin123
mc mb minio/lct-backend1-images --ignore-existing
```

### Проблема: Приложение не может подключиться к БД
```bash
# Проверить, что PostgreSQL готов
docker-compose exec postgres pg_isready -U postgres

# Проверить переменные окружения
docker-compose exec app env | grep DATABASE_URL

# Применить миграции вручную
docker-compose exec app alembic upgrade head
```

## 🏗️ Кастомизация

### Добавление новых сервисов
1. Добавьте сервис в `docker-compose.yml`
2. Обновите сеть `lct_network`
3. При необходимости обновите nginx.conf

### Изменение портов
1. Измените порты в `docker-compose.yml`
2. Обновите nginx.conf если нужно
3. Обновите документацию

### Добавление SSL
1. Поместите сертификаты в папку `ssl/`
2. Используйте `docker-compose.prod.yml`
3. Настройте nginx.prod.conf

## 📝 Логирование

Все логи сохраняются в Docker volumes:
- Приложение: `docker-compose logs app`
- Nginx: volume `nginx_logs`
- PostgreSQL: `docker-compose logs postgres`
- MinIO: `docker-compose logs minio`

Для централизованного логирования можно добавить ELK stack или аналогичное решение.
