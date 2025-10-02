"""
Конфигурация приложения
"""
import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Настройки приложения"""
    
    # База данных
    database_url: str = "postgresql://username:password@localhost:5432/lct_backend1"
    
    # URLs внешних сервисов
    backend2_url: str = "http://localhost:8001"
    backend3_url: str = "http://localhost:8002"
    
    # Настройки приложения
    debug: bool = True
    host: str = "0.0.0.0"
    port: int = 8000
    
    # Настройки загрузки файлов
    max_file_size: int = 50 * 1024 * 1024  # 50MB
    allowed_extensions: list = [".jpg", ".jpeg", ".png", ".bmp", ".tiff"]
    
    # S3 конфигурация
    aws_access_key_id: str = ""
    aws_secret_access_key: str = ""
    s3_bucket_name: str = "lct-backend1-images"
    s3_region: str = "us-east-1"
    s3_endpoint_url: str = ""  # Для MinIO или других S3-совместимых сервисов

    # RabbitMQ
    rabbitmq_url: str = "amqp://app:app_password@localhost:5672/"
    rabbitmq_queue_image_tasks: str = "image_tasks"
    rabbitmq_queue_image_results: str = "image_results"
    rabbitmq_queue_image_descriptions: str = "image_descriptions"
    
    class Config:
        env_file = ".env"


settings = Settings()
