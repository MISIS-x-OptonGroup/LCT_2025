import os
import base64
from typing import Optional
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import uvicorn
from dotenv import load_dotenv
import requests
try:
    from .agent_graph import run_agent  # запуск как пакет: python -m agent.main
except ImportError:
    try:
        from agent.agent_graph import run_agent  # запуск из корня репозитория: python -m agent.main
    except ImportError:
        # запуск из папки agent: python main.py
        from agent_graph import run_agent

# Загружаем переменные окружения
load_dotenv()

app = FastAPI(
    title="NVIDIA AI Image Analysis API",
    description="API для анализа изображений с использованием NVIDIA AI модели",
    version="1.0.0"
)

# Модели данных
class ImageAnalysisRequest(BaseModel):
    image_url: Optional[str] = None
    text_prompt: str = "Опиши что ты видишь на этом изображении, отвечай на русском языке"
    max_tokens: int = 512
    temperature: float = 1.0

class ImageAnalysisResponse(BaseModel):
    success: bool
    result: str
    error: Optional[str] = None

# Ответ агента
class AgentAnalysisResponse(BaseModel):
    success: bool
    result_json: str
    logs: list[str]
    error: Optional[str] = None

# Конфигурация
NVIDIA_API_KEY = os.getenv("NVIDIA_API_KEY", "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")
INVOKE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"

@app.get("/")
async def root():
    """Главная страница API"""
    return {
        "message": "NVIDIA AI Image Analysis API",
        "version": "1.0.0",
        "endpoints": {
            "analyze_image_url": "/analyze-image-url",
            "analyze_image_upload": "/analyze-image-upload",
            "agent_analyze_url": "/agent-analyze-url",
            "agent_analyze_upload": "/agent-analyze-upload",
            "health": "/health"
        }
    }

@app.get("/health")
async def health_check():
    """Проверка здоровья сервиса"""
    return {"status": "healthy", "service": "nvidia-ai-api"}

@app.post("/analyze-image-url", response_model=ImageAnalysisResponse)
async def analyze_image_by_url(request: ImageAnalysisRequest):
    """Анализ изображения по URL"""
    
    if not request.image_url:
        raise HTTPException(status_code=400, detail="URL изображения обязателен")
    
    try:
        headers = {
            "Authorization": f"Bearer {NVIDIA_API_KEY}",
            "Accept": "application/json"
        }

        payload = {
            "model": "meta/llama-3.2-90b-vision-instruct",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": request.text_prompt},
                        {"type": "image_url", "image_url": {"url": request.image_url}}
                    ]
                }
            ],
            "max_tokens": request.max_tokens,
            "temperature": request.temperature,
            "top_p": 1.00,
            "frequency_penalty": 0.00,
            "presence_penalty": 0.00,
            "stream": False
        }

        response = requests.post(INVOKE_URL, headers=headers, json=payload)
        response.raise_for_status()
        
        result = response.json()
        
        if "choices" in result and len(result["choices"]) > 0:
            analysis_result = result["choices"][0]["message"]["content"]
            return ImageAnalysisResponse(
                success=True,
                result=analysis_result
            )
        else:
            return ImageAnalysisResponse(
                success=False,
                result="",
                error="Не удалось получить результат анализа"
            )
            
    except requests.exceptions.RequestException as e:
        return ImageAnalysisResponse(
            success=False,
            result="",
            error=f"Ошибка при обращении к NVIDIA API: {str(e)}"
        )
    except Exception as e:
        return ImageAnalysisResponse(
            success=False,
            result="",
            error=f"Внутренняя ошибка сервера: {str(e)}"
        )

@app.post("/analyze-image-upload", response_model=ImageAnalysisResponse)
async def analyze_uploaded_image(
    file: UploadFile = File(...),
    text_prompt: str = Form("Опиши что ты видишь на этом изображении, отвечай на русском языке"),
    max_tokens: int = Form(512),
    temperature: float = Form(1.0)
):
    """Анализ загруженного изображения"""
    
    # Проверяем тип файла
    if not file.content_type or not file.content_type.startswith('image/'):
        raise HTTPException(status_code=400, detail="Файл должен быть изображением")
    
    try:
        # Читаем содержимое файла и кодируем в base64
        file_content = await file.read()
        image_b64 = base64.b64encode(file_content).decode()
        
        headers = {
            "Authorization": f"Bearer {NVIDIA_API_KEY}",
            "Accept": "application/json"
        }

        payload = {
            "model": "meta/llama-3.2-90b-vision-instruct",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": text_prompt},
                        {"type": "image_url", "image_url": {"url": f"data:{file.content_type};base64,{image_b64}"}}
                    ]
                }
            ],
            "max_tokens": max_tokens,
            "temperature": temperature,
            "top_p": 1.00,
            "frequency_penalty": 0.00,
            "presence_penalty": 0.00,
            "stream": False
        }

        response = requests.post(INVOKE_URL, headers=headers, json=payload)
        response.raise_for_status()
        
        result = response.json()
        
        if "choices" in result and len(result["choices"]) > 0:
            analysis_result = result["choices"][0]["message"]["content"]
            return ImageAnalysisResponse(
                success=True,
                result=analysis_result
            )
        else:
            return ImageAnalysisResponse(
                success=False,
                result="",
                error="Не удалось получить результат анализа"
            )
            
    except requests.exceptions.RequestException as e:
        return ImageAnalysisResponse(
            success=False,
            result="",
            error=f"Ошибка при обращении к NVIDIA API: {str(e)}"
        )
    except Exception as e:
        return ImageAnalysisResponse(
            success=False,
            result="",
            error=f"Внутренняя ошибка сервера: {str(e)}"
        )


@app.post("/agent-analyze-url", response_model=AgentAnalysisResponse)
async def agent_analyze_image_url(request: ImageAnalysisRequest):
    """Агент: анализ изображения по URL с использованием NVIDIA + RAG, вывод строго JSON по promt.txt."""
    if not request.image_url:
        raise HTTPException(status_code=400, detail="URL изображения обязателен")

    try:
        result = run_agent(
            image_input=request.image_url,
            max_tokens=request.max_tokens,
            temperature=request.temperature,
        )
        return AgentAnalysisResponse(success=True, result_json=result["final_json"], logs=result.get("logs", []))
    except Exception as e:
        return AgentAnalysisResponse(success=False, result_json="", logs=[], error=str(e))


@app.post("/agent-analyze-upload", response_model=AgentAnalysisResponse)
async def agent_analyze_image_upload(
    file: UploadFile = File(...),
    max_tokens: int = Form(768),
    temperature: float = Form(0.2)
):
    """Агент: анализ загруженного изображения (файл → data URI) с использованием NVIDIA + RAG."""
    if not file.content_type or not file.content_type.startswith('image/'):
        raise HTTPException(status_code=400, detail="Файл должен быть изображением")

    try:
        file_content = await file.read()
        image_b64 = base64.b64encode(file_content).decode()
        data_url = f"data:{file.content_type};base64,{image_b64}"

        result = run_agent(
            image_input=data_url,
            max_tokens=max_tokens,
            temperature=temperature,
        )
        return AgentAnalysisResponse(success=True, result_json=result["final_json"], logs=result.get("logs", []))
    except Exception as e:
        return AgentAnalysisResponse(success=False, result_json="", logs=[], error=str(e))

if __name__ == "__main__":
    port = int(os.getenv("PORT", 8000))
    app_path = "agent.main:app" if __package__ else "main:app"
    uvicorn.run(
        app_path,
        host="0.0.0.0",
        port=port,
        reload=True
    )
