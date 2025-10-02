"""
Сервис для работы с RabbitMQ через aio-pika
"""
import json
import logging
import aio_pika
from aio_pika import Message, DeliveryMode
from datetime import datetime

from app.core.config import settings

logger = logging.getLogger(__name__)


def json_serializer(obj):
    """Сериализатор для datetime объектов в JSON"""
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Type {type(obj)} not serializable")


class MQService:
    """Паблишер задач и результатов в RabbitMQ"""

    def __init__(self):
        self.amqp_url = settings.rabbitmq_url
        self.queue_tasks = settings.rabbitmq_queue_image_tasks
        self.queue_results = settings.rabbitmq_queue_image_results

    async def _ensure_queue(self, channel: aio_pika.abc.AbstractChannel, name: str):
        return await channel.declare_queue(name, durable=True)

    async def publish_image_task(self, payload: dict) -> None:
        """
        Публикует задачу обработки изображения в очередь image_tasks
        payload ожидается как сериализуемый в JSON словарь
        """
        connection: aio_pika.abc.AbstractRobustConnection = await aio_pika.connect_robust(self.amqp_url)
        try:
            channel = await connection.channel()
            await channel.set_qos(prefetch_count=10)

            await self._ensure_queue(channel, self.queue_tasks)

            body = json.dumps(payload, default=json_serializer).encode("utf-8")
            message = Message(
                body=body,
                content_type="application/json",
                delivery_mode=DeliveryMode.PERSISTENT,
            )

            await channel.default_exchange.publish(message, routing_key=self.queue_tasks)
            logger.info("Сообщение задачи отправлено в очередь %s", self.queue_tasks)
        finally:
            await connection.close()

    async def publish_image_result(self, payload: dict) -> None:
        connection: aio_pika.abc.AbstractRobustConnection = await aio_pika.connect_robust(self.amqp_url)
        try:
            channel = await connection.channel()
            await channel.set_qos(prefetch_count=10)

            await self._ensure_queue(channel, self.queue_results)

            body = json.dumps(payload, default=json_serializer).encode("utf-8")
            message = Message(
                body=body,
                content_type="application/json",
                delivery_mode=DeliveryMode.PERSISTENT,
            )

            await channel.default_exchange.publish(message, routing_key=self.queue_results)
            logger.info("Сообщение результата отправлено в очередь %s", self.queue_results)
        finally:
            await connection.close()


