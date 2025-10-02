"""
Конфигурация backend_2
"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    debug: bool = True
    host: str = "0.0.0.0"
    port: int = 8001

    rabbitmq_url: str = "amqp://app:app_password@rabbitmq:5672/"
    rabbitmq_queue_image_tasks: str = "image_tasks"
    rabbitmq_queue_image_results: str = "image_results"

    class Config:
        env_file = ".env"


settings = Settings()


