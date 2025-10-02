"""
FastAPI приложение backend_2 с консьюмером RabbitMQ
"""
import asyncio
import io
import json
import logging
import os
import time
from typing import List

import aio_pika
from aio_pika import Message, DeliveryMode
from fastapi import FastAPI
import httpx
import numpy as np
from PIL import Image

from .core.config import settings
from .ML.ImageSegmentatator import ImageSegmentator

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Backend 2 - Image Processor", version="1.0.0")
segmentator = ImageSegmentator(
    _box_thr=0.10080074890084499,
    _text_thr=0.2693266367707789,
    _iou_thr=0.8527497557418572,
    _beta=0.9457256509335716,
    _alpha_all_classes=0.7412089719658128,
)

# Флаг готовности моделей
models_warmed_up = False


async def warmup_models() -> None:
    """
    Прогревает ML модели при старте сервиса
    Выполняет dummy inference для инициализации CUDA и загрузки весов в память GPU
    """
    global models_warmed_up
    
    if models_warmed_up:
        logger.info("Модели уже прогреты, пропускаем")
        return
    
    try:
        logger.info("🔥 Начинаем прогрев моделей (GroundingDINO + SAM2)...")
        start_time = time.time()
        
        # Создаем dummy изображение для прогрева
        dummy_image = np.zeros((640, 640, 3), dtype=np.uint8)
        
        # Выполняем dummy inference
        masks, boxes, labels, scores = segmentator.predict_with_array(dummy_image)
        
        elapsed = time.time() - start_time
        device = segmentator.device
        
        logger.info(f"✅ Модели прогреты за {elapsed:.2f}s на устройстве: {device}")
        logger.info(f"   - Найдено объектов: {len(boxes)} (ожидается 0 для пустого изображения)")
        
        models_warmed_up = True
        
    except Exception as e:
        logger.error(f"❌ Ошибка при прогреве моделей: {e}")
        logger.warning("⚠️ Продолжаем работу без прогрева, первая обработка будет медленной")


async def start_consumer() -> None:
    connection: aio_pika.abc.AbstractRobustConnection = await aio_pika.connect_robust(settings.rabbitmq_url)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)

    queue = await channel.declare_queue(settings.rabbitmq_queue_image_tasks, durable=True)

    async with queue.iterator() as queue_iter:
        async for message in queue_iter:
            async with message.process():
                try:
                    start_time = time.time()
                    payload = json.loads(message.body.decode("utf-8"))
                    image_id = payload.get("image_id")
                    image_path = payload.get("image_path")
                    s3_url = payload.get("s3_url")

                    logger.info(f"📥 Начата обработка image_id={image_id}")

                    # Получаем изображение либо по локальному пути, либо по URL
                    download_start = time.time()
                    if image_path and os.path.exists(image_path):
                        img = Image.open(image_path).convert("RGB")
                        logger.info(f"   ✓ Загружено из локального пути за {time.time() - download_start:.2f}s")
                    elif s3_url:
                        async with httpx.AsyncClient(timeout=60.0) as client:
                            resp = await client.get(s3_url)
                            resp.raise_for_status()
                            img = Image.open(io.BytesIO(resp.content)).convert("RGB")
                            download_time = time.time() - download_start
                            size_mb = len(resp.content) / (1024 * 1024)
                            logger.info(f"   ✓ Загружено из S3: {size_mb:.2f} MB за {download_time:.2f}s")
                    else:
                        logger.error("❌ Нет ни локального пути, ни URL для изображения; пропускаю сообщение")
                        continue

                    # Сегментация изображения
                    image_rgb = np.array(img, dtype=np.uint8)
                    logger.info(f"   🖼️ Размер изображения: {img.size[0]}x{img.size[1]}")
                    
                    segment_start = time.time()
                    masks, boxes, labels, scores = segmentator.predict_with_array(image_rgb)
                    segment_time = time.time() - segment_start
                    logger.info(f"   ✓ Сегментация завершена за {segment_time:.2f}s, найдено объектов: {len(boxes)}")

                    # Формируем результат с координатами для Backend3
                    result = {
                        "image_id": image_id,
                        "image_url": s3_url,  # URL исходного изображения
                        "image_width": img.size[0],  # Ширина исходного изображения
                        "image_height": img.size[1],  # Высота исходного изображения
                        "detected_objects": [
                            {
                                "bbox": box.tolist() if hasattr(box, 'tolist') else list(box),  # [x1, y1, x2, y2] координаты для обрезки
                                "label": label,
                                "confidence": float(score)
                            } for box, label, score in zip(boxes, labels, scores)
                        ],
                    }

                    # Сохраняем detected_objects в JSON-файл (output.json)
                    try:
                        qq = result.get("detected_objects", [])
                        # Если у объектов есть строковое поле description, пробуем распарсить как JSON
                        for obj in qq:
                            desc = obj.get("description")
                            if isinstance(desc, str):
                                s = desc.replace("\n", "").replace("  ", "")
                                try:
                                    obj["description"] = json.loads(s)
                                except Exception:
                                    pass
                        with open("output.json", "w", encoding="utf-8") as f:
                            json.dump(qq, f, ensure_ascii=False)
                    except Exception as e:
                        logger.warning("Не удалось сохранить output.json: %s", e)

                    # Публикация результата в очередь результатов
                    result_body = json.dumps(result).encode("utf-8")
                    await channel.default_exchange.publish(
                        Message(
                            result_body,
                            content_type="application/json",
                            delivery_mode=DeliveryMode.PERSISTENT,
                        ),
                        routing_key=settings.rabbitmq_queue_image_results,
                    )
                    
                    total_time = time.time() - start_time
                    logger.info(f"✅ Обработка image_id={image_id} завершена за {total_time:.2f}s (найдено {len(boxes)} объектов)")
                    
                except Exception as e:
                    logger.exception(f"❌ Ошибка обработки сообщения image_id={image_id}: %s", e)


@app.on_event("startup")
async def on_startup():
    # Прогреваем модели перед началом обработки
    await warmup_models()
    # Запускаем consumer в фоновом режиме
    asyncio.create_task(start_consumer())


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "models_warmed_up": models_warmed_up,
        "device": segmentator.device,
        "cuda_available": segmentator.device == "cuda"
    }


