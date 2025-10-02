"""
Главный файл FastAPI приложения
"""
import asyncio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging

from app.core.config import settings
from app.core.database import create_tables
from app.api.images import router as images_router
from app.services.consumer_service import ConsumerService

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)

logger = logging.getLogger(__name__)

# Создание таблиц в базе данных
create_tables()

# Создание FastAPI приложения
app = FastAPI(
    title="LCT Backend1 - Image Processing Service",
    description="Сервис для обработки изображений с интеграцией внешних backend'ов",
    version="1.0.0",
    debug=settings.debug
)

# Настройка CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # В продакшене нужно указать конкретные домены
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Подключение роутеров
app.include_router(images_router, prefix="/api/v1/images", tags=["images"])

# Инициализация консьюмера
consumer_service = ConsumerService()


@app.on_event("startup")
async def startup_event():
    """Запуск консьюмера при старте приложения"""
    try:
        # Создаем очереди
        await consumer_service.ensure_queues()
        # Запускаем консьюмер в фоне
        asyncio.create_task(consumer_service.start_consumer())
        logger.info("Consumer успешно запущен")
    except Exception as e:
        logger.error(f"Ошибка запуска consumer: {e}")
        # Продолжаем работу без consumer'а


@app.get("/")
async def root():
    """
    Корневой endpoint для проверки работы сервиса
    """
    return {
        "message": "LCT Backend1 - Image Processing Service",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health")
async def health_check():
    """
    Endpoint для проверки здоровья сервиса
    """
    return {
        "status": "healthy",
        "database": "connected"  # Можно добавить реальную проверку БД
    }


if __name__ == "__main__":
    import uvicorn
    
    logger.info(f"Запуск сервера на {settings.host}:{settings.port}")
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )
