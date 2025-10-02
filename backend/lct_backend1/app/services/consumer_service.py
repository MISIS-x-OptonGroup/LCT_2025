"""
Consumer для обработки результатов из RabbitMQ
"""
import asyncio
import json
import logging
import time
import aio_pika
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.database import SessionLocal
from app.services.image_service import ImageService

logger = logging.getLogger(__name__)


class ConsumerService:
    """Консьюмер для обработки результатов из RabbitMQ"""

    def __init__(self):
        self.amqp_url = settings.rabbitmq_url
        self.queue_descriptions = settings.rabbitmq_queue_image_descriptions
        self.image_service = ImageService()

    async def start_consumer(self):
        """Запускает консьюмер для обработки результатов"""
        connection: aio_pika.abc.AbstractRobustConnection = await aio_pika.connect_robust(self.amqp_url)
        try:
            channel = await connection.channel()
            await channel.set_qos(prefetch_count=10)

            # Создаем очередь для получения описаний от Backend3
            queue = await channel.declare_queue(self.queue_descriptions, durable=True)

            async with queue.iterator() as queue_iter:
                async for message in queue_iter:
                    async with message.process():
                        try:
                            start_time = time.time()
                            payload = json.loads(message.body.decode("utf-8"))
                            image_id = payload.get('image_id')
                            obj_count = len(payload.get('detected_objects', []))
                            
                            logger.info(f"📥 Backend1: Получен результат для image_id={image_id} ({obj_count} объектов)")
                            
                            # Обрабатываем результат в базе данных
                            with SessionLocal() as db:
                                await self.image_service.process_description_result(db, payload)
                            
                            elapsed = time.time() - start_time
                            logger.info(f"✅ Backend1: Результат для image_id={image_id} сохранен за {elapsed:.2f}s")
                                
                        except Exception as e:
                            logger.exception(f"❌ Backend1: Ошибка обработки сообщения: {e}")
        finally:
            await connection.close()

    async def ensure_queues(self):
        """Создает необходимые очереди"""
        connection: aio_pika.abc.AbstractRobustConnection = await aio_pika.connect_robust(self.amqp_url)
        try:
            channel = await connection.channel()
            
            # Создаем все необходимые очереди
            await channel.declare_queue(settings.rabbitmq_queue_image_tasks, durable=True)
            await channel.declare_queue(settings.rabbitmq_queue_image_results, durable=True)
            await channel.declare_queue(settings.rabbitmq_queue_image_descriptions, durable=True)
            
            logger.info("Все очереди RabbitMQ созданы")
        finally:
            await connection.close()
