"""
Конфигурация backend3
"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    debug: bool = True
    host: str = "0.0.0.0"
    port: int = 8002

    rabbitmq_url: str = "amqp://app:app_password@rabbitmq:5672/"
    rabbitmq_queue_image_results: str = "image_results"
    rabbitmq_queue_image_descriptions: str = "image_descriptions"  # выход: текст описания

    # Интеграции
    backend1_url: str = "http://backend1:8000"

    # NVIDIA API
    nvidia_api_key: str | None = None
    nvidia_invoke_url: str = "https://integrate.api.nvidia.com/v1/chat/completions"

    class Config:
        env_file = ".env"


settings = Settings()


