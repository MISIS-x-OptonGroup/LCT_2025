'use client';

import { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Eye, Calendar, TreeDeciduous, X } from 'lucide-react';
import Link from 'next/link';
import type { Image } from '@/lib/api';
import OptimizedImage from './OptimizedImage';

interface MapViewProps {
  images: Image[];
}

// Parse coordinates from location string
function parseCoordinates(location: string): [number, number] | null {
  const patterns = [
    /(-?\d+\.?\d*)[,\s]+(-?\d+\.?\d*)/,
    /lat[:\s]+(-?\d+\.?\d*)[,\s]+lng[:\s]+(-?\d+\.?\d*)/i,
  ];

  for (const pattern of patterns) {
    const match = location.match(pattern);
    if (match) {
      const lat = parseFloat(match[1]);
      const lng = parseFloat(match[2]);
      if (!isNaN(lat) && !isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
        return [lat, lng];
      }
    }
  }
  
  return null;
}

export default function MapView({ images }: MapViewProps) {
  const [mounted, setMounted] = useState(false);
  const [selectedImage, setSelectedImage] = useState<Image | null>(null);
  const [showPreview, setShowPreview] = useState(false);
  const mapRef = useRef<HTMLDivElement>(null);
  const leafletMapRef = useRef<any>(null);
  const markersLayerRef = useRef<any>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!mounted || !mapRef.current) return;
    
    // Проверяем, что Leaflet загружен
    if (typeof window === 'undefined' || !(window as any).L) {
      console.error('Leaflet не загружен');
      return;
    }

    const L = (window as any).L;

    // Если карта уже инициализирована, не создаём заново
    if (leafletMapRef.current) {
      return;
    }

    try {
      // Создаём карту
      const leafletMap = L.map(mapRef.current, {
        attributionControl: false  // Убираем плашку
      }).setView([55.751244, 37.618423], 11);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: ''
      }).addTo(leafletMap);

      const markersLayer = L.layerGroup().addTo(leafletMap);

      leafletMapRef.current = leafletMap;
      markersLayerRef.current = markersLayer;

      console.log('Карта инициализирована');
    } catch (e) {
      console.error('Ошибка инициализации карты:', e);
    }

    // Cleanup при размонтировании
    return () => {
      if (leafletMapRef.current) {
        leafletMapRef.current.remove();
        leafletMapRef.current = null;
        markersLayerRef.current = null;
      }
    };
  }, [mounted]);

  // Добавляем маркеры при изменении images
  useEffect(() => {
    if (!leafletMapRef.current || !markersLayerRef.current || !mounted) return;

    const L = (window as any).L;
    if (!L) return;

    // Очищаем существующие маркеры
    markersLayerRef.current.clearLayers();

    // Фильтруем изображения с координатами
    const imagesWithCoords = images
      .filter(img => img.location)
      .map(img => ({
        ...img,
        coords: parseCoordinates(img.location!),
      }))
      .filter(img => img.coords !== null);

    console.log(`Добавляем ${imagesWithCoords.length} маркеров на карту`);

    // Добавляем метки для каждого изображения
    const markers: any[] = [];
    imagesWithCoords.forEach((img) => {
      if (!img.coords) return;

      const [lat, lon] = img.coords;
      const status = img.processing_status === 'completed' ? '✓' : '⋯';
      const marker = L.marker([lat, lon]).addTo(markersLayerRef.current);

      const popupContent = `
        <div style="min-width: 200px;">
          <strong>${status} #${img.id}</strong><br>
          ${img.original_filename}<br>
          <small>${img.created_at || ''}</small><br>
          <i>Кликните для просмотра</i>
        </div>
      `;
      marker.bindPopup(popupContent);

      // Обработчик клика
      marker.on('click', () => {
        console.log(`Клик на метку, показываем превью для изображения #${img.id}`);
        setSelectedImage(img);
        setShowPreview(true);
      });

      markers.push(marker);
    });

    // Подгоняем границы карты к маркерам
    if (markers.length > 0) {
      try {
        const group = L.featureGroup(markers);
        leafletMapRef.current.fitBounds(group.getBounds().pad(0.2));
      } catch (e) {
        console.error('Ошибка подгонки границ карты:', e);
      }
    }
  }, [images, mounted]);

  // Обновляем размер карты при показе
  useEffect(() => {
    if (leafletMapRef.current && mounted) {
      setTimeout(() => {
        leafletMapRef.current.invalidateSize();
      }, 100);
    }
  }, [mounted]);

  // Закрытие модального окна
  const handleClosePreview = () => {
    setShowPreview(false);
    setSelectedImage(null);
  };

  if (!mounted) {
    return (
      <div className="w-full h-full flex items-center justify-center">
        <div className="animate-pulse text-white/60">Загрузка карты...</div>
      </div>
    );
  }

  const imagesWithCoords = images.filter(img => img.location && parseCoordinates(img.location) !== null);

  return (
    <div className="relative w-full h-full">
      {/* Карта */}
      <div 
        ref={mapRef}
        id="map" 
        style={{ 
          width: '100%', 
          height: '100%', 
          borderRadius: '16px', 
          border: '1px solid rgba(255,255,255,0.1)' 
        }}
      ></div>

      {/* Модальное окно для превью изображения */}
      <AnimatePresence>
        {showPreview && selectedImage && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/90 z-[2000] flex items-center justify-center"
            onClick={handleClosePreview}
          >
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="relative max-w-[90%] max-h-[90%] bg-gray-900/95 backdrop-blur-xl rounded-2xl p-6 shadow-2xl border border-white/10"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Кнопка закрытия */}
              <button
                onClick={handleClosePreview}
                className="absolute top-4 right-4 w-10 h-10 bg-white/10 hover:bg-white/20 rounded-full flex items-center justify-center transition-colors z-10"
              >
                <X className="w-5 h-5 text-white" />
              </button>

              <div className="flex flex-col items-center gap-6">
                {/* Превью изображения */}
                <div className="relative max-w-[70vh] max-h-[60vh] rounded-xl overflow-hidden border border-white/10">
                  <OptimizedImage
                    imageId={selectedImage.id}
                    alt={selectedImage.original_filename}
                    className="max-w-full max-h-[60vh] object-contain"
                  />
                </div>

                {/* Информация об изображении */}
                <div className="text-center text-white space-y-3 w-full max-w-md">
                  <div className="flex items-center justify-center gap-2">
                    <TreeDeciduous className="w-5 h-5 text-green-400" />
                    <h3 className="text-xl font-semibold">{selectedImage.original_filename}</h3>
                  </div>
                  
                  <div className="flex items-center justify-center gap-4 text-sm text-white/70">
                    <div className="flex items-center gap-1">
                      <span>ID: {selectedImage.id}</span>
                    </div>
                    {selectedImage.taken_at && (
                      <>
                        <span>|</span>
                        <div className="flex items-center gap-1">
                          <Calendar className="w-4 h-4" />
                          <span>{new Date(selectedImage.taken_at).toLocaleDateString('ru-RU')}</span>
                        </div>
                      </>
                    )}
                  </div>

                  {selectedImage.detected_objects && selectedImage.detected_objects.length > 0 && (
                    <p className="text-sm text-white/80">
                      Обнаружено объектов: <span className="font-semibold text-green-400">{selectedImage.detected_objects.length}</span>
                    </p>
                  )}

                  <div className="text-xs text-white/60">
                    Статус: {selectedImage.processing_status === 'completed' ? '✓ Обработано' : '⋯ В процессе'}
                  </div>

                  {/* Кнопка открытия анализа */}
                  <Link href={`/image/${selectedImage.id}`}>
                    <button 
                      className="w-full mt-4 flex items-center justify-center gap-2 px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 text-white rounded-xl text-base font-semibold transition-all shadow-lg hover:shadow-xl"
                      onClick={handleClosePreview}
                    >
                      <Eye className="w-5 h-5" />
                      Открыть анализ
                    </button>
                  </Link>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {imagesWithCoords.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="glass rounded-2xl p-8 text-center"
          >
            <p className="text-white/80">
              Нет изображений с геолокацией
            </p>
            <p className="text-sm text-white/60 mt-2">
              Загрузите изображения с координатами для отображения на карте
            </p>
          </motion.div>
        </div>
      )}

      <div className="absolute top-4 left-4 glass rounded-xl p-4 z-[1000]">
        <div className="text-sm">
          <div className="font-semibold mb-1">Статистика</div>
          <div className="text-white/70">
            Всего меток: <span className="text-white font-semibold">{imagesWithCoords.length}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
