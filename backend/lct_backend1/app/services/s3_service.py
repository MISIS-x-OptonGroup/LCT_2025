"""
Сервис для работы с Amazon S3
"""
import uuid
import logging
from typing import Optional, BinaryIO
from pathlib import Path
import aioboto3
from botocore.exceptions import ClientError, NoCredentialsError
from fastapi import UploadFile

from app.core.config import settings

logger = logging.getLogger(__name__)


class S3Service:
    """
    Сервис для работы с Amazon S3 хранилищем
    """
    
    def __init__(self):
        self.bucket_name = settings.s3_bucket_name
        self.region = settings.s3_region
        self.access_key = settings.aws_access_key_id
        self.secret_key = settings.aws_secret_access_key
        self.endpoint_url = settings.s3_endpoint_url
        
    async def _get_s3_client(self):
        """
        Создает асинхронный S3 клиент
        """
        session = aioboto3.Session()
        return session.client(
            's3',
            region_name=self.region,
            aws_access_key_id=self.access_key,
            aws_secret_access_key=self.secret_key,
            endpoint_url=self.endpoint_url if self.endpoint_url else None
        )
    
    async def upload_file(
        self, 
        file: UploadFile, 
        folder: str = "images"
    ) -> tuple[str, str]:
        """
        Загружает файл в S3 хранилище
        
        Args:
            file: Загружаемый файл
            folder: Папка в S3 bucket
            
        Returns:
            tuple[str, str]: (s3_key, s3_url) - ключ файла в S3 и полный URL
        """
        try:
            # Генерируем уникальное имя файла
            file_extension = Path(file.filename).suffix.lower()
            unique_filename = f"{uuid.uuid4()}{file_extension}"
            s3_key = f"{folder}/{unique_filename}"
            
            # Читаем содержимое файла
            await file.seek(0)  # Сбрасываем указатель на начало
            file_content = await file.read()
            await file.seek(0)  # Возвращаем указатель на начало для других операций
            
            # Загружаем в S3
            async with await self._get_s3_client() as s3_client:
                await s3_client.put_object(
                    Bucket=self.bucket_name,
                    Key=s3_key,
                    Body=file_content,
                    ContentType=file.content_type or 'application/octet-stream',
                    Metadata={
                        'original_filename': file.filename or 'unknown',
                        'uploaded_by': 'lct_backend1'
                    }
                )
            
            # Формируем URL файла
            if self.endpoint_url:
                # Для MinIO или других S3-совместимых сервисов
                s3_url = f"{self.endpoint_url}/{self.bucket_name}/{s3_key}"
            else:
                # Для AWS S3
                s3_url = f"https://{self.bucket_name}.s3.{self.region}.amazonaws.com/{s3_key}"
            
            logger.info(f"Файл успешно загружен в S3: {s3_key}")
            return s3_key, s3_url
            
        except NoCredentialsError:
            logger.error("AWS credentials не настроены")
            raise Exception("Ошибка конфигурации S3: отсутствуют учетные данные")
        except ClientError as e:
            error_code = e.response['Error']['Code']
            logger.error(f"Ошибка S3 клиента: {error_code} - {e}")
            raise Exception(f"Ошибка загрузки в S3: {error_code}")
        except Exception as e:
            logger.error(f"Неожиданная ошибка при загрузке в S3: {e}")
            raise Exception(f"Не удалось загрузить файл в S3: {e}")
    
    async def upload_bytes(
        self, 
        file_content: bytes, 
        filename: str, 
        content_type: str,
        folder: str = "fragments"
    ) -> tuple[str, str]:
        """
        Загружает байты в S3 хранилище
        
        Args:
            file_content: Содержимое файла в байтах
            filename: Имя файла
            content_type: MIME тип файла
            folder: Папка в S3 bucket
            
        Returns:
            tuple[str, str]: (s3_key, s3_url) - ключ файла в S3 и полный URL
        """
        try:
            # Генерируем уникальное имя файла
            file_extension = Path(filename).suffix.lower()
            unique_filename = f"{uuid.uuid4()}{file_extension}"
            s3_key = f"{folder}/{unique_filename}"
            
            # Загружаем в S3
            async with await self._get_s3_client() as s3_client:
                await s3_client.put_object(
                    Bucket=self.bucket_name,
                    Key=s3_key,
                    Body=file_content,
                    ContentType=content_type,
                    Metadata={
                        'original_filename': filename,
                        'uploaded_by': 'lct_backend1'
                    }
                )
            
            # Формируем URL файла
            if self.endpoint_url:
                s3_url = f"{self.endpoint_url}/{self.bucket_name}/{s3_key}"
            else:
                s3_url = f"https://{self.bucket_name}.s3.{self.region}.amazonaws.com/{s3_key}"
            
            logger.info(f"Байты успешно загружены в S3: {s3_key}")
            return s3_key, s3_url
            
        except Exception as e:
            logger.error(f"Ошибка при загрузке байтов в S3: {e}")
            raise Exception(f"Не удалось загрузить данные в S3: {e}")
    
    async def get_file_url(self, s3_key: str, expires_in: int = 3600) -> str:
        """
        Генерирует подписанный URL для доступа к файлу
        
        Args:
            s3_key: Ключ файла в S3
            expires_in: Время действия URL в секундах (по умолчанию 1 час)
            
        Returns:
            str: Подписанный URL для доступа к файлу
        """
        try:
            async with await self._get_s3_client() as s3_client:
                url = await s3_client.generate_presigned_url(
                    'get_object',
                    Params={'Bucket': self.bucket_name, 'Key': s3_key},
                    ExpiresIn=expires_in
                )
            return url
            
        except Exception as e:
            logger.error(f"Ошибка при генерации подписанного URL: {e}")
            raise Exception(f"Не удалось создать URL для файла: {e}")
    
    async def delete_file(self, s3_key: str) -> bool:
        """
        Удаляет файл из S3 хранилища
        
        Args:
            s3_key: Ключ файла в S3
            
        Returns:
            bool: True если файл успешно удален
        """
        try:
            async with await self._get_s3_client() as s3_client:
                await s3_client.delete_object(
                    Bucket=self.bucket_name,
                    Key=s3_key
                )
            
            logger.info(f"Файл удален из S3: {s3_key}")
            return True
            
        except Exception as e:
            logger.error(f"Ошибка при удалении файла из S3: {e}")
            return False
    
    async def file_exists(self, s3_key: str) -> bool:
        """
        Проверяет существование файла в S3
        
        Args:
            s3_key: Ключ файла в S3
            
        Returns:
            bool: True если файл существует
        """
        try:
            async with await self._get_s3_client() as s3_client:
                await s3_client.head_object(
                    Bucket=self.bucket_name,
                    Key=s3_key
                )
            return True
            
        except ClientError as e:
            if e.response['Error']['Code'] == '404':
                return False
            raise
        except Exception as e:
            logger.error(f"Ошибка при проверке существования файла: {e}")
            return False
    
    async def get_file_info(self, s3_key: str) -> Optional[dict]:
        """
        Получает информацию о файле в S3
        
        Args:
            s3_key: Ключ файла в S3
            
        Returns:
            Optional[dict]: Информация о файле или None если файл не найден
        """
        try:
            async with await self._get_s3_client() as s3_client:
                response = await s3_client.head_object(
                    Bucket=self.bucket_name,
                    Key=s3_key
                )
            
            return {
                'size': response.get('ContentLength', 0),
                'content_type': response.get('ContentType', ''),
                'last_modified': response.get('LastModified'),
                'metadata': response.get('Metadata', {})
            }
            
        except ClientError as e:
            if e.response['Error']['Code'] == '404':
                return None
            raise
        except Exception as e:
            logger.error(f"Ошибка при получении информации о файле: {e}")
            return None
