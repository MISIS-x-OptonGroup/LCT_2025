# Настройка S3 хранилища

Этот документ описывает как настроить S3 хранилище для работы с LCT Backend1.

## Варианты настройки

### 1. Amazon S3 (AWS)

#### Создание S3 bucket
1. Войдите в AWS Console
2. Перейдите в сервис S3
3. Создайте новый bucket:
   - Имя: `lct-backend1-images` (или любое уникальное имя)
   - Регион: выберите ближайший к вам
   - Настройки доступа: по умолчанию (приватный)

#### Создание IAM пользователя
1. Перейдите в IAM
2. Создайте нового пользователя:
   - Имя: `lct-backend1-s3-user`
   - Тип доступа: Programmatic access
3. Прикрепите политику:
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::lct-backend1-images",
                "arn:aws:s3:::lct-backend1-images/*"
            ]
        }
    ]
}
```

#### Настройка переменных окружения
```bash
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
S3_BUCKET_NAME=lct-backend1-images
S3_REGION=us-east-1
S3_ENDPOINT_URL=  # Оставьте пустым для AWS S3
```

### 2. MinIO (Self-hosted)

#### Установка MinIO
```bash
# Docker
docker run -p 9000:9000 -p 9001:9001 \
  --name minio \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin123" \
  -v /data:/data \
  quay.io/minio/minio server /data --console-address ":9001"
```

#### Создание bucket
1. Откройте MinIO Console: http://localhost:9001
2. Войдите (minioadmin / minioadmin123)
3. Создайте bucket `lct-backend1-images`

#### Настройка переменных окружения
```bash
AWS_ACCESS_KEY_ID=minioadmin
AWS_SECRET_ACCESS_KEY=minioadmin123
S3_BUCKET_NAME=lct-backend1-images
S3_REGION=us-east-1
S3_ENDPOINT_URL=http://localhost:9000
```

### 3. DigitalOcean Spaces

#### Создание Space
1. Войдите в DigitalOcean Console
2. Перейдите в Spaces
3. Создайте новый Space:
   - Имя: `lct-backend1-images`
   - Регион: выберите ближайший

#### Создание API ключей
1. Перейдите в API
2. Создайте новый Spaces access key
3. Сохраните Key и Secret

#### Настройка переменных окружения
```bash
AWS_ACCESS_KEY_ID=DO00...
AWS_SECRET_ACCESS_KEY=...
S3_BUCKET_NAME=lct-backend1-images
S3_REGION=nyc3
S3_ENDPOINT_URL=https://nyc3.digitaloceanspaces.com
```

## Проверка настройки

После настройки переменных окружения запустите тест:

```python
import asyncio
from app.services.s3_service import S3Service

async def test_s3():
    s3 = S3Service()
    
    # Тест создания файла
    test_content = b"Test content"
    s3_key, s3_url = await s3.upload_bytes(
        test_content, 
        "test.txt", 
        "text/plain"
    )
    print(f"Uploaded: {s3_key}")
    print(f"URL: {s3_url}")
    
    # Тест проверки существования
    exists = await s3.file_exists(s3_key)
    print(f"File exists: {exists}")
    
    # Тест получения подписанного URL
    signed_url = await s3.get_file_url(s3_key)
    print(f"Signed URL: {signed_url}")
    
    # Тест удаления
    deleted = await s3.delete_file(s3_key)
    print(f"Deleted: {deleted}")

# Запуск теста
asyncio.run(test_s3())
```

## Безопасность

### Рекомендации:
1. **Никогда не делайте bucket публичным** - все файлы должны быть приватными
2. **Используйте подписанные URLs** - для временного доступа к файлам
3. **Ограничьте права IAM пользователя** - только необходимые операции
4. **Используйте HTTPS** - для всех операций с S3
5. **Регулярно ротируйте ключи доступа**

### Настройка CORS (если нужен доступ из браузера):
```json
[
    {
        "AllowedHeaders": ["*"],
        "AllowedMethods": ["GET", "POST", "PUT", "DELETE"],
        "AllowedOrigins": ["https://yourdomain.com"],
        "ExposeHeaders": []
    }
]
```

## Мониторинг

### Метрики для отслеживания:
- Размер bucket
- Количество запросов
- Стоимость операций
- Ошибки доступа

### Логирование:
Сервис автоматически логирует все операции с S3:
- Успешные загрузки
- Ошибки доступа  
- Операции удаления

## Резервное копирование

Настройте автоматическое резервное копирование:
- AWS S3: Versioning + Cross-Region Replication
- MinIO: mc mirror команда
- DigitalOcean Spaces: Versioning

## Оптимизация затрат

1. **Используйте lifecycle policies** для автоматического перемещения старых файлов в более дешевые классы хранения
2. **Настройте мониторинг затрат** в AWS/DigitalOcean
3. **Регулярно очищайте неиспользуемые файлы**
4. **Используйте сжатие** для больших файлов (если применимо)
