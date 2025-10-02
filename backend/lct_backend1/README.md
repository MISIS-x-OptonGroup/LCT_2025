# LCT Backend1 - Сервис обработки изображений

Backend сервис на FastAPI для обработки изображений с интеграцией внешних сервисов.

## Функциональность

Сервис выполняет следующие функции:

1. **Прием изображений с Android** - принимает картинку и метаданные (время, место, автор)
2. **Сохранение в S3 и БД** - сохраняет фотки в Amazon S3, пути в PostgreSQL вместе с метаданными
3. **Интеграция с Backend2** - отправляет фотку на другой бекенд для разделения на фрагменты
4. **Обработка фрагментов** - принимает маленькие фотки, сохраняет в S3 и привязывает к основной
5. **Интеграция с Backend3** - отправляет фрагменты для получения JSON описаний
6. **Сохранение описаний** - сохраняет описания фрагментов в БД
7. **Управление файлами** - предоставляет подписанные URLs для скачивания из S3

## Структура проекта

```
lct_backend1/
├── app/
│   ├── api/
│   │   └── images.py          # API endpoints для изображений
│   ├── core/
│   │   ├── config.py          # Конфигурация приложения
│   │   └── database.py        # Настройка базы данных
│   ├── models/
│   │   ├── image.py           # Модель изображения
│   │   └── image_fragment.py  # Модель фрагмента изображения
│   ├── schemas/
│   │   ├── image.py           # Pydantic схемы для изображений
│   │   └── image_fragment.py  # Pydantic схемы для фрагментов
│   ├── services/
│   │   ├── image_service.py         # Сервис для работы с изображениями
│   │   ├── backend_integration.py   # Интеграция с внешними API
│   │   └── s3_service.py            # Сервис для работы с Amazon S3
│   └── main.py                # Главный файл приложения
├── alembic/                   # Миграции базы данных
├── requirements.txt           # Зависимости Python
├── run.py                     # Скрипт запуска сервера
├── env_example.txt            # Пример файла окружения
└── README.md                  # Этот файл
```

## 🚀 Быстрый запуск с Docker (рекомендуется)

### Запуск одной командой (Windows)
```cmd
start.bat
```

### Запуск с Make (Linux/macOS)
```bash
make setup
```

### Ручной запуск с Docker
```bash
# 1. Создайте .env файл
cp env.docker.example .env

# 2. Запустите сервисы
docker-compose build
docker-compose up -d

# 3. Примените миграции
docker-compose exec app alembic upgrade head
```

После запуска доступны:
- **API**: http://localhost:8000 (Swagger: http://localhost:8000/docs)
- **MinIO Console**: http://localhost:9001 (minioadmin/minioadmin123)
- **Nginx**: http://localhost:80
- **RabbitMQ UI**: http://localhost:15672 (app/app_password)

### RabbitMQ брокер

Теперь RabbitMQ поднимается вместе с основными сервисами в `docker-compose.yml`.

Доступы по умолчанию:

- UI: http://localhost:15672 (user: `app`, password: `app_password`)
- AMQP: `amqp://app:app_password@rabbitmq:5672/`

Переменные окружения для сервиса `app` уже заданы в compose. При необходимости переопределите через `.env`:

```bash
RABBITMQ_URL=amqp://app:app_password@rabbitmq:5672/
RABBITMQ_QUEUE_IMAGE_TASKS=image_tasks
RABBITMQ_QUEUE_IMAGE_RESULTS=image_results
```

Backend1 отправляет задачу в очередь `image_tasks` сразу после загрузки изображения. Backend_2 читает из `image_tasks` и публикует заглушку результата в `image_results`.

Подробная документация: [DOCKER_SETUP.md](DOCKER_SETUP.md)

---

## 🛠️ Локальная разработка (без Docker)

### 1. Установка зависимостей

```bash
pip install -r requirements.txt
```

### 2. Настройка окружения

Скопируйте `env_example.txt` в `.env` и настройте переменные:

```bash
# База данных PostgreSQL
DATABASE_URL=postgresql://username:password@localhost:5432/lct_backend1

# URLs внешних сервисов
BACKEND2_URL=http://localhost:8001
BACKEND3_URL=http://localhost:8002

# Настройки приложения
DEBUG=True
HOST=0.0.0.0
PORT=8000
```

### 3. Настройка базы данных

Создайте миграцию и примените её:

```bash
# Создание миграции
alembic revision --autogenerate -m "Initial migration"

# Применение миграции
alembic upgrade head
```

### 4. Запуск сервера

```bash
# Через run.py
python run.py

# Или напрямую через uvicorn
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## API Endpoints

### Загрузка изображения
```http
POST /api/v1/images/upload
Content-Type: multipart/form-data

file: [image file]
metadata: {"taken_at": "2024-01-01T12:00:00", "location": "Москва", "author": "Иван Иванов"}
```

### Получение списка изображений
```http
GET /api/v1/images/?skip=0&limit=100
```

### Получение изображения по ID
```http
GET /api/v1/images/{image_id}
```

### Скачивание изображения из S3
```http
GET /api/v1/images/{image_id}/download?expires_in=3600
```

### Скачивание фрагмента изображения из S3
```http
GET /api/v1/images/{image_id}/fragments/{fragment_id}/download?expires_in=3600
```

### Удаление изображения и всех фрагментов
```http
DELETE /api/v1/images/{image_id}
```

### Повторная обработка изображения
```http
POST /api/v1/images/{image_id}/reprocess
```

### Проверка здоровья сервиса
```http
GET /health
```

## Модели данных

### Image (Изображение)
- `id` - Уникальный идентификатор
- `filename` - Имя файла в S3
- `original_filename` - Оригинальное имя файла
- `content_type` - MIME тип файла
- `file_size` - Размер файла в байтах
- `s3_key` - Ключ файла в S3
- `s3_url` - URL файла в S3
- `s3_bucket` - Название S3 bucket
- `width`, `height` - Размеры изображения
- `taken_at` - Время создания фотографии
- `location` - Место съемки
- `author` - Автор фотографии
- `processing_status` - Статус обработки
- `created_at`, `updated_at` - Временные метки

### ImageFragment (Фрагмент изображения)
- `id` - Уникальный идентификатор
- `image_id` - ID родительского изображения
- `filename` - Имя файла фрагмента в S3
- `s3_key` - Ключ файла в S3
- `s3_url` - URL файла в S3
- `s3_bucket` - Название S3 bucket
- `position_x`, `position_y` - Позиция в оригинальном изображении
- `width`, `height` - Размеры фрагмента
- `description` - JSON описание от Backend3
- `description_text` - Текстовое описание
- `processing_status` - Статус обработки

## Интеграция с внешними сервисами

### Backend2 (Разделение изображений)
- **Endpoint**: `POST {BACKEND2_URL}/split-image`
- **Входные данные**: multipart/form-data с изображением
- **Выходные данные**: JSON с массивом путей к фрагментам

### Backend3 (Описание изображений)
- **Endpoint**: `POST {BACKEND3_URL}/describe-image`
- **Входные данные**: multipart/form-data с фрагментом изображения
- **Выходные данные**: JSON описание фрагмента

## Логирование

Сервис использует стандартное логирование Python. Логи выводятся в консоль с уровнем INFO.

## Разработка

### Создание новой миграции
```bash
alembic revision --autogenerate -m "Description of changes"
alembic upgrade head
```

### Запуск в режиме разработки
```bash
python run.py
# или
uvicorn app.main:app --reload
```

## Требования к системе

- Python 3.8+
- PostgreSQL 12+
- Amazon S3 или S3-совместимое хранилище (MinIO, DigitalOcean Spaces и т.д.)
- Доступ к Backend2 и Backend3 сервисам

## Поддерживаемые форматы изображений

- JPEG (.jpg, .jpeg)
- PNG (.png)
- BMP (.bmp)
- TIFF (.tiff)

Максимальный размер файла: 50MB
