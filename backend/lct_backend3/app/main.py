"""
FastAPI приложение backend3: читает результаты backend2 и публикует текстовое описание
"""
import asyncio
import base64
import io
import os
import json
import logging
import time
from typing import Optional, Dict, List

import aio_pika
import httpx
from aio_pika import Message, DeliveryMode
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from pydantic import BaseModel
from PIL import Image

from .core.config import settings

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Backend 3 - Image Describer", version="1.0.0")

# Endpoint NVIDIA Integrate API
NVIDIA_INTEGRATE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"


class ImageAnalysisRequest(BaseModel):
    image_url: Optional[str] = None
    text_prompt: str = ""
    max_tokens: int = 1024
    temperature: float = 0.9


class ImageAnalysisResponse(BaseModel):
    success: bool
    result: str
    error: Optional[str] = None

def check_confidence_threshold(description: str, threshold: int = 20) -> bool:
    """
    Проверяет overall_confidence в JSON ответе от VLM
    
    Args:
        description: JSON строка с ответом от VLM
        threshold: Минимальный порог уверенности (по умолчанию 20)
        
    Returns:
        bool: True если overall_confidence > threshold, False иначе
    """
    try:
        # Пытаемся распарсить JSON
        data = json.loads(description)
        
        # Ищем overall_confidence в data_quality
        if "data_quality" in data and "overall_confidence" in data["data_quality"]:
            confidence = data["data_quality"]["overall_confidence"]
            return confidence > threshold
        
        # Если поле не найдено, пропускаем фрагмент (считаем что уверенности нет)
        logger.warning("Поле overall_confidence не найдено в ответе VLM")
        return True  # По умолчанию пропускаем, если структура изменилась
        
    except json.JSONDecodeError:
        # Если не удалось распарсить JSON, пропускаем фрагмент
        logger.warning("Не удалось распарсить JSON ответ от VLM")
        return True  # По умолчанию пропускаем, чтобы не терять данные
    except Exception as e:
        logger.error(f"Ошибка при проверке confidence: {e}")
        return True  # По умолчанию пропускаем при ошибке


def get_prompt_text() -> str:
    a = '''СИСТЕМНАЯ ИНСТРУКЦИЯ
Всегда возвращай ВАЛИДНЫЙ JSON строго по схеме ниже.
Никакого текста вне JSON. Ответ должен завершаться закрывающей фигурной скобкой.

РОЛЬ
Ты — дендролог. Анализируешь одно изображение (crop одного дерева или куста).

ЗАДАЧИ
1. Определи тип объекта: tree | bush | unknown.
2. Определи породу (ТОЛЬКО из справочника ниже; иначе "неопределено").
3. Зафиксируй признаки состояния (ствол, крона, кора, грибные тела, болезни, вредители).
4. Определи сезон по кадру (spring | summer | autumn | winter | unknown).
5. Укажи уровень риска (low | medium | high | critical).
6. Все confidence/likelihood — целые 0–100.

СПЕЦРЕЖИМ: Если ты уверен, что на фото по большей части изображено не дерево и не куст, имеенно по большей части и имеенно не дерево и не куст,
ТО:
— data_quality.overall_confidence = 0

СПРАВОЧНИК
Породы деревьев (type=tree): Клён остролистный; Клён ясенелистный; Липа; Берёза; Каштан; Дуб; Ясень; Вяз; Осина; Ива; Сосна; Ель; Лиственница; Туя; Рябина.
Породы кустов (type=bush): Сосна (кустарниковая форма); Можжевельник; Лапчатка кустарниковая (курильский чай); Чубушник; Сирень обыкновенная; Карагана древовидная; Пузыреплодник калинолистный; Спирея; Кизильник; Дерен белый; Лещина; Боярышник; Роза собачья (шиповник); Роза морщинистая.
Болезни/вредители/характеристики: мучнистая роса; чёрная пятнистость клёна (Rhytisma spp.); антракноз; ржавчина; хлороз/дефицит питания; сажистый налёт (вторичный при тлях/щитовках);
листовоминирующие (Cameraria ohridella, др. минёры); канкеры/язвы (Nectria, Cytospora и др.); сокотечение/slime-flux; гнили трутовиков (Fomes fomentarius, Laetiporus sulphureus и др.);
Armillaria; Phytophthora; древоточцы; комлевая гниль; стволовая гниль; сухобочина; дупло; механические повреждения; отслоение коры; сухостой; сухие ветви; рак; пенёк/остолоп; трещины; вывал корневой системы; суховершинность.

ПРАВИЛА
- species.label_ru всегда только из списка выше или "неопределено".
- diseases[].name_ru и pests[].name_ru только из списка выше.
- Если признак не различим → present=false и confidence=10–20.
- dry_branches_pct = 0 / 25 / 50 / 75.
- evidence/locations: коротко по факту («плодовое тело трутовика у основания», «глубокая трещина»).
- risk.level выбирай из факторов (наклон, плодовые тела, трещины, сухие ветви и т.п.).
— Всё описание должно быть на русском языке. Все характеристики должны быть на русском языке. Все ответы должны быть на русском языке.

СХЕМА JSON (заполняй ровно по образцу)
{
  "scene": {
    "season_inferred": "spring|summer|autumn|winter|unknown",
    "note": ""
  },
  "object": {
    "type": "tree|bush|unknown",
    "species": { "label_ru": "...", "confidence": 0 },
    "condition": {
      "trunk_decay":     { "present": false, "evidence": [], "confidence": 0 },
      "cavities":        { "present": false, "locations": [], "confidence": 0 },
      "cracks":          { "present": false, "locations": [], "confidence": 0 },
      "bark_detachment": { "present": false, "locations": [], "confidence": 0 },
      "trunk_damage":    { "present": false, "evidence": [], "confidence": 0 },
      "crown_damage":    { "present": false, "evidence": [], "confidence": 0 },
      "fruiting_bodies": { "present": false, "evidence": [], "confidence": 0 },
      "root_damage": { "present": false, "evidence": [], "confidence": 0 },
      "root_collar_decay": { "present": false, "evidence": [], "confidence": 0 },
      "tree_status": "alive|dying|dead|unknown",
      "leaning": { "present": false, "angle": 0, "confidence": 0 },
      "diseases": [
        {
          "name_ru": "...",
          "type": "fungal|bacterial|viral|physiological",
          "likelihood": 0,
          "evidence": [],
          "severity": "low|medium|high"
        }
      ],
      "pests": [
        { "name_ru": "...", "likelihood": 0, "evidence": [] }
      ],
      "dry_branches_pct": 0,
      "other": []
    },
    "risk": {
      "level": "low|medium|high|critical",
      "drivers": [],
      "imminent_failure_risk": false
    }
  },
  "data_quality": {
    "issues": [],
    "overall_confidence": 0
  }
}

(В финальном ответе выводи ТОЛЬКО JSON по схеме; без примеров и дополнительного текста.)'''
    return a

async def start_consumer() -> None:
    connection: aio_pika.abc.AbstractRobustConnection = await aio_pika.connect_robust(settings.rabbitmq_url)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)

    queue = await channel.declare_queue(settings.rabbitmq_queue_image_results, durable=True)

    async with queue.iterator() as queue_iter:
        async for message in queue_iter:
            async with message.process():
                try:
                    start_time = time.time()
                    payload = json.loads(message.body.decode("utf-8"))
                    image_id = payload.get("image_id")
                    image_url = payload.get("image_url")
                    detected_objects = payload.get("detected_objects", [])

                    logger.info(f"📥 Backend3: Начата обработка image_id={image_id}, объектов: {len(detected_objects)}")

                    if not image_url:
                        logger.error(f"❌ Нет URL изображения для image_id={image_id}")
                        continue

                    if not detected_objects:
                        # Если объекты не найдены, анализируем целое изображение и возвращаем как единичный объект
                        result_text = await call_nvidia_api_by_url(image_url)
                        
                        # Проверяем overall_confidence
                        should_include = check_confidence_threshold(result_text)
                        
                        if should_include:
                            result = {
                                "image_id": image_id,
                                "detected_objects": [
                                    {
                                        "bbox": [],
                                        "label": "изображение целиком",
                                        "confidence": 1.0,
                                        "fragment_url": image_url,
                                        "description": result_text,
                                    }
                                ],
                            }
                        else:
                            # Если уверенность низкая, возвращаем пустой список объектов
                            logger.info(f"Изображение целиком для image_id={image_id} отфильтровано по low confidence (<=20%)")
                            result = {
                                "image_id": image_id,
                                "detected_objects": [],
                            }
                    else:
                        # ОПТИМИЗАЦИЯ: Скачиваем изображение ОДИН РАЗ для всех фрагментов
                        download_start = time.time()
                        logger.info(f"   📥 Скачивание изображения из S3...")
                        
                        try:
                            async with httpx.AsyncClient(timeout=60.0) as client:
                                resp = await client.get(image_url)
                                resp.raise_for_status()
                                image_data = resp.content
                                img = Image.open(io.BytesIO(image_data)).convert("RGB")
                                
                            download_time = time.time() - download_start
                            size_mb = len(image_data) / (1024 * 1024)
                            logger.info(f"   ✓ Изображение загружено: {size_mb:.2f} MB за {download_time:.2f}s")
                        except Exception as e:
                            logger.error(f"❌ Ошибка загрузки изображения: {e}")
                            continue
                        
                        # ПАРАЛЛЕЛЬНАЯ обработка фрагментов через asyncio.gather()
                        logger.info(f"   🚀 Начинаем ПАРАЛЛЕЛЬНУЮ обработку {len(detected_objects)} фрагментов...")
                        
                        # Создаём ОБЩИЙ httpx клиент для всех параллельных запросов
                        async with httpx.AsyncClient(timeout=120.0, limits=httpx.Limits(max_connections=20, max_keepalive_connections=20)) as shared_client:
                            
                            async def process_single_fragment(i: int, obj: dict, img_pil: Image.Image, client: httpx.AsyncClient) -> Optional[dict]:
                                """Обрабатывает один фрагмент: обрезка, анализ VLM, сохранение"""
                                try:
                                    fragment_start = time.time()
                                    bbox = obj.get("bbox", [])  # [x1, y1, x2, y2]
                                    label = obj.get("label", "объект")
                                    confidence = obj.get("confidence", 0.0)
                                    
                                    logger.info(f"      🔍 [{i+1}] Обработка фрагмента (label={label}, conf={confidence:.2f})")
                                    
                                    if len(bbox) != 4:
                                        return None
                                    
                                    # Обрезаем изображение по координатам (БЕЗ повторной загрузки!)
                                    x1, y1, x2, y2 = bbox
                                    cropped_img = img_pil.crop((int(x1), int(y1), int(x2), int(y2)))
                                    
                                    # Конвертируем в base64
                                    buffer = io.BytesIO()
                                    cropped_img.save(buffer, format='JPEG', quality=95)
                                    cropped_image_b64 = base64.b64encode(buffer.getvalue()).decode()
                                    
                                    crop_time = time.time() - fragment_start
                                    logger.info(f"         ✓ [{i+1}] Обрезка: {crop_time:.2f}s")
                                    
                                    # Анализируем обрезанный фрагмент через NVIDIA API (РЕАЛЬНО параллельно с общим клиентом!)
                                    api_start = time.time()
                                    fragment_description = await call_nvidia_api_with_base64_shared(cropped_image_b64, client)
                                    api_time = time.time() - api_start
                                    logger.info(f"         ✓ [{i+1}] NVIDIA VLM API: {api_time:.2f}s")
                                    
                                    # Проверяем overall_confidence
                                    should_include = check_confidence_threshold(fragment_description)
                                    
                                    if not should_include:
                                        logger.info(f"         ⚠️ [{i+1}] Фильтр: low confidence (<=20%)")
                                        return None
                                    
                                    # Сохраняем обрезанное изображение в S3 (параллельно!)
                                    save_start = time.time()
                                    fragment_url = await save_cropped_image_shared(cropped_image_b64, image_id, i, label, client)
                                    save_time = time.time() - save_start
                                    logger.info(f"         ✓ [{i+1}] S3 save: {save_time:.2f}s")
                                    
                                    fragment_time = time.time() - fragment_start
                                    logger.info(f"      ✅ [{i+1}] ГОТОВО за {fragment_time:.2f}s")
                                    
                                    return {
                                        "bbox": bbox,
                                        "label": label,
                                        "confidence": confidence,
                                        "fragment_url": fragment_url,
                                        "description": fragment_description
                                    }
                                    
                                except Exception as e:
                                    logger.error(f"         ❌ [{i+1}] Ошибка: {e}")
                                    return None
                            
                            # Запускаем ВСЕ фрагменты РЕАЛЬНО ПАРАЛЛЕЛЬНО с общим HTTP клиентом
                            parallel_start = time.time()
                            tasks = [
                                process_single_fragment(i, obj, img, shared_client) 
                                for i, obj in enumerate(detected_objects)
                            ]
                            results = await asyncio.gather(*tasks, return_exceptions=True)
                            parallel_time = time.time() - parallel_start
                        
                        # Фильтруем успешные результаты
                        processed_objects = [r for r in results if r is not None and not isinstance(r, Exception)]
                        
                        # Возвращаем список объектов без комбинированного текста
                        result = {
                            "image_id": image_id,
                            "detected_objects": processed_objects
                        }
                        
                        logger.info(f"   ⚡ Параллельная обработка: {len(processed_objects)}/{len(detected_objects)} за {parallel_time:.2f}s")

                    await channel.default_exchange.publish(
                        Message(
                            json.dumps(result).encode("utf-8"),
                            content_type="application/json",
                            delivery_mode=DeliveryMode.PERSISTENT,
                        ),
                        routing_key=settings.rabbitmq_queue_image_descriptions,
                    )
                    
                    total_time = time.time() - start_time
                    obj_count = len(result.get("detected_objects", []))
                    logger.info(f"✅ Backend3: Обработка image_id={image_id} завершена за {total_time:.2f}s (возвращено {obj_count} объектов)")
                    
                except Exception as e:
                    logger.exception(f"❌ Backend3: Ошибка обработки image_id={image_id}: %s", e)


@app.on_event("startup")
async def on_startup():
    asyncio.create_task(start_consumer())


@app.get("/health")
async def health():
    return {"status": "ok"}


async def crop_image_from_url(image_url: str, bbox: list) -> Optional[str]:
    """
    Обрезает изображение по координатам и возвращает base64
    
    Args:
        image_url: URL изображения
        bbox: [x1, y1, x2, y2] координаты для обрезки
        
    Returns:
        Optional[str]: base64 обрезанного изображения или None при ошибке
    """
    try:
        # Загружаем изображение
        async with httpx.AsyncClient(timeout=90.0) as client:
            resp = await client.get(image_url)
            resp.raise_for_status()
            
            # Открываем изображение
            img = Image.open(io.BytesIO(resp.content)).convert("RGB")
            
            # Обрезаем по координатам [x1, y1, x2, y2]
            x1, y1, x2, y2 = bbox
            cropped_img = img.crop((int(x1), int(y1), int(x2), int(y2)))
            
            # Конвертируем в base64
            buffer = io.BytesIO()
            cropped_img.save(buffer, format='JPEG', quality=95)
            img_b64 = base64.b64encode(buffer.getvalue()).decode()
            
            return img_b64
            
    except Exception as e:
        logger.error(f"Ошибка при обрезке изображения: {e}")
        return None


async def call_nvidia_api_with_base64_shared(image_b64: str, client: httpx.AsyncClient) -> str:
    """
    Вызывает NVIDIA API с base64 изображением используя общий HTTP клиент
    
    Args:
        image_b64: base64 изображения
        client: Общий httpx.AsyncClient для параллельных запросов
        
    Returns:
        str: Описание изображения
    """
    try:
        prompt_text = get_prompt_text()
        headers = {
            "Authorization": f"Bearer {settings.nvidia_api_key}" if settings.nvidia_api_key else "",
            "Accept": "application/json",
        }
        payload = {
            "model": "meta/llama-3.2-90b-vision-instruct",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt_text},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}},
                    ],
                }
            ],
            "max_tokens": 512,
            "temperature": 1.0,
            "top_p": 1.0,
            "frequency_penalty": 0.0,
            "presence_penalty": 0.0,
            "stream": False,
        }
        
        resp = await client.post(NVIDIA_INTEGRATE_URL, headers=headers, json=payload)
        resp.raise_for_status()
        data = resp.json()
        if "choices" in data and data["choices"]:
            return data["choices"][0]["message"]["content"]
        return "Не удалось получить описание"
            
    except Exception as e:
        logger.error(f"Ошибка при вызове NVIDIA API: {e}")
        return f"Ошибка анализа: {str(e)}"


async def call_nvidia_api_with_base64(image_b64: str) -> str:
    """
    Вызывает NVIDIA API с base64 изображением (legacy версия для обратной совместимости)
    
    Args:
        image_b64: base64 изображения
        
    Returns:
        str: Описание изображения
    """
    try:
        prompt_text = get_prompt_text()
        headers = {
            "Authorization": f"Bearer {settings.nvidia_api_key}" if settings.nvidia_api_key else "",
            "Accept": "application/json",
        }
        payload = {
            "model": "meta/llama-3.2-90b-vision-instruct",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt_text},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}},
                    ],
                }
            ],
            "max_tokens": 512,
            "temperature": 1.0,
            "top_p": 1.0,
            "frequency_penalty": 0.0,
            "presence_penalty": 0.0,
            "stream": False,
        }
        
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(NVIDIA_INTEGRATE_URL, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
            if "choices" in data and data["choices"]:
                return data["choices"][0]["message"]["content"]
            return "Не удалось получить описание"
            
    except Exception as e:
        logger.error(f"Ошибка при вызове NVIDIA API: {e}")
        return f"Ошибка анализа: {str(e)}"


async def save_cropped_image_shared(image_b64: str, image_id: int, fragment_index: int, label: str, client: httpx.AsyncClient) -> str:
    """
    Сохраняет обрезанное изображение в S3 (через Backend1 API) используя общий HTTP клиент
    """
    try:
        import base64
        
        # Декодируем base64 в байты
        image_bytes = base64.b64decode(image_b64)
        
        # Создаем имя файла
        filename = f"fragment_{image_id}_{fragment_index}_{label}.jpg"
        
        # Отправляем в Backend1 для сохранения в S3
        files = {
            "file": (filename, image_bytes, "image/jpeg")
        }
        data = {
            "folder": "fragments"
        }
        
        # Используем внутренний endpoint Backend1 для сохранения
        response = await client.post(
            f"{settings.backend1_url}/api/v1/images/internal/save-fragment",
            files=files,
            data=data
        )
        
        if response.status_code == 200:
            result = response.json()
            return result.get("s3_url", filename)
        else:
            logger.warning(f"Не удалось сохранить фрагмент: {response.status_code}")
            return filename
                
    except Exception as e:
        logger.error(f"Ошибка при сохранении фрагмента: {e}")
        return f"fragment_{image_id}_{fragment_index}_{label}.jpg"


async def save_cropped_image(image_b64: str, image_id: int, fragment_index: int, label: str) -> str:
    """
    Сохраняет обрезанное изображение в S3 (через Backend1 API) (legacy версия)
    """
    try:
        import base64
        
        # Декодируем base64 в байты
        image_bytes = base64.b64decode(image_b64)
        
        # Создаем имя файла
        filename = f"fragment_{image_id}_{fragment_index}_{label}.jpg"
        
        # Отправляем в Backend1 для сохранения в S3
        async with httpx.AsyncClient(timeout=30.0) as client:
            files = {
                "file": (filename, image_bytes, "image/jpeg")
            }
            data = {
                "folder": "fragments"
            }
            
            # Используем внутренний endpoint Backend1 для сохранения
            response = await client.post(
                f"{settings.backend1_url}/api/v1/images/internal/save-fragment",
                files=files,
                data=data
            )
            
            if response.status_code == 200:
                result = response.json()
                return result.get("s3_url", filename)
            else:
                logger.warning(f"Не удалось сохранить фрагмент: {response.status_code}")
                return filename
                
    except Exception as e:
        logger.error(f"Ошибка при сохранении фрагмента: {e}")
        return f"fragment_{image_id}_{fragment_index}_{label}.jpg"


async def call_nvidia_api_by_url(image_url: str) -> str:
    # Читаем промпт через хелпер
    prompt_text = get_prompt_text()
    headers = {
        "Authorization": f"Bearer {settings.nvidia_api_key}" if settings.nvidia_api_key else "",
        "Accept": "application/json",
    }
    payload = {
        "model": "meta/llama-3.2-90b-vision-instruct",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt_text},
                    {"type": "image_url", "image_url": {"url": image_url}},
                ],
            }
        ],
        "max_tokens": 1024,
        "temperature": 1.0,
        "top_p": 1.0,
        "frequency_penalty": 0.0,
        "presence_penalty": 0.0,
        "stream": False,
    }
    async with httpx.AsyncClient(timeout=120.0) as client:
        resp = await client.post(NVIDIA_INTEGRATE_URL, headers=headers, json=payload)
        resp.raise_for_status()
        data = resp.json()
        if "choices" in data and data["choices"]:
            return data["choices"][0]["message"]["content"]
        return ""


@app.get("/")
async def root():
    return {
        "message": "Image describer API",
        "version": "1.0.0",
        "endpoints": {
            "analyze_image_url": "/analyze-image-url",
            "analyze_image_upload": "/analyze-image-upload",
            "health": "/health",
        },
    }


@app.post("/analyze-image-url", response_model=ImageAnalysisResponse)
async def analyze_image_by_url(request: ImageAnalysisRequest):
    if not request.image_url:
        raise HTTPException(status_code=400, detail="URL изображения обязателен")
    try:
        text = await call_nvidia_api_by_url(request.image_url)
        return ImageAnalysisResponse(success=True, result=text)
    except httpx.HTTPError as e:
        return ImageAnalysisResponse(success=False, result="", error=f"Ошибка при обращении к NVIDIA API: {str(e)}")
    except Exception as e:
        return ImageAnalysisResponse(success=False, result="", error=f"Внутренняя ошибка сервера: {str(e)}")


@app.post("/analyze-image-upload", response_model=ImageAnalysisResponse)
async def analyze_uploaded_image(
    file: UploadFile = File(...),
    text_prompt: str = Form("Опиши что ты видишь на этом изображении, отвечай на русском языке"),
    max_tokens: int = Form(512),
    temperature: float = Form(1.0),
):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Файл должен быть изображением")
    try:
        file_content = await file.read()
        image_b64 = base64.b64encode(file_content).decode()
        # Всегда используем промпт из файла, игнорируя пришедший text_prompt
        prompt_text = get_prompt_text()

        headers = {
            "Authorization": f"Bearer {settings.nvidia_api_key}" if settings.nvidia_api_key else "",
            "Accept": "application/json",
        }
        payload = {
            "model": "meta/llama-3.2-90b-vision-instruct",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt_text},
                        {"type": "image_url", "image_url": {"url": f"data:{file.content_type};base64,{image_b64}"}},
                    ],
                }
            ],
            "max_tokens": max_tokens,
            "temperature": temperature,
            "top_p": 1.0,
            "frequency_penalty": 0.0,
            "presence_penalty": 0.0,
            "stream": False,
        }
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.post(NVIDIA_INTEGRATE_URL, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
            if "choices" in data and data["choices"]:
                return ImageAnalysisResponse(success=True, result=data["choices"][0]["message"]["content"])
            return ImageAnalysisResponse(success=False, result="", error="Не удалось получить результат анализа")
    except httpx.HTTPError as e:
        return ImageAnalysisResponse(success=False, result="", error=f"Ошибка при обращении к NVIDIA API: {str(e)}")
    except Exception as e:
        return ImageAnalysisResponse(success=False, result="", error=f"Внутренняя ошибка сервера: {str(e)}")

