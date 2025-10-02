"""
Сервисы приложения
"""
from .image_service import ImageService
from .s3_service import S3Service
from .mq_service import MQService
from .consumer_service import ConsumerService

__all__ = ["ImageService", "S3Service", "MQService", "ConsumerService"]
