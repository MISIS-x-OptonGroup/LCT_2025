"""
Схемы для фрагментов изображений
"""
from pydantic import BaseModel
from datetime import datetime
from typing import Optional, Dict, Any


class ImageFragmentResponse(BaseModel):
    """Схема ответа с информацией о фрагменте изображения"""
    id: int
    image_id: int
    filename: str
    content_type: str
    file_size: int
    s3_key: str
    s3_url: str
    s3_bucket: Optional[str] = None
    position_x: Optional[int] = None
    position_y: Optional[int] = None
    width: Optional[int] = None
    height: Optional[int] = None
    description: Optional[Dict[str, Any]] = None
    description_text: Optional[str] = None
    processing_status: str
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True
