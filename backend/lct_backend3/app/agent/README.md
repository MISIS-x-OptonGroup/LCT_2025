# NVIDIA AI Image Analysis API

FastAPI приложение для анализа изображений с помощью NVIDIA AI API. Основано на модели `meta/llama-3.2-90b-vision-instruct`.

## Возможности

- 📸 Анализ изображений с помощью AI
- 🎯 Настраиваемые промпты для анализа
- 🔧 Настройка параметров генерации (temperature, max_tokens)
- 🐳 Полная контейнеризация с Docker
- 📚 Автоматическая документация API
- ❤️ Health check эндпоинт

## Быстрый запуск

### Предварительные требования

1. Docker и Docker Compose
2. NVIDIA API ключ (получить на [NVIDIA AI](https://build.nvidia.com/))

### Запуск с Docker Compose

1. Клонируйте репозиторий и перейдите в директорию проекта

2. Создайте файл `.env` и добавьте ваш API ключ:
```bash
NVIDIA_API_KEY=ваш_api_ключ_здесь
```

3. Запустите приложение:
```bash
docker-compose up --build
```

4. API будет доступно по адресу: http://localhost:8000

### Запуск с Docker

```bash
# Сборка образа
docker build -t nvidia-ai-api .

# Запуск контейнера
docker run -p 8000:8000 -e NVIDIA_API_KEY=ваш_api_ключ_здесь nvidia-ai-api
```

### Локальный запуск (без Docker)

```bash
# Установка зависимостей
pip install -r requirements.txt

# Установка переменной окружения
export NVIDIA_API_KEY=ваш_api_ключ_здесь

# Запуск приложения
python main.py
```

## Использование API

### Эндпоинты

- `GET /` - Главная страница
- `GET /health` - Проверка состояния сервиса
- `POST /analyze` - Анализ изображения
- `GET /docs` - Интерактивная документация Swagger
- `GET /redoc` - Альтернативная документация ReDoc

### Анализ изображения

**POST** `/analyze`

#### Параметры:
- `file` (обязательный) - Изображение для анализа (PNG, JPG, JPEG)
- `prompt` (обязательный) - Текстовый промпт для анализа
- `temperature` (опциональный, по умолчанию 1.0) - Параметр креативности (0.0-2.0)
- `max_tokens` (опциональный, по умолчанию 512) - Максимальная длина ответа

#### Пример запроса с curl:
```bash
curl -X POST "http://localhost:8000/analyze" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@image.png" \
  -F "prompt=Опиши что изображено на этой фотографии на русском языке" \
  -F "temperature=0.8" \
  -F "max_tokens=256"
```

#### Пример ответа:
```json
{
  "result": "На изображении показан...",
  "model_used": "meta/llama-3.2-90b-vision-instruct"
}
```

### Пример использования с Python

```python
import requests

# Отправка изображения на анализ
with open("image.png", "rb") as f:
    response = requests.post(
        "http://localhost:8000/analyze",
        files={"file": f},
        data={
            "prompt": "Что изображено на этой фотографии?",
            "temperature": 0.8,
            "max_tokens": 300
        }
    )

result = response.json()
print(result["result"])
```

### Пример использования с JavaScript

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('prompt', 'Опиши эту картинку');
formData.append('temperature', '0.8');

fetch('http://localhost:8000/analyze', {
    method: 'POST',
    body: formData
})
.then(response => response.json())
.then(data => console.log(data.result));
```

## Настройка

### Переменные окружения

- `NVIDIA_API_KEY` - API ключ NVIDIA (обязательный)
- `PORT` - Порт для запуска приложения (по умолчанию 8000)

### Поддерживаемые форматы изображений

- PNG
- JPG/JPEG
- Другие форматы, поддерживаемые браузером

## Структура проекта

```
.
├── main.py              # Основное приложение FastAPI
├── requirements.txt     # Python зависимости
├── Dockerfile          # Docker образ
├── docker-compose.yml  # Docker Compose конфигурация
├── README.md           # Документация
└── .env               # Переменные окружения (создать самостоятельно)
```

## Разработка

### Локальная разработка

```bash
# Установка зависимостей для разработки
pip install -r requirements.txt

# Запуск с автоперезагрузкой
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Логирование

Приложение использует стандартное логирование FastAPI. В production рекомендуется настроить structured logging.

## Безопасность

- API ключ передается через переменные окружения
- Приложение запускается от непривилегированного пользователя в Docker
- Валидация типов файлов
- Ограничения на размер файлов (настраиваются в FastAPI)

## Лицензия

MIT License

## Поддержка

При возникновении проблем:
1. Проверьте правильность API ключа NVIDIA
2. Убедитесь, что изображение в поддерживаемом формате
3. Проверьте логи контейнера: `docker-compose logs`

---

**Примечание:** Для использования API требуется действующий API ключ от NVIDIA. Получить его можно на [build.nvidia.com](https://build.nvidia.com/).
