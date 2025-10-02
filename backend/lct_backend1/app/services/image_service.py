"""
Сервис для работы с изображениями
"""
import os
import uuid
import aiofiles
import tempfile
from pathlib import Path
from PIL import Image as PILImage
from fastapi import UploadFile
from sqlalchemy.orm import Session
from typing import Optional, List
import io

from app.models.image import Image
from app.models.image_fragment import ImageFragment
from app.schemas.image import ImageMetadata
from app.core.config import settings
from app.services.mq_service import MQService
from app.services.s3_service import S3Service
import logging

logger = logging.getLogger(__name__)


class ImageService:
    """
    Сервис для работы с изображениями
    """
    
    def __init__(self):
        self.s3_service = S3Service()
        self.mq_service = MQService()
    
    async def save_uploaded_image(
        self, 
        db: Session, 
        file: UploadFile, 
        metadata: Optional[ImageMetadata] = None
    ) -> Image:
        """
        Сохраняет загруженное изображение в S3 и базу данных
        
        Args:
            db: Сессия базы данных
            file: Загруженный файл
            metadata: Метаданные изображения
            
        Returns:
            Image: Созданная запись изображения
        """
        # Проверка размера файла
        if file.size and file.size > settings.max_file_size:
            raise ValueError(f"Размер файла превышает максимально допустимый ({settings.max_file_size} байт)")
        
        # Проверка расширения файла
        file_extension = Path(file.filename).suffix.lower()
        if file_extension not in settings.allowed_extensions:
            raise ValueError(f"Неподдерживаемый тип файла. Разрешены: {settings.allowed_extensions}")
        
        # Загрузка в S3
        s3_key, s3_url = await self.s3_service.upload_file(file, "images")
        
        # Получение размеров изображения из временного файла
        await file.seek(0)
        content = await file.read()
        width, height = await self._get_image_dimensions_from_bytes(content)
        
        # Создание записи в базе данных
        db_image = Image(
            filename=Path(s3_key).name,  # Извлекаем имя файла из S3 ключа
            original_filename=file.filename,
            content_type=file.content_type,
            file_size=len(content),
            s3_key=s3_key,
            s3_url=s3_url,
            s3_bucket=settings.s3_bucket_name,
            width=width,
            height=height,
            processing_status="uploaded"
        )
        
        # Добавление метаданных если есть
        if metadata:
            db_image.taken_at = metadata.taken_at
            db_image.location = metadata.location
            db_image.author = metadata.author
        
        db.add(db_image)
        db.commit()
        db.refresh(db_image)
        
        logger.info(f"Изображение сохранено в S3: {s3_key} (ID: {db_image.id})")
        return db_image
    
    async def process_image(self, db: Session, image_id: int) -> Image:
        """
        Обновляет статус изображения для обработки через RabbitMQ
        Фактическая обработка происходит асинхронно через очереди
        
        Args:
            db: Сессия базы данных
            image_id: ID изображения
            
        Returns:
            Image: Обновленная запись изображения
        """
        image = db.query(Image).filter(Image.id == image_id).first()
        if not image:
            raise ValueError(f"Изображение с ID {image_id} не найдено")
        
        try:
            # Обновляем статус на "обрабатывается"
            image.processing_status = "processing"
            db.commit()
            
            # Получаем подписанный URL для изображения из S3
            image_url = await self.s3_service.get_file_url(image.s3_key)
            
            # Отправляем задачу в RabbitMQ для Backend2
            await self.mq_service.publish_image_task({
                "image_id": image.id,
                "s3_key": image.s3_key,
                "s3_url": image_url,  # Подписанный URL с доступом
                "content_type": image.content_type,
            })
            
            logger.info(f"Задача обработки изображения {image_id} отправлена в очередь")
            return image
            
        except Exception as e:
            logger.error(f"Ошибка при отправке задачи обработки изображения {image_id}: {e}")
            image.processing_status = "error"
            db.commit()
            raise
    
    async def process_description_result(self, db: Session, result_data: dict):
        """
        Обрабатывает результат описания изображения из RabbitMQ
        
        Args:
            db: Сессия базы данных
            result_data: Данные результата от Backend3
        """
        try:
            image_id = result_data.get("image_id")
            description_text = result_data.get("text", "")
            detected_objects = result_data.get("detected_objects", [])
            
            image = db.query(Image).filter(Image.id == image_id).first()
            if not image:
                logger.error(f"Изображение с ID {image_id} не найдено")
                return
            
            # Обновляем описание изображения
            image.description_text = description_text
            image.processing_status = "completed"
            
            # Сохраняем координаты найденных объектов как JSON
            if detected_objects:
                import json
                image.detected_objects = json.dumps(detected_objects, ensure_ascii=False)
            
            db.commit()
            
            logger.info(f"Описание и координаты для изображения {image_id} успешно сохранены")
            
        except Exception as e:
            logger.error(f"Ошибка при обработке результата описания: {e}")
            if 'image_id' in locals():
                image = db.query(Image).filter(Image.id == image_id).first()
                if image:
                    image.processing_status = "error"
                    db.commit()
    
    async def _get_image_dimensions_from_bytes(self, image_bytes: bytes) -> tuple[int, int]:
        """
        Получает размеры изображения из байтов
        """
        try:
            with PILImage.open(io.BytesIO(image_bytes)) as img:
                return img.size
        except Exception as e:
            logger.warning(f"Не удалось получить размеры изображения из байтов: {e}")
            return (0, 0)
    
    def _get_image_dimensions(self, image_path: str) -> tuple[int, int]:
        """
        Получает размеры изображения из файла (для совместимости)
        """
        try:
            with PILImage.open(image_path) as img:
                return img.size
        except Exception as e:
            logger.warning(f"Не удалось получить размеры изображения {image_path}: {e}")
            return (0, 0)
    
    def get_image_by_id(self, db: Session, image_id: int) -> Optional[Image]:
        """
        Получает изображение по ID
        """
        return db.query(Image).filter(Image.id == image_id).first()
    
    def get_images(self, db: Session, skip: int = 0, limit: int = 100) -> List[Image]:
        """
        Получает список изображений с пагинацией
        """
        return db.query(Image).offset(skip).limit(limit).all()
