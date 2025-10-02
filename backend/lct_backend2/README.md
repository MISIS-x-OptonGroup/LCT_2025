# Проект LCT Backend

## Установка и запуск

### Локальный запуск
1. Установите зависимости:
   ```
   pip install -r requirements.txt
   ```
2. Запустите приложение:
   ```
   uvicorn app.main:app --reload --port 8001
   ```

### Запуск через Docker
1. Установите Docker и Docker Compose
2. Соберите и запустите контейнер:
   ```
   docker-compose up --build
   ```

Приложение будет доступно по адресу `http://localhost:8001`