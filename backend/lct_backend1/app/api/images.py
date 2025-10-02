"""
API endpoints для работы с изображениями
"""
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, BackgroundTasks
from sqlalchemy.orm import Session
from typing import List, Optional
import json

from app.core.database import get_db
from app.services.image_service import ImageService
from app.services.s3_service import S3Service
from app.services.mq_service import MQService
from app.schemas.image import ImageResponse, ImageMetadata
import logging

logger = logging.getLogger(__name__)

router = APIRouter()
image_service = ImageService()
s3_service = S3Service()
mq_service = MQService()


@router.post("/upload", response_model=ImageResponse)
async def upload_image(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    metadata: Optional[str] = Form(None),
    db: Session = Depends(get_db)
):
    """
    Загрузка изображения с Android с метаданными
    
    Args:
        file: Файл изображения
        metadata: JSON строка с метаданными (опционально)
        db: Сессия базы данных
        
    Returns:
        ImageResponse: Информация о загруженном изображении
    """
    try:
        # Парсинг метаданных если есть
        parsed_metadata = None
        if metadata:
            try:
                metadata_dict = json.loads(metadata)
                parsed_metadata = ImageMetadata(**metadata_dict)
            except (json.JSONDecodeError, ValueError) as e:
                logger.warning(f"Ошибка парсинга метаданных: {e}")
        
        # Сохранение изображения
        image = await image_service.save_uploaded_image(db, file, parsed_metadata)
        
        # Получаем подписанный URL для доступа Backend2
        signed_url = await s3_service.get_file_url(image.s3_key)
        
        # Публикация задачи в очередь на обработку изображения
        background_tasks.add_task(
            mq_service.publish_image_task,
            {
                "image_id": image.id,
                "s3_key": image.s3_key,
                "s3_url": signed_url,  # Подписанный URL
                "content_type": image.content_type,
                "metadata": parsed_metadata.model_dump(mode='json') if parsed_metadata else None,
            },
        )
        
        return ImageResponse.model_validate(image)
        
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Ошибка при загрузке изображения: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.get("/", response_model=List[ImageResponse])
async def get_images(
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """
    Получение списка изображений
    
    Args:
        skip: Количество записей для пропуска
        limit: Максимальное количество записей
        db: Сессия базы данных
        
    Returns:
        List[ImageResponse]: Список изображений
    """
    try:
        images = image_service.get_images(db, skip, limit)
        return [ImageResponse.model_validate(image) for image in images]
    except Exception as e:
        logger.error(f"Ошибка при получении списка изображений: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.get("/{image_id}", response_model=ImageResponse)
async def get_image(
    image_id: int,
    db: Session = Depends(get_db)
):
    """
    Получение изображения по ID
    
    Args:
        image_id: ID изображения
        db: Сессия базы данных
        
    Returns:
        ImageResponse: Информация об изображении
    """
    try:
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        return ImageResponse.model_validate(image)
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при получении изображения {image_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.post("/{image_id}/reprocess")
async def reprocess_image(
    image_id: int,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db)
):
    """
    Повторная обработка изображения
    
    Args:
        image_id: ID изображения
        background_tasks: Фоновые задачи
        db: Сессия базы данных
        
    Returns:
        dict: Сообщение о запуске обработки
    """
    try:
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        # Запуск обработки в фоне
        background_tasks.add_task(image_service.process_image, db, image_id)
        
        return {"message": f"Обработка изображения {image_id} запущена"}
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при запуске повторной обработки изображения {image_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.get("/{image_id}/download")
async def download_image(
    image_id: int,
    expires_in: int = 3600,
    db: Session = Depends(get_db)
):
    """
    Получение подписанного URL для скачивания изображения из S3
    
    Args:
        image_id: ID изображения
        expires_in: Время действия URL в секундах (по умолчанию 1 час)
        db: Сессия базы данных
        
    Returns:
        dict: Подписанный URL для скачивания
    """
    try:
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        download_url = await s3_service.get_file_url(image.s3_key, expires_in)
        
        return {
            "download_url": download_url,
            "expires_in": expires_in,
            "filename": image.original_filename
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при получении URL для скачивания изображения {image_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.get("/{image_id}/fragments/{fragment_id}/download")
async def download_fragment(
    image_id: int,
    fragment_id: int,
    expires_in: int = 3600,
    db: Session = Depends(get_db)
):
    """
    Получение подписанного URL для скачивания фрагмента изображения из S3
    
    Args:
        image_id: ID изображения
        fragment_id: ID фрагмента
        expires_in: Время действия URL в секундах
        db: Сессия базы данных
        
    Returns:
        dict: Подписанный URL для скачивания фрагмента
    """
    try:
        # Проверяем существование изображения
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        # Находим фрагмент
        fragment = None
        for frag in image.fragments:
            if frag.id == fragment_id:
                fragment = frag
                break
        
        if not fragment:
            raise HTTPException(status_code=404, detail="Фрагмент не найден")
        
        download_url = await s3_service.get_file_url(fragment.s3_key, expires_in)
        
        return {
            "download_url": download_url,
            "expires_in": expires_in,
            "filename": fragment.filename,
            "description": fragment.description_text
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при получении URL для скачивания фрагмента {fragment_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.delete("/{image_id}")
async def delete_image(
    image_id: int,
    db: Session = Depends(get_db)
):
    """
    Удаление изображения и всех его фрагментов из S3 и базы данных
    
    Args:
        image_id: ID изображения
        db: Сессия базы данных
        
    Returns:
        dict: Сообщение об успешном удалении
    """
    try:
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        # Удаляем фрагменты из S3
        for fragment in image.fragments:
            await s3_service.delete_file(fragment.s3_key)
        
        # Удаляем основное изображение из S3
        await s3_service.delete_file(image.s3_key)
        
        # Удаляем из базы данных (фрагменты удалятся автоматически через cascade)
        db.delete(image)
        db.commit()
        
        logger.info(f"Изображение {image_id} и все его фрагменты удалены")
        
        return {"message": f"Изображение {image_id} успешно удалено"}
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при удалении изображения {image_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.post("/internal/save-fragment")
async def save_fragment_internal(
    file: UploadFile = File(...),
    folder: str = Form("fragments")
):
    """
    Внутренний endpoint для сохранения фрагментов изображений в S3
    Используется Backend3 для сохранения обрезанных изображений
    """
    try:
        # Загружаем фрагмент в S3
        s3_key, s3_url = await s3_service.upload_file(file, folder)
        
        return {
            "success": True,
            "s3_key": s3_key,
            "s3_url": s3_url,
            "filename": file.filename
        }
        
    except Exception as e:
        logger.error(f"Ошибка при сохранении фрагмента: {e}")
        raise HTTPException(status_code=500, detail="Ошибка сохранения фрагмента")


@router.get("/{image_id}/objects")
async def get_detected_objects(
    image_id: int,
    db: Session = Depends(get_db)
):
    """
    Получение координат найденных объектов на изображении
    
    Args:
        image_id: ID изображения
        db: Сессия базы данных
        
    Returns:
        dict: Найденные объекты с координатами и ссылками на фрагменты
    """
    try:
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        if not image.detected_objects:
            return {
                "image_id": image_id,
                "detected_objects": [],
                "message": "Объекты не найдены или изображение еще не обработано"
            }
        
        import json
        detected_objects = json.loads(image.detected_objects)
        
        return {
            "image_id": image_id,
            "image_width": image.width,
            "image_height": image.height,
            "detected_objects": detected_objects,
            "total_objects": len(detected_objects)
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при получении объектов изображения {image_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")


@router.get("/{image_id}/fragments")
async def get_image_fragments(
    image_id: int,
    db: Session = Depends(get_db)
):
    """
    Получение ссылок на все обрезанные фрагменты изображения
    
    Args:
        image_id: ID изображения
        db: Сессия базы данных
        
    Returns:
        dict: Список фрагментов с подписанными URL для скачивания
    """
    try:
        image = image_service.get_image_by_id(db, image_id)
        if not image:
            raise HTTPException(status_code=404, detail="Изображение не найдено")
        
        if not image.detected_objects:
            return {
                "image_id": image_id,
                "fragments": [],
                "message": "Фрагменты не найдены"
            }
        
        import json
        detected_objects = json.loads(image.detected_objects)
        
        fragments = []
        for i, obj in enumerate(detected_objects):
            fragment_url = obj.get("fragment_url", "")
            if fragment_url and fragment_url.startswith("http"):
                # Если это S3 URL, создаем подписанный URL
                if "s3" in fragment_url or "minio" in fragment_url:
                    # Извлекаем S3 ключ из URL
                    s3_key = fragment_url.split("/")[-2] + "/" + fragment_url.split("/")[-1]
                    signed_url = await s3_service.get_file_url(s3_key)
                else:
                    signed_url = fragment_url
                
                fragments.append({
                    "index": i,
                    "label": obj.get("label", "объект"),
                    "confidence": obj.get("confidence", 0.0),
                    "bbox": obj.get("bbox", []),
                    "description": obj.get("description", ""),
                    "fragment_url": signed_url
                })
        
        return {
            "image_id": image_id,
            "fragments": fragments,
            "total_fragments": len(fragments)
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Ошибка при получении фрагментов изображения {image_id}: {e}")
        raise HTTPException(status_code=500, detail="Внутренняя ошибка сервера")
