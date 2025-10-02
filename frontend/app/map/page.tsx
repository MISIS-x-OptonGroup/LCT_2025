'use client';

import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Home, Map as MapIcon, X } from 'lucide-react';
import Link from 'next/link';

export default function MapPage() {
  const mapRef = useRef<HTMLDivElement>(null);
  const leafletMapRef = useRef<any>(null);
  const markersLayerRef = useRef<any>(null);
  const [mounted, setMounted] = useState(false);
  
  // Модальное окно для превью
  const [showPreview, setShowPreview] = useState(false);
  const [previewData, setPreviewData] = useState<any>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

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

      // Загружаем все изображения
      loadAllImages();
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

  const loadAllImages = async () => {
    try {
      console.log('Загрузка всех изображений для карты...');
      const response = await fetch('/api/images');
      const data = await response.json();

      if (data && data.images) {
        console.log(`Загружено ${data.images.length} изображений`);

        const L = (window as any).L;
        if (!markersLayerRef.current || !L) return;

        // Добавляем метки для каждого изображения
        const markers: any[] = [];
        data.images.forEach((img: any) => {
          if (img.location && img.location.includes(',')) {
            const [latStr, lonStr] = img.location.split(',');
            const lat = parseFloat(latStr.trim());
            const lon = parseFloat(lonStr.trim());

            if (isFinite(lat) && isFinite(lon)) {
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
                showImagePreview(img.id);
              });

              markers.push(marker);
            }
          }
        });

        // Подгоняем границы карты к маркерам
        if (markers.length > 0 && leafletMapRef.current) {
          const group = L.featureGroup(markers);
          leafletMapRef.current.fitBounds(group.getBounds().pad(0.2));
        }
      }
    } catch (e) {
      console.error('Ошибка при загрузке всех изображений:', e);
    }
  };

  const showImagePreview = async (imageId: number) => {
    setShowPreview(true);
    setPreviewLoading(true);
    setPreviewData(null);

    try {
      console.log(`Загружаем превью для изображения #${imageId}...`);

      // Загружаем информацию об изображении
      const response = await fetch(`/api/images/${imageId}`);
      const data = await response.json();

      if (data && !data.error && data.id) {
        setPreviewData(data);
      } else {
        setPreviewData({ error: 'Ошибка загрузки изображения' });
      }
    } catch (e: any) {
      console.error('Ошибка загрузки превью:', e);
      setPreviewData({ error: e.message || 'Ошибка загрузки' });
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleClosePreview = () => {
    setShowPreview(false);
    setPreviewData(null);
  };

  if (!mounted) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-pulse text-white/60">Загрузка...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header */}
      <header className="glass-dark border-b border-white/10 z-20">
        <div className="container mx-auto px-6 py-6">
          <div className="flex items-center justify-between">
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              className="flex items-center gap-3"
            >
              <div className="p-3 bg-gradient-to-br from-green-500 to-blue-500 rounded-2xl">
                <MapIcon className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-2xl font-bold">Карта локаций</h1>
                <p className="text-sm text-white/60">Геолокация проанализированных деревьев</p>
              </div>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
            >
              <Link href="/">
                <button className="flex items-center gap-2 px-6 py-3 rounded-xl glass hover:bg-white/10 transition-all">
                  <Home className="w-5 h-5" />
                  <span className="hidden sm:inline">Главная</span>
                </button>
              </Link>
            </motion.div>
          </div>
        </div>
      </header>

      {/* Map */}
      <main className="flex-1 relative overflow-hidden">
        {/* Background decoration */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-20 -left-20 w-96 h-96 bg-green-500/10 rounded-full blur-3xl" />
          <div className="absolute bottom-20 -right-20 w-96 h-96 bg-blue-500/10 rounded-full blur-3xl" />
        </div>

        <div className="relative z-10 h-full p-6">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.2 }}
            className="h-full"
          >
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
          </motion.div>
        </div>
      </main>

      {/* Модальное окно для превью изображения */}
      {showPreview && (
        <div 
          style={{ 
            display: 'flex', 
            position: 'fixed', 
            top: 0, 
            left: 0, 
            width: '100%', 
            height: '100%', 
            background: 'rgba(0,0,0,0.9)', 
            zIndex: 1000, 
            alignItems: 'center', 
            justifyContent: 'center' 
          }}
          onClick={handleClosePreview}
        >
          <div 
            style={{ 
              maxWidth: '90%', 
              maxHeight: '90%', 
              background: '#1a1a1a', 
              borderRadius: '16px', 
              padding: '20px', 
              position: 'relative' 
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <button 
              onClick={handleClosePreview}
              style={{ 
                position: 'absolute', 
                top: '10px', 
                right: '10px', 
                background: 'rgba(255,255,255,0.1)', 
                border: 'none', 
                color: 'white', 
                width: '40px', 
                height: '40px', 
                borderRadius: '50%', 
                cursor: 'pointer', 
                fontSize: '20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <X className="w-5 h-5" />
            </button>
            <div style={{ textAlign: 'center' }}>
              {previewLoading && (
                <div style={{ padding: '40px', fontSize: '18px', color: '#888' }}>
                  ⏳ Загрузка...
                </div>
              )}
              
              {!previewLoading && previewData && !previewData.error && (
                <>
                  <img 
                    src={`/api/image/${previewData.id}`}
                    style={{ 
                      maxWidth: '100%', 
                      maxHeight: '70vh', 
                      borderRadius: '8px', 
                      marginBottom: '20px' 
                    }} 
                    alt="Preview" 
                  />
                  <div style={{ color: 'white', marginBottom: '20px' }}>
                    <h3>{previewData.original_filename || 'Изображение'}</h3>
                    <p>ID: {previewData.id} | Статус: {previewData.processing_status === 'completed' ? '✓ Обработано' : '⋯ В процессе'}</p>
                    <p>Обнаружено объектов: {previewData.detected_objects?.length || 0}</p>
                    <p>Дата: {previewData.created_at || 'Неизвестно'}</p>
                  </div>
                  <Link href={`/image/${previewData.id}`}>
                    <button 
                      style={{ 
                        padding: '12px 24px', 
                        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', 
                        color: 'white', 
                        border: 'none', 
                        borderRadius: '8px', 
                        cursor: 'pointer', 
                        fontSize: '16px' 
                      }}
                    >
                      Открыть анализ
                    </button>
                  </Link>
                </>
              )}

              {!previewLoading && previewData?.error && (
                <div style={{ padding: '40px', color: '#ff6b6b' }}>
                  {previewData.error}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
