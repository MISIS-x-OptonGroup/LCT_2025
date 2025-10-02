document.addEventListener('DOMContentLoaded', () => {
    // Проверяем загрузку Leaflet
    if (typeof L === 'undefined') {
        console.error('Leaflet не загружен! Проверьте подключение к интернету.');
    } else {
        console.log('Leaflet успешно загружен');
    }
    
    // --- Элементы DOM ---
    const uploadStep = document.getElementById('uploadStep');
    const analysisStep = document.getElementById('analysisStep');
    const mapStep = document.getElementById('mapStep');
    const uploadButton = document.getElementById('uploadButton');
    const restartButton = document.getElementById('restartButton');
    const mapButton = document.getElementById('mapButton');
    const backFromMapButton = document.getElementById('backFromMapButton');
    
    // Элементы модального окна превью
    const imagePreviewModal = document.getElementById('imagePreviewModal');
    const closePreviewButton = document.getElementById('closePreviewButton');
    const previewImage = document.getElementById('previewImage');
    const previewInfo = document.getElementById('previewInfo');
    const previewLoader = document.getElementById('previewLoader');
    const openAnalysisButton = document.getElementById('openAnalysisButton');
    
    const mainImage = document.getElementById('mainImage');
    const imagePreview = document.getElementById('imagePreview');
    const analysisResult = document.getElementById('analysisResult');
    const fragmentImageWrapper = document.getElementById('fragmentImageWrapper');
    
    const prevButton = document.getElementById('prevButton');
    const nextButton = document.getElementById('nextButton');
    const fragmentIndicator = document.getElementById('fragmentIndicator');
    const responseText = document.getElementById('responseText');
    const fragmentNumberSpan = document.getElementById('fragmentNumber');

    // --- Переменные состояния ---
    let currentFragment = 0;
    let mainImageSrc = '';
    let lastImageId = null;
    let pollingTimer = null;
    let analysisData = null; // Данные анализа от бэкенда
    let detectedObjects = []; // Массив обнаруженных объектов

    // --- Инициализация QWebChannel ---
    new QWebChannel(qt.webChannelTransport, (channel) => {
        window.backend = channel.objects.backend;

        uploadButton.addEventListener('click', async () => {
            uploadButton.disabled = true;
            const filePath = await window.backend.open_file_dialog();
            uploadButton.disabled = false;
            
            if (filePath) {
                mainImageSrc = filePath;
                mainImage.src = mainImageSrc;
                mainImage.style.display = 'block';
                
                // Переключаем шаги
                showStep(analysisStep);
                startAnalysisAnimation();

                // Загружаем изображение на сервер
                try {
                    const uploadRespText = await window.backend.upload_image(filePath);
                    const uploadResp = safeJsonParse(uploadRespText);
                    
                    if (!uploadResp) {
                        throw new Error('Не удалось разобрать ответ сервера');
                    }
                    
                    if (uploadResp.error) {
                        throw new Error(uploadResp.error);
                    }
                    
                    if (uploadResp.id !== undefined) {
                        lastImageId = uploadResp.id;
                        console.log(`Изображение загружено с ID: ${lastImageId}`);
                        // Начинаем поллинг статуса
                        startPollingStatus(lastImageId);
                    } else {
                        throw new Error('В ответе сервера отсутствует ID изображения');
                    }
                } catch (e) {
                    console.error('Ошибка при загрузке:', e);
                    stopAnalysisAndShowError(`Ошибка загрузки: ${e.message || e}`);
                }
            }
        });

        restartButton.addEventListener('click', () => {
            cancelPolling();
            // Сброс состояния
            currentFragment = 0;
            analysisData = null;
            detectedObjects = [];
            mainImage.style.display = 'none';
            analysisResult.classList.remove('active');
            imagePreview.classList.remove('is-scanning');
            showStep(uploadStep);
        });
    });
    
    // --- Переходы между шагами ---
    function showStep(target) {
        uploadStep.classList.remove('active');
        analysisStep.classList.remove('active');
        mapStep.classList.remove('active');
        // Небольшая пауза под CSS transition, если требуется сценарий исчезновения
        setTimeout(() => {
            target.classList.add('active');
            if (target === mapStep) {
                initMapIfNeeded();
                setTimeout(() => {
                    if (leafletMap) {
                        leafletMap.invalidateSize();
                    }
                }, 0);
            }
        }, 0);
    }

    // --- Кнопки карты ---
    mapButton.addEventListener('click', () => {
        showStep(mapStep);
        fitToMarkersIfAny();
    });

    backFromMapButton.addEventListener('click', () => {
        // Возвращаемся к анализу, если он был начат, иначе к загрузке
        if (mainImage.src) {
            showStep(analysisStep);
        } else {
            showStep(uploadStep);
        }
    });

    // --- Логика навигации ---
    prevButton.addEventListener('click', () => {
        if (currentFragment > 0) {
            currentFragment--;
            updateFragmentView();
        }
    });

    nextButton.addEventListener('click', () => {
        if (detectedObjects.length > 0 && currentFragment < detectedObjects.length - 1) {
            currentFragment++;
            updateFragmentView();
        }
    });

    // --- Анимация сканирования и завершение ---
    function startAnalysisAnimation() {
        imagePreview.classList.add('is-scanning');
        analysisResult.classList.remove('active');
    }

    function stopAnalysisAndShowResults() {
        imagePreview.classList.remove('is-scanning');
        analysisResult.classList.add('active');
        updateFragmentView();
    }

    function stopAnalysisAndShowError(message) {
        imagePreview.classList.remove('is-scanning');
        analysisResult.classList.add('active');
        responseText.innerText = message;
    }

    // --- Обновление вида фрагмента ---
    function updateFragmentView() {
        const totalFragments = detectedObjects.length;
        
        // Обновляем индикатор и текст
        fragmentIndicator.innerText = `Объект ${currentFragment + 1} / ${totalFragments}`;
        fragmentNumberSpan.innerText = currentFragment + 1;
        
        // Формируем текст анализа из данных бэкенда
        if (detectedObjects.length > 0 && detectedObjects[currentFragment]) {
            const obj = detectedObjects[currentFragment];
            const desc = obj.description;
            let analysisText = '';
            
            if (desc && desc.object) {
                const objData = desc.object;
                
                // Тип объекта и вид
                analysisText += `**Объект ${currentFragment + 1}: ${objData.type || 'неизвестно'}**\n\n`;
                
                if (objData.species && objData.species.label_ru) {
                    analysisText += `**Вид:** ${objData.species.label_ru} (уверенность: ${objData.species.confidence}%)\n\n`;
                }
                
                // Состояние
                if (objData.condition) {
                    const cond = objData.condition;
                    analysisText += `**Состояние:**\n`;
                    
                    // Проверяем различные признаки
                    if (cond.crown_damage && cond.crown_damage.present) {
                        analysisText += `- Повреждение кроны: ${cond.crown_damage.evidence.join(', ')}\n`;
                    }
                    if (cond.trunk_decay && cond.trunk_decay.present) {
                        analysisText += `- Гниль ствола: ${cond.trunk_decay.evidence.join(', ')}\n`;
                    }
                    if (cond.bark_detachment && cond.bark_detachment.present) {
                        analysisText += `- Отслоение коры: ${cond.bark_detachment.locations.join(', ')}\n`;
                    }
                    if (cond.cavities && cond.cavities.present) {
                        analysisText += `- Дупла: ${cond.cavities.locations.join(', ')}\n`;
                    }
                    if (cond.cracks && cond.cracks.present) {
                        analysisText += `- Трещины: ${cond.cracks.locations.join(', ')}\n`;
                    }
                    
                    // Заболевания
                    if (cond.diseases && cond.diseases.length > 0) {
                        analysisText += `\n**Возможные заболевания:**\n`;
                        cond.diseases.forEach(disease => {
                            analysisText += `- ${disease.name_ru} (вероятность: ${disease.likelihood}%)`;
                            if (disease.evidence && disease.evidence.length > 0) {
                                analysisText += `: ${disease.evidence.join(', ')}`;
                            }
                            analysisText += `\n`;
                        });
                    }
                    
                    // Вредители
                    if (cond.pests && cond.pests.length > 0) {
                        analysisText += `\n**Возможные вредители:**\n`;
                        cond.pests.forEach(pest => {
                            analysisText += `- ${pest.name_ru} (вероятность: ${pest.likelihood}%)`;
                            if (pest.evidence && pest.evidence.length > 0) {
                                analysisText += `: ${pest.evidence.join(', ')}`;
                            }
                            analysisText += `\n`;
                        });
                    }
                    
                    if (cond.dry_branches_pct !== undefined) {
                        analysisText += `\n**Сухие ветви:** ${cond.dry_branches_pct}%\n`;
                    }
                }
                
                // Риск
                if (objData.risk) {
                    analysisText += `\n**Уровень риска:** ${objData.risk.level}`;
                    if (objData.risk.drivers && objData.risk.drivers.length > 0) {
                        analysisText += ` (${objData.risk.drivers.join(', ')})`;
                    }
                    analysisText += `\n`;
                }
                
                // Качество данных
                if (desc.data_quality) {
                    analysisText += `\n**Качество анализа:** ${desc.data_quality.overall_confidence}%`;
                    if (desc.data_quality.issues && desc.data_quality.issues.length > 0) {
                        analysisText += `\n**Проблемы:** ${desc.data_quality.issues.join(', ')}`;
                    }
                }
            } else {
                analysisText = `**Объект ${currentFragment + 1}**\n\nТип: ${obj.label}\nУверенность: ${(obj.confidence * 100).toFixed(1)}%`;
            }
            
            responseText.innerHTML = analysisText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>').replace(/\n/g, '<br>');
            
            // Показываем фрагмент, если есть URL
            if (obj.fragment_url) {
                const url = obj.fragment_url;
                fragmentImageWrapper.style.backgroundImage = `url("${url}")`;
                fragmentImageWrapper.style.backgroundSize = 'cover';
                fragmentImageWrapper.style.backgroundPosition = 'center';
            } else {
                // Если нет URL, показываем оригинал
                fragmentImageWrapper.style.backgroundImage = `url("${mainImageSrc}")`;
                fragmentImageWrapper.style.backgroundSize = 'cover';
                fragmentImageWrapper.style.backgroundPosition = 'center';
            }
        } else {
            responseText.innerHTML = 'Нет данных для отображения';
        }

        // Обновляем состояние кнопок
        prevButton.disabled = currentFragment === 0;
        nextButton.disabled = currentFragment >= totalFragments - 1;

        // Анимация "пульсации"
        fragmentImageWrapper.classList.remove('pulse');
        void fragmentImageWrapper.offsetWidth; 
        fragmentImageWrapper.classList.add('pulse');
    }

    // --- Работа с API через backend ---
    function safeJsonParse(text) {
        try { return JSON.parse(text); } catch { return null; }
    }

    function startPollingStatus(imageId) {
        cancelPolling();
        pollingTimer = setInterval(async () => {
            try {
                const statusText = await window.backend.get_image(imageId);
                const data = safeJsonParse(statusText);
                if (data && data.processing_status) {
                    if (data.processing_status === 'completed') {
                        cancelPolling();
                        
                        // Сохраняем данные анализа
                        analysisData = data;
                        detectedObjects = data.detected_objects || [];
                        
                        console.log(`Обработка завершена. Объектов найдено: ${detectedObjects.length}`);
                        
                        // Логируем URLs фрагментов
                        detectedObjects.forEach((obj, idx) => {
                            const url = obj.fragment_url || 'отсутствует';
                            console.log(`  Объект ${idx + 1}: ${obj.label} - URL: ${url.substring(0, 60)}...`);
                        });
                        
                        // Если объектов нет, показываем сообщение
                        if (detectedObjects.length === 0) {
                            stopAnalysisAndShowError('Анализ завершен, но объектов не обнаружено');
                            return;
                        }
                        
                        currentFragment = 0;
                        stopAnalysisAndShowResults();
                        
                        // Добавляем метку на карту, если есть координаты
                        if (typeof data.location === 'string' && data.location.includes(',')) {
                            const [latStr, lonStr] = data.location.split(',');
                            const lat = parseFloat(latStr.trim());
                            const lon = parseFloat(lonStr.trim());
                            if (isFinite(lat) && isFinite(lon)) {
                                addMarker(lat, lon, `Изображение #${imageId} (${detectedObjects.length} объектов)`, imageId);
                            }
                        }
                    } else if (data.processing_status === 'failed' || data.error) {
                        cancelPolling();
                        stopAnalysisAndShowError('Обработка не удалась');
                    }
                } else if (data && data.error) {
                    cancelPolling();
                    stopAnalysisAndShowError(`Ошибка: ${data.error}`);
                }
            } catch (e) {
                cancelPolling();
                stopAnalysisAndShowError(`Ошибка статуса: ${e.message || e}`);
            }
        }, 2000);
    }

    function cancelPolling() {
        if (pollingTimer) {
            clearInterval(pollingTimer);
            pollingTimer = null;
        }
    }

    // --- Карта Leaflet ---
    let leafletMap = null;
    let markersLayer = null;
    let allImagesLoaded = false;

    function initMapIfNeeded() {
        if (leafletMap) return;
        
        // Проверяем, что Leaflet загружен
        if (typeof L === 'undefined') {
            console.error('Leaflet не загружен');
            return;
        }
        
        try {
            const mapElement = document.getElementById('map');
            if (!mapElement) {
                console.error('Элемент карты не найден');
                return;
            }
            
            leafletMap = L.map('map', {
                attributionControl: false  // Убираем плашку
            }).setView([55.751244, 37.618423], 11);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: ''
            }).addTo(leafletMap);
            markersLayer = L.layerGroup().addTo(leafletMap);
            
            // Загружаем все изображения при первой инициализации карты
            if (!allImagesLoaded) {
                loadAllImages();
            }
        } catch (e) {
            console.error('Ошибка инициализации карты:', e);
        }
    }
    
    async function loadAllImages() {
        try {
            console.log('Загрузка всех изображений для карты...');
            const response = await window.backend.get_all_images();
            const data = safeJsonParse(response);
            
            if (data && data.success) {
                console.log(`Загружено ${data.with_location} изображений с координатами из ${data.total}`);
                
                // Добавляем метки для каждого изображения
                data.images.forEach((img, index) => {
                    if (img.location && img.location.includes(',')) {
                        const [latStr, lonStr] = img.location.split(',');
                        const lat = parseFloat(latStr.trim());
                        const lon = parseFloat(lonStr.trim());
                        
                        if (isFinite(lat) && isFinite(lon)) {
                            const status = img.processing_status === 'completed' ? '✓' : '⋯';
                            const label = `${status} #${img.id}: ${img.filename}<br>${img.created_at}<br><i>Кликните для просмотра</i>`;
                            addMarker(lat, lon, label, img.id);  // Передаем imageId
                        }
                    }
                });
                
                allImagesLoaded = true;
                fitToMarkersIfAny();
            } else {
                console.error('Ошибка загрузки изображений:', data?.error);
            }
        } catch (e) {
            console.error('Ошибка при загрузке всех изображений:', e);
        }
    }

    function addMarker(lat, lon, label, imageId = null) {
        initMapIfNeeded();
        if (!leafletMap) return;
        
        try {
            const marker = L.marker([lat, lon]).addTo(markersLayer);
            
            // Если есть imageId, добавляем обработчик клика
            if (imageId) {
                marker.bindPopup(label || 'Метка');
                marker.on('click', () => {
                    console.log(`Клик на метку, показываем превью для изображения #${imageId}`);
                    showImagePreview(imageId);
                });
            } else {
                marker.bindPopup(label || 'Метка');
            }
            
            fitToMarkersIfAny();
        } catch (e) {
            console.error('Ошибка добавления маркера:', e);
        }
    }
    
    async function showImagePreview(imageId) {
        try {
            // Сбрасываем состояние модального окна
            imagePreviewModal.style.display = 'flex';
            previewLoader.style.display = 'block';
            previewImage.style.display = 'none';
            previewImage.src = '';  // Очищаем предыдущее изображение
            previewInfo.style.display = 'none';
            openAnalysisButton.style.display = 'none';
            
            console.log(`Загружаем превью для изображения #${imageId}...`);
            
            // Загружаем ТОЛЬКО информацию об изображении (без создания фрагментов)
            // Используем простой GET запрос к API напрямую
            const apiBase = 'http://36.34.82.242:18087';
            const response = await fetch(`${apiBase}/api/v1/images/${imageId}`);
            const data = await response.json();
            
            if (data && !data.error && data.id) {
                // Показываем изображение - используем s3_url напрямую (внутренний URL не сработает, но браузер попробует)
                // Лучше использовать download endpoint
                const downloadResponse = await fetch(`${apiBase}/api/v1/images/${imageId}/download`);
                const downloadData = await downloadResponse.json();
                
                if (downloadData.download_url) {
                    // Заменяем minio:9000 на публичный адрес для браузера
                    let imageUrl = downloadData.download_url;
                    if (imageUrl.includes('minio:9000') || imageUrl.startsWith('http://minio')) {
                        imageUrl = imageUrl.replace('http://minio:9000', 'http://36.34.82.242:17897');
                        imageUrl = imageUrl.replace('minio:9000', '36.34.82.242:17897');
                    }
                    previewImage.src = imageUrl;
                    previewImage.style.display = 'block';
                    console.log(`Превью загружается с: ${imageUrl.substring(0, 80)}...`);
                }
                
                // Показываем информацию
                const objectsCount = data.detected_objects ? data.detected_objects.length : 0;
                const status = data.processing_status === 'completed' ? '✓ Обработано' : '⋯ В процессе';
                
                previewInfo.innerHTML = `
                    <h3>${data.original_filename || 'Изображение'}</h3>
                    <p>ID: ${data.id} | Статус: ${status}</p>
                    <p>Обнаружено объектов: ${objectsCount}</p>
                    <p>Дата: ${data.created_at || 'Неизвестно'}</p>
                `;
                previewInfo.style.display = 'block';
                
                // Показываем кнопку открытия анализа
                openAnalysisButton.style.display = 'inline-block';
                openAnalysisButton.onclick = () => {
                    imagePreviewModal.style.display = 'none';
                    loadImageById(imageId);
                };
                
                previewLoader.style.display = 'none';
            } else {
                previewInfo.innerHTML = '<p style="color: #ff6b6b;">Ошибка загрузки изображения</p>';
                previewInfo.style.display = 'block';
                previewLoader.style.display = 'none';
            }
            
        } catch (e) {
            console.error('Ошибка загрузки превью:', e);
            previewInfo.innerHTML = '<p style="color: #ff6b6b;">Ошибка: ' + e.message + '</p>';
            previewInfo.style.display = 'block';
            previewLoader.style.display = 'none';
        }
    }
    
    // Закрытие модального окна
    closePreviewButton.addEventListener('click', () => {
        imagePreviewModal.style.display = 'none';
    });
    
    // Закрытие по клику вне окна
    imagePreviewModal.addEventListener('click', (e) => {
        if (e.target === imagePreviewModal) {
            imagePreviewModal.style.display = 'none';
        }
    });
    
    async function loadImageById(imageId) {
        try {
            // Показываем шаг анализа с индикацией загрузки
            showStep(analysisStep);
            startAnalysisAnimation();
            
            // Показываем текст загрузки
            responseText.innerHTML = '<div style="text-align: center; padding: 20px;">' +
                '<div style="font-size: 18px; margin-bottom: 10px;">⏳ Загрузка изображения...</div>' +
                '<div style="font-size: 14px; color: #888;">Скачивание с сервера и создание фрагментов</div>' +
                '</div>';
            
            console.log(`Загружаем данные для изображения #${imageId}...`);
            
            // Загружаем данные изображения с сервера
            const statusText = await window.backend.get_image(imageId);
            const data = safeJsonParse(statusText);
            
            if (data && data.error) {
                stopAnalysisAndShowError(`Ошибка: ${data.error}`);
                return;
            }
            
            if (data && data.id) {
                // Устанавливаем ID для текущего изображения
                lastImageId = data.id;
                
                // Устанавливаем главное изображение
                // Используем тот же подход что и в превью - через download endpoint
                try {
                    const apiBase = 'http://36.34.82.242:18087';
                    const downloadResponse = await fetch(`${apiBase}/api/v1/images/${data.id}/download`);
                    const downloadData = await downloadResponse.json();
                    
                    if (downloadData.download_url) {
                        let imageUrl = downloadData.download_url;
                        // Заменяем minio:9000 на публичный адрес
                        if (imageUrl.includes('minio:9000') || imageUrl.startsWith('http://minio')) {
                            imageUrl = imageUrl.replace('http://minio:9000', 'http://36.34.82.242:17897');
                            imageUrl = imageUrl.replace('minio:9000', '36.34.82.242:17897');
                        }
                        mainImageSrc = imageUrl;
                        mainImage.src = imageUrl;
                        mainImage.style.display = 'block';
                        console.log(`Главное изображение загружено: ${imageUrl.substring(0, 80)}...`);
                    }
                } catch (err) {
                    console.error('Ошибка загрузки главного изображения:', err);
                    // Пробуем запасной вариант - s3_url
                    if (data.s3_url) {
                        mainImageSrc = data.s3_url;
                        mainImage.src = mainImageSrc;
                        mainImage.style.display = 'block';
                    }
                }
                
                // Сохраняем данные анализа
                analysisData = data;
                detectedObjects = data.detected_objects || [];
                
                console.log(`Загружено. Объектов найдено: ${detectedObjects.length}`);
                
                if (detectedObjects.length === 0) {
                    stopAnalysisAndShowError('У этого изображения нет обнаруженных объектов');
                    return;
                }
                
                currentFragment = 0;
                stopAnalysisAndShowResults();
            } else {
                stopAnalysisAndShowError('Не удалось загрузить данные изображения');
            }
            
        } catch (e) {
            console.error('Ошибка загрузки изображения:', e);
            stopAnalysisAndShowError(`Ошибка: ${e.message || e}`);
        }
    }

    function fitToMarkersIfAny() {
        if (!leafletMap || !markersLayer) return;
        
        try {
            const layers = markersLayer.getLayers();
            if (layers.length > 0) {
                const group = L.featureGroup(layers);
            leafletMap.fitBounds(group.getBounds().pad(0.2));
            }
        } catch (e) {
            console.error('Ошибка подгонки границ карты:', e);
        }
    }
});