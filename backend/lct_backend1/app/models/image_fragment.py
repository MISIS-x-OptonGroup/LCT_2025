"""
Модель фрагмента изображения
"""
from sqlalchemy import Column, Integer, String, DateTime, Text, ForeignKey, JSON
from sqlalchemy.orm import relationship
from datetime import datetime

from app.core.database import Base


class ImageFragment(Base):
    """
    Модель для хранения фрагментов изображения и их описаний
    """
    __tablename__ = "image_fragments"
    
    id = Column(Integer, primary_key=True, index=True)
    image_id = Column(Integer, ForeignKey("images.id"), nullable=False)
    filename = Column(String(255), nullable=False)
    content_type = Column(String(100), nullable=False)
    file_size = Column(Integer, nullable=False)
    
    # S3 информация
    s3_key = Column(String(500), nullable=False, index=True)  # Ключ файла в S3
    s3_url = Column(String(1000), nullable=False)  # URL файла в S3
    s3_bucket = Column(String(255), nullable=True)  # Название bucket
    
    # Позиция фрагмента в оригинальном изображении
    position_x = Column(Integer, nullable=True)
    position_y = Column(Integer, nullable=True)
    width = Column(Integer, nullable=True)
    height = Column(Integer, nullable=True)
    
    # Описание от backend3
    description = Column(JSON, nullable=True)  # JSON описание фрагмента
    description_text = Column(Text, nullable=True)  # Текстовое описание
    
    # Статус обработки
    processing_status = Column(String(50), default="created")  # created, sent_to_backend3, described, error
    
    # Временные метки
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Связь с основным изображением
    image = relationship("Image", back_populates="fragments")
    
    def __repr__(self):
        return f"<ImageFragment(id={self.id}, image_id={self.image_id}, filename={self.filename}, s3_key={self.s3_key})>"
