"""
Схемы для изображений
"""
from pydantic import BaseModel, Field, field_validator
from datetime import datetime
from typing import Optional, List, Any
import json
from .image_fragment import ImageFragmentResponse


class ImageMetadata(BaseModel):
    """Метаданные изображения"""
    taken_at: Optional[datetime] = Field(None, description="Время создания фотографии")
    location: Optional[str] = Field(None, description="Местоположение съемки")
    author: Optional[str] = Field(None, description="Автор фотографии")


class ImageCreate(BaseModel):
    """Схема для создания изображения"""
    metadata: Optional[ImageMetadata] = None


class ImageResponse(BaseModel):
    """Схема ответа с информацией об изображении"""
    id: int
    filename: str
    original_filename: str
    content_type: str
    file_size: int
    s3_key: str
    s3_url: str
    s3_bucket: Optional[str] = None
    width: Optional[int] = None
    height: Optional[int] = None
    taken_at: Optional[datetime] = None
    location: Optional[str] = None
    author: Optional[str] = None
    processing_status: str
    description_text: Optional[str] = None
    detected_objects: Optional[List[dict]] = None  # Список объектов с координатами
    created_at: datetime
    updated_at: datetime
    fragments: List[ImageFragmentResponse] = []
    
    @field_validator('detected_objects', mode='before')
    @classmethod
    def parse_detected_objects(cls, v: Any) -> Optional[List[dict]]:
        """Парсит detected_objects из JSON строки в список объектов"""
        if v is None or v == "":
            return None
        
        # Если уже список, возвращаем как есть
        if isinstance(v, list):
            return v
        
        # Если строка, парсим JSON
        if isinstance(v, str):
            try:
                objects = json.loads(v)
                # Парсим description внутри каждого объекта
                for obj in objects:
                    if 'description' in obj and isinstance(obj['description'], str):
                        try:
                            obj['description'] = json.loads(obj['description'])
                        except (json.JSONDecodeError, TypeError):
                            # Если не удалось распарсить, оставляем как строку
                            pass
                return objects
            except (json.JSONDecodeError, TypeError):
                return None
        
        return None
    
    class Config:
        from_attributes = True
