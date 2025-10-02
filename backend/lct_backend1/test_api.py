"""
Пример тестирования API endpoints
"""
import requests
import json
from pathlib import Path

# Базовый URL API
BASE_URL = "http://localhost:8000/api/v1"

def test_health_check():
    """Тест проверки здоровья сервиса"""
    response = requests.get("http://localhost:8000/health")
    print(f"Health check: {response.status_code}")
    print(f"Response: {response.json()}")

def test_upload_image():
    """Тест загрузки изображения"""
    # Пример метаданных
    metadata = {
        "taken_at": "2024-01-01T12:00:00",
        "location": "Москва, Красная площадь",
        "author": "Тестовый пользователь"
    }
    
    # Подготовка файлов для отправки
    # Замените на реальный путь к изображению
    image_path = "test_image.jpg"
    
    if not Path(image_path).exists():
        print(f"Файл {image_path} не найден. Создайте тестовое изображение.")
        return
    
    with open(image_path, 'rb') as f:
        files = {'file': f}
        data = {'metadata': json.dumps(metadata)}
        
        response = requests.post(
            f"{BASE_URL}/images/upload",
            files=files,
            data=data
        )
    
    print(f"Upload status: {response.status_code}")
    if response.status_code == 200:
        result = response.json()
        print(f"Image ID: {result['id']}")
        print(f"Filename: {result['filename']}")
        print(f"Processing status: {result['processing_status']}")
        return result['id']
    else:
        print(f"Error: {response.text}")

def test_get_images():
    """Тест получения списка изображений"""
    response = requests.get(f"{BASE_URL}/images/")
    print(f"Get images status: {response.status_code}")
    if response.status_code == 200:
        images = response.json()
        print(f"Found {len(images)} images")
        for image in images:
            print(f"- ID: {image['id']}, Status: {image['processing_status']}")

def test_get_image(image_id):
    """Тест получения конкретного изображения"""
    response = requests.get(f"{BASE_URL}/images/{image_id}")
    print(f"Get image {image_id} status: {response.status_code}")
    if response.status_code == 200:
        image = response.json()
        print(f"Image details: {image['filename']}")
        print(f"S3 key: {image['s3_key']}")
        print(f"S3 URL: {image['s3_url']}")
        print(f"Fragments count: {len(image['fragments'])}")

def test_download_image(image_id):
    """Тест получения URL для скачивания изображения"""
    response = requests.get(f"{BASE_URL}/images/{image_id}/download")
    print(f"Download image {image_id} status: {response.status_code}")
    if response.status_code == 200:
        result = response.json()
        print(f"Download URL: {result['download_url']}")
        print(f"Expires in: {result['expires_in']} seconds")

def test_delete_image(image_id):
    """Тест удаления изображения"""
    response = requests.delete(f"{BASE_URL}/images/{image_id}")
    print(f"Delete image {image_id} status: {response.status_code}")
    if response.status_code == 200:
        result = response.json()
        print(f"Delete result: {result['message']}")

if __name__ == "__main__":
    print("Тестирование LCT Backend1 API")
    print("=" * 40)
    
    # Тест проверки здоровья
    test_health_check()
    print()
    
    # Тест загрузки изображения
    image_id = test_upload_image()
    print()
    
    # Тест получения списка изображений
    test_get_images()
    print()
    
    # Тест получения конкретного изображения
    if image_id:
        test_get_image(image_id)
        print()
        
        # Тест скачивания изображения
        test_download_image(image_id)
        print()
        
        # Тест удаления изображения (осторожно!)
        # test_delete_image(image_id)
