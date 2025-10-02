# Решение проблем

## ✅ Проблемы исправлены

### 1. Ошибка Pydantic "BaseSettings has been moved"
**Проблема**: `BaseSettings` перенесен в отдельный пакет в Pydantic 2.x

**Решение**: 
- ✅ Добавлен `pydantic-settings==2.1.0` в requirements.txt
- ✅ Изменен импорт в `app/core/config.py`: `from pydantic_settings import BaseSettings`

### 2. MinIO "Invalid login"
**Проблема**: Неправильные учетные данные

**Решение**: Используйте правильные данные из docker-compose.yml:
- **URL**: http://localhost:9001
- **Логин**: `minioadmin`
- **Пароль**: `minioadmin123`

### 3. Приложение не запускается
**Проблема**: Ошибки импорта и миграций

**Решение**:
- ✅ Исправлены зависимости Pydantic
- ✅ Убрано deprecated `version: '3.8'` из docker-compose.yml
- ✅ Пересобран образ приложения

## 🚀 Текущий статус

Все сервисы работают:
- ✅ **API**: http://localhost:8000 (Swagger: http://localhost:8000/docs)
- ✅ **MinIO Console**: http://localhost:9001 (minioadmin/minioadmin123)
- ✅ **PostgreSQL**: localhost:5432
- ✅ **Nginx**: http://localhost:80

## 🔧 Полезные команды для диагностики

### Проверка статуса сервисов
```bash
docker-compose ps
```

### Просмотр логов
```bash
# Все сервисы
docker-compose logs

# Конкретный сервис
docker-compose logs app
docker-compose logs minio
docker-compose logs postgres
```

### Проверка здоровья API
```bash
curl http://localhost:8000/health
```

### Перезапуск проблемного сервиса
```bash
# Только приложение
docker-compose restart app

# Все сервисы
docker-compose restart
```

### Пересборка при изменении кода
```bash
docker-compose build app
docker-compose up -d app
```

## 🆘 Если что-то не работает

### 1. Полный перезапуск
```bash
docker-compose down
docker-compose build
docker-compose up -d
```

### 2. Очистка и пересоздание
```bash
docker-compose down -v  # Удаляет volumes
docker-compose build --no-cache
docker-compose up -d
```

### 3. Проверка портов
```bash
netstat -tulpn | grep :8000
netstat -tulpn | grep :9001
```

### 4. Проверка Docker
```bash
docker --version
docker-compose --version
docker system df  # Использование места
```

## 📝 Логи ошибок

### Если приложение не запускается
1. Проверьте логи: `docker-compose logs app`
2. Убедитесь что PostgreSQL готов: `docker-compose logs postgres`
3. Проверьте миграции: `docker-compose exec app alembic current`

### Если MinIO не работает
1. Проверьте логи: `docker-compose logs minio`
2. Убедитесь что bucket создан: `docker-compose logs minio-setup`
3. Проверьте подключение: `curl http://localhost:9000/minio/health/live`

### Если база данных недоступна
1. Проверьте статус: `docker-compose exec postgres pg_isready -U postgres`
2. Подключитесь вручную: `docker-compose exec postgres psql -U postgres -d lct_backend1`
3. Проверьте переменные: `docker-compose exec app env | grep DATABASE_URL`
