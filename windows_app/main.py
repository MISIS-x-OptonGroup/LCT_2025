# qt_engine.py

import sys
import os
import json
import requests
import base64
from io import BytesIO
from PIL import Image
from PyQt6.QtWidgets import QApplication, QMainWindow, QFileDialog, QSystemTrayIcon, QMenu
from PyQt6.QtWebEngineWidgets import QWebEngineView
from PyQt6.QtWebEngineCore import QWebEnginePage, QWebEngineSettings
from PyQt6.QtCore import QUrl, QObject, pyqtSlot
from PyQt6.QtWebChannel import QWebChannel
from PyQt6.QtGui import QIcon, QAction

class Bridge(QObject):
    def __init__(self):
        super().__init__()
        self.current_image_path = None  # Путь к текущему загруженному изображению
    @pyqtSlot(result=str)
    def open_file_dialog(self):
        file_path, _ = QFileDialog.getOpenFileName(
            None,
            "Выбрать изображение",
            "",
            "Image Files (*.png *.jpg *.jpeg *.bmp)"
        )
        if file_path:
            return QUrl.fromLocalFile(file_path).toString()
        return ""

    @pyqtSlot(str, result=str)
    def upload_image(self, file_url_str):
        try:
            # Преобразуем file:/// URL в локальный путь
            local_path = QUrl(file_url_str).toLocalFile()
            if not local_path or not os.path.exists(local_path):
                return json.dumps({"error": "Файл не найден"})
            
            # Сохраняем путь к изображению для последующей обрезки
            self.current_image_path = local_path
            
            api_base = os.environ.get("API_BASE_URL", "http://36.34.82.242:18087")
            endpoint = f"{api_base}/api/v1/images/upload"
            
            # Определяем content type по расширению
            filename = os.path.basename(local_path)
            ext = os.path.splitext(filename)[1].lower()
            content_type = {
                '.jpg': 'image/jpeg',
                '.jpeg': 'image/jpeg',
                '.png': 'image/png',
                '.bmp': 'image/bmp'
            }.get(ext, 'application/octet-stream')
            
            # Читаем файл полностью перед отправкой
            with open(local_path, "rb") as f:
                file_content = f.read()
            
            # Отправляем запрос с содержимым файла
            files = {"file": (filename, file_content, content_type)}
            resp = requests.post(endpoint, files=files, timeout=30)
            resp.raise_for_status()
            return resp.text
        except requests.exceptions.RequestException as e:
            return json.dumps({"error": f"Ошибка сети: {str(e)}"})
        except Exception as e:
            return json.dumps({"error": str(e)})

    def _crop_image_by_bbox(self, bbox):
        """Обрезает текущее изображение по bbox координатам
        
        Args:
            bbox: Массив [x1, y1, x2, y2] - координаты левого верхнего и правого нижнего углов
            
        Returns:
            str: Data URL с обрезанным изображением или None при ошибке
        """
        try:
            if not self.current_image_path or not os.path.exists(self.current_image_path):
                print("Ошибка: нет текущего изображения")
                return None
            
            # Открываем изображение
            img = Image.open(self.current_image_path)
            print(f"Размер оригинального изображения: {img.size}")
            
            # bbox формат: [x1, y1, x2, y2]
            if not bbox or len(bbox) != 4:
                print(f"Ошибка: неверный формат bbox: {bbox}")
                return None
            
            x1, y1, x2, y2 = bbox
            
            # Преобразуем в целые числа и проверяем границы
            x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
            
            # Убеждаемся что координаты в правильном порядке
            if x1 > x2:
                x1, x2 = x2, x1
            if y1 > y2:
                y1, y2 = y2, y1
            
            # Проверяем границы изображения
            width, height = img.size
            x1 = max(0, min(x1, width))
            y1 = max(0, min(y1, height))
            x2 = max(0, min(x2, width))
            y2 = max(0, min(y2, height))
            
            # Проверяем что область имеет размер (не нулевая ширина/высота)
            crop_width = x2 - x1
            crop_height = y2 - y1
            
            if crop_width <= 0 or crop_height <= 0:
                print(f"Ошибка: невалидная область обрезки ({crop_width}x{crop_height})")
                return None
            
            print(f"Обрезка по координатам: ({x1}, {y1}, {x2}, {y2}) = {crop_width}x{crop_height}")
            
            # Обрезаем изображение
            cropped = img.crop((x1, y1, x2, y2))
            print(f"Размер обрезанного изображения: {cropped.size}")
            
            # Конвертируем в base64
            buffered = BytesIO()
            cropped.save(buffered, format="JPEG", quality=85)
            img_str = base64.b64encode(buffered.getvalue()).decode()
            
            return f"data:image/jpeg;base64,{img_str}"
            
        except Exception as e:
            print(f"Ошибка обрезки изображения: {e}")
            import traceback
            traceback.print_exc()
            return None
    
    @pyqtSlot(result=str)
    def get_all_images(self):
        """Получает все изображения с сервера для отображения на карте"""
        try:
            api_base = os.environ.get("API_BASE_URL", "http://36.34.82.242:18087")
            limit = 1000
            endpoint = f"{api_base}/api/v1/images/?skip=0&limit={limit}"
            
            print(f"Загрузка изображений: {endpoint}")
            resp = requests.get(endpoint, timeout=30)
            resp.raise_for_status()
            
            images = resp.json()
            print(f"Получено изображений: {len(images)}")
            
            # Фильтруем изображения с координатами
            images_with_location = []
            for img in images:
                location = img.get("location")
                print(f"  Изображение #{img.get('id')}: location='{location}', type={type(location)}")
                
                # Проверяем что location это строка и содержит координаты
                if location and isinstance(location, str) and len(location) > 0 and "," in location:
                    try:
                        # Проверяем что можно распарсить координаты
                        parts = location.split(',')
                        lat = float(parts[0].strip())
                        lon = float(parts[1].strip())
                        
                        images_with_location.append({
                            "id": img.get("id"),
                            "location": location,
                            "filename": img.get("original_filename", "Unknown"),
                            "created_at": img.get("created_at"),
                            "processing_status": img.get("processing_status", "unknown")
                        })
                        print(f"    ✓ Добавлено: {lat}, {lon}")
                    except (ValueError, IndexError) as e:
                        print(f"    ✗ Ошибка парсинга координат: {e}")
            
            print(f"\n=== Итого: {len(images_with_location)} изображений с координатами из {len(images)} ===\n")
            
            return json.dumps({
                "success": True,
                "total": len(images),
                "with_location": len(images_with_location),
                "images": images_with_location
            })
            
        except Exception as e:
            print(f"Ошибка загрузки всех изображений: {e}")
            import traceback
            traceback.print_exc()
            return json.dumps({"success": False, "error": str(e)})
    
    @pyqtSlot(int, result=str)
    def get_image(self, image_id):
        try:
            api_base = os.environ.get("API_BASE_URL", "http://36.34.82.242:18087")
            endpoint = f"{api_base}/api/v1/images/{image_id}"
            resp = requests.get(endpoint, timeout=15)
            resp.raise_for_status()
            
            # Парсим ответ
            data = resp.json()
            
            # Если обработка завершена, создаем фрагменты локально по bbox
            if data.get("processing_status") == "completed" and data.get("detected_objects"):
                # Если нет текущего изображения (загружаем с карты), пытаемся скачать оригинал
                if not self.current_image_path or not os.path.exists(self.current_image_path):
                    print(f"Нет локального изображения, пытаемся скачать с сервера...")
                    image_downloaded = self._download_original_image(data, api_base)
                    if not image_downloaded:
                        print("⚠️ Не удалось скачать оригинальное изображение, фрагменты не будут созданы")
                
                if self.current_image_path and os.path.exists(self.current_image_path):
                    print(f"Обработка завершена, создаем фрагменты локально из оригинала")
                    print(f"Путь к изображению: {self.current_image_path}")
                    
                    # Обрезаем фрагменты локально по bbox координатам
                    created_count = 0
                    for i, obj in enumerate(data["detected_objects"]):
                        bbox = obj.get("bbox")
                        label = obj.get("label", "объект")
                        
                        if bbox and len(bbox) == 4:
                            print(f"\nОбъект {i + 1}/{len(data['detected_objects'])}: {label}")
                            print(f"  BBox: {bbox}")
                            
                            # Создаем обрезанное изображение
                            fragment_data_url = self._crop_image_by_bbox(bbox)
                            
                            if fragment_data_url:
                                obj["fragment_url"] = fragment_data_url
                                created_count += 1
                                print(f"  ✓ Фрагмент создан ({len(fragment_data_url)} байт)")
                            else:
                                print(f"  ✗ Не удалось создать фрагмент")
                        else:
                            print(f"Объект {i}: нет bbox или неверный формат")
                    
                    print(f"\nСоздано фрагментов: {created_count} из {len(data['detected_objects'])}")
            
            return json.dumps(data)
        except Exception as e:
            return json.dumps({"error": str(e)})
    
    def _download_original_image(self, image_data, api_base):
        """Скачивает оригинальное изображение с сервера для обрезки"""
        try:
            # Пытаемся получить подписанный URL для скачивания
            image_id = image_data.get("id")
            if not image_id:
                return False
            
            download_endpoint = f"{api_base}/api/v1/images/{image_id}/download"
            print(f"Запрос подписанного URL: {download_endpoint}")
            
            resp = requests.get(download_endpoint, timeout=15)
            resp.raise_for_status()
            
            download_data = resp.json()
            download_url = download_data.get("download_url")
            
            if not download_url:
                print("Нет download_url в ответе")
                return False
            
            # Заменяем внутренний minio URL на публичный
            if "minio:9000" in download_url or download_url.startswith("http://minio"):
                # MinIO API доступен на публичном порту 17897
                download_url = download_url.replace("http://minio:9000", "http://36.34.82.242:17897")
                download_url = download_url.replace("minio:9000", "36.34.82.242:17897")
                print(f"URL преобразован в публичный: {download_url[:100]}...")
            else:
                print(f"Скачиваем изображение: {download_url[:100]}...")
            
            # Скачиваем файл
            img_resp = requests.get(download_url, timeout=30)
            img_resp.raise_for_status()
            
            # Сохраняем во временный файл
            import tempfile
            temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.jpg')
            temp_file.write(img_resp.content)
            temp_file.close()
            
            self.current_image_path = temp_file.name
            print(f"✓ Изображение скачано: {self.current_image_path} ({len(img_resp.content)} байт)")
            
            return True
            
        except Exception as e:
            print(f"Ошибка скачивания оригинала: {e}")
            import traceback
            traceback.print_exc()
            return False

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        self.setWindowTitle("AI: Анализ здоровья дерева")
        
        # Устанавливаем иконку приложения
        icon_path = os.path.join(os.path.dirname(__file__), "icons", "leaf.svg")
        self.app_icon = QIcon(icon_path) if os.path.exists(icon_path) else QIcon()
        
        if not self.app_icon.isNull():
            # Иконка окна (в заголовке и панели задач)
            self.setWindowIcon(self.app_icon)
            
            # System Tray иконка (в трее)
            self.tray_icon = QSystemTrayIcon(self.app_icon, self)
            self.tray_icon.setToolTip("AI: Анализ здоровья дерева")
            
            # Меню для трея
            tray_menu = QMenu()
            show_action = QAction("Показать окно", self)
            show_action.triggered.connect(self.show)
            quit_action = QAction("Выход", self)
            quit_action.triggered.connect(QApplication.quit)
            
            tray_menu.addAction(show_action)
            tray_menu.addSeparator()
            tray_menu.addAction(quit_action)
            
            self.tray_icon.setContextMenu(tray_menu)
            self.tray_icon.activated.connect(self.on_tray_icon_activated)
            self.tray_icon.show()
        
        self.web_view = QWebEngineView()
        self.page = QWebEnginePage(self)
        self.web_view.setPage(self.page)
        
        # Включаем необходимые настройки WebEngine
        settings = self.page.settings()
        settings.setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessRemoteUrls, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.JavascriptEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.LocalStorageEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.AllowRunningInsecureContent, True)
        
        # Перехватываем JavaScript console.log для отладки
        self.page.javaScriptConsoleMessage = lambda level, message, lineNumber, sourceID: \
            print(f"js: {message}")
        
        self.bridge = Bridge()
        self.channel = QWebChannel()
        self.channel.registerObject("backend", self.bridge)
        self.page.setWebChannel(self.channel)

        file_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "index.html"))
        self.web_view.setUrl(QUrl.fromLocalFile(file_path))

        self.setCentralWidget(self.web_view)
    
    def on_tray_icon_activated(self, reason):
        """Обработчик клика по иконке в трее"""
        if reason == QSystemTrayIcon.ActivationReason.Trigger:
            # Показываем/скрываем окно при клике
            if self.isVisible():
                self.hide()
            else:
                self.show()
                self.activateWindow()

if __name__ == "__main__":
    app = QApplication(sys.argv)
    
    # Устанавливаем иконку приложения для всего QApplication
    icon_path = os.path.join(os.path.dirname(__file__), "icons", "leaf.svg")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))
    
    window = MainWindow()
    window.showMaximized()  # Открываем окно в развернутом виде
    sys.exit(app.exec())