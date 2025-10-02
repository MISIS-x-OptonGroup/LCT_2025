from sqlalchemy import Column, Integer, String, DateTime, Text, LargeBinary
from sqlalchemy.orm import relationship
from datetime import datetime

from app.core.database import Base


class Image(Base):
    """
    Модель для хранения основного изображения и его метаданных
    """
    __tablename__ = "images"
    
    id = Column(Integer, primary_key=True, index=True)
    filename = Column(String(255), nullable=False)  # Имя файла в S3
    original_filename = Column(String(255), nullable=False)
    content_type = Column(String(100), nullable=False)
    file_size = Column(Integer, nullable=False)
    
    # S3 информация
    s3_key = Column(String(500), nullable=False, index=True)  # Ключ файла в S3
    s3_url = Column(String(1000), nullable=False)  # URL файла в S3
    s3_bucket = Column(String(255), nullable=True)  # Название bucket (для мультибакет конфигураций)
    
    # Метаданные изображения
    taken_at = Column(DateTime, nullable=True)  # Когда сделана фотка
    location = Column(String(500), nullable=True)  # Где сделана фотка
    author = Column(String(255), nullable=True)  # Кто сделал фотку
    
    # Техническая информация
    width = Column(Integer, nullable=True)
    height = Column(Integer, nullable=True)
    
    # Временные метки
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Статус обработки и результаты
    processing_status = Column(String(50), default="uploaded")  # uploaded, processing, completed, error
    description_text = Column(Text, nullable=True)  # Текстовое описание от Backend3
    detected_objects = Column(Text, nullable=True)  # JSON с координатами найденных объектов
    
    # Связь с фрагментами
    fragments = relationship("ImageFragment", back_populates="image", cascade="all, delete-orphan")
    
    def __repr__(self):
        return f"<Image(id={self.id}, filename={self.filename}, s3_key={self.s3_key})>"
