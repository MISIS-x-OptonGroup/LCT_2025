'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Home,
  Calendar,
  MapPin,
  User,
  FileImage,
  Loader2,
  ArrowLeft,
  TreeDeciduous,
  AlertTriangle,
  CheckCircle,
  Info,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import Link from 'next/link';
import { getImage, type Image } from '@/lib/api';
import OptimizedImage from '@/components/OptimizedImage';
import OptimizedFragment from '@/components/OptimizedFragment';

export default function ImageDetailPage() {
  const params = useParams();
  const router = useRouter();
  const [image, setImage] = useState<Image | null>(null);
  const [loading, setLoading] = useState(true);
  const [expandedObject, setExpandedObject] = useState<number | null>(null);

  useEffect(() => {
    if (params.id) {
      loadImage(parseInt(params.id as string));
    }
  }, [params.id]);

  const loadImage = async (id: number) => {
    try {
      setLoading(true);
      const data = await getImage(id);
      setImage(data);
    } catch (error) {
      console.error('Failed to load image:', error);
      alert('Ошибка при загрузке изображения');
      router.push('/');
    } finally {
      setLoading(false);
    }
  };

  const toggleObject = (index: number) => {
    setExpandedObject(expandedObject === index ? null : index);
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <Loader2 className="w-12 h-12 animate-spin text-blue-400" />
          <p className="text-white/60">Загрузка...</p>
        </div>
      </div>
    );
  }

  if (!image) return null;

  return (
    <div className="min-h-screen relative overflow-hidden">
      {/* Background */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-20 -left-20 w-96 h-96 bg-blue-500/20 rounded-full blur-3xl" />
        <div className="absolute bottom-20 -right-20 w-96 h-96 bg-purple-500/20 rounded-full blur-3xl" />
      </div>

      {/* Header */}
      <header className="relative z-10 glass-dark border-b border-white/10">
        <div className="container mx-auto px-6 py-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <Link href="/">
                <button className="p-2 rounded-xl glass hover:bg-white/10 transition-all">
                  <ArrowLeft className="w-5 h-5" />
                </button>
              </Link>
              <div>
                <h1 className="text-2xl font-bold">Детали анализа</h1>
                <p className="text-sm text-white/60">{image.original_filename}</p>
              </div>
            </div>

            <Link href="/">
              <button className="flex items-center gap-2 px-6 py-3 rounded-xl glass hover:bg-white/10 transition-all">
                <Home className="w-5 h-5" />
                <span className="hidden sm:inline">Главная</span>
              </button>
            </Link>
          </div>
        </div>
      </header>

      {/* Main content */}
      <main className="relative z-10 container mx-auto px-6 py-12">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left column - Image */}
          <div className="lg:col-span-2 space-y-6">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="glass rounded-3xl overflow-hidden"
            >
              <OptimizedImage 
                imageId={image.id}
                alt={image.original_filename}
                className="w-full h-96 object-cover"
              />
            </motion.div>

            {/* Detected Objects */}
            {image.detected_objects && image.detected_objects.length > 0 && (
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1 }}
                className="glass rounded-3xl p-8"
              >
                <h2 className="text-2xl font-bold mb-6 flex items-center gap-3">
                  <TreeDeciduous className="w-7 h-7 text-green-400" />
                  Обнаруженные объекты
                </h2>

                <div className="space-y-4">
                  {image.detected_objects.map((obj, index) => (
                    <motion.div
                      key={index}
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: index * 0.05 }}
                      className="glass-dark rounded-2xl overflow-hidden"
                    >
                      <div
                        onClick={() => toggleObject(index)}
                        className="p-6 cursor-pointer hover:bg-white/5 transition-all"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex-1">
                            <div className="flex items-center justify-between mb-2">
                              <h3 className="font-semibold text-lg">{obj.label}</h3>
                              <span className="text-sm text-white/60">
                                {Math.round(obj.confidence * 100)}%
                              </span>
                            </div>

                            {obj.description?.object && (
                              <div className="flex items-center gap-4 text-sm">
                                {obj.description.object.species && (
                                  <span className="text-white/80">
                                    {obj.description.object.species.label_ru}
                                  </span>
                                )}

                                {obj.description.object.risk && (
                                  <div className="flex items-center gap-2">
                                    {obj.description.object.risk.level === 'low' && (
                                      <CheckCircle className="w-4 h-4 text-green-400" />
                                    )}
                                    {obj.description.object.risk.level === 'medium' && (
                                      <Info className="w-4 h-4 text-yellow-400" />
                                    )}
                                    {(obj.description.object.risk.level === 'high' || 
                                      obj.description.object.risk.level === 'critical') && (
                                      <AlertTriangle className="w-4 h-4 text-red-400" />
                                    )}
                                    <span className="text-white/60 capitalize">
                                      {obj.description.object.risk.level}
                                    </span>
                                  </div>
                                )}
                              </div>
                            )}
                          </div>

                          {expandedObject === index ? (
                            <ChevronUp className="w-5 h-5 text-white/60 ml-4" />
                          ) : (
                            <ChevronDown className="w-5 h-5 text-white/60 ml-4" />
                          )}
                        </div>
                      </div>

                      <AnimatePresence>
                        {expandedObject === index && (
                          <motion.div
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: 'auto', opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            transition={{ duration: 0.3 }}
                            className="overflow-hidden"
                          >
                            <div className="px-6 pb-6 space-y-4 border-t border-white/10 pt-4">
                              {/* Фото фрагмента */}
                              <div className="rounded-xl overflow-hidden bg-black/20">
                                <OptimizedFragment
                                  imageId={image.id}
                                  index={index}
                                  alt={`Фрагмент ${index + 1}`}
                                  className="w-full h-48 object-contain"
                                />
                              </div>

                              {/* Полное описание */}
                              {obj.description && (
                                <div className="space-y-3 text-sm">
                                  {/* Информация о сезоне */}
                                  {obj.description.scene && (
                                    <div className="p-3 glass rounded-lg">
                                      <div className="text-white/60 mb-1">Сезон</div>
                                      <div className="text-white capitalize">
                                        {obj.description.scene.season_inferred}
                                      </div>
                                      {obj.description.scene.note && (
                                        <div className="text-white/50 text-xs mt-1">
                                          {obj.description.scene.note}
                                        </div>
                                      )}
                                    </div>
                                  )}

                                  {/* Информация о породе */}
                                  {obj.description.object?.species && (
                                    <div className="p-3 glass rounded-lg">
                                      <div className="text-white/60 mb-1">Порода</div>
                                      <div className="text-white">
                                        {obj.description.object.species.label_ru}
                                      </div>
                                      <div className="text-white/50 text-xs mt-1">
                                        Уверенность: {obj.description.object.species.confidence}%
                                      </div>
                                    </div>
                                  )}

                                  {/* Состояние */}
                                  {obj.description.object?.condition && (
                                    <div className="p-3 glass rounded-lg">
                                      <div className="text-white/60 mb-2">Состояние</div>
                                      <div className="space-y-2 text-xs">
                                        {obj.description.object.condition.dry_branches_pct > 0 && (
                                          <div className="flex justify-between">
                                            <span className="text-white/70">Сухие ветви:</span>
                                            <span className="text-yellow-400">{obj.description.object.condition.dry_branches_pct}%</span>
                                          </div>
                                        )}
                                        
                                        {obj.description.object.condition.trunk_decay?.present && (
                                          <div className="text-orange-400">⚠ Гниение ствола</div>
                                        )}
                                        
                                        {obj.description.object.condition.cavities?.present && (
                                          <div className="text-orange-400">⚠ Дупла обнаружены</div>
                                        )}
                                        
                                        {obj.description.object.condition.cracks?.present && (
                                          <div className="text-orange-400">⚠ Трещины</div>
                                        )}
                                        
                                        {obj.description.object.condition.fruiting_bodies?.present && (
                                          <div className="text-red-400">⚠ Плодовые тела грибов</div>
                                        )}
                                      </div>
                                    </div>
                                  )}

                                  {/* Болезни */}
                                  {obj.description.object?.condition?.diseases && 
                                   obj.description.object.condition.diseases.length > 0 && (
                                    <div className="p-3 glass rounded-lg">
                                      <div className="text-white/60 mb-2">Возможные болезни</div>
                                      <div className="space-y-1 text-xs">
                                        {obj.description.object.condition.diseases.map((disease: any, i: number) => (
                                          <div key={i} className="flex justify-between items-start">
                                            <span className="text-white/80">{disease.name_ru}</span>
                                            <span className="text-red-400 ml-2">{disease.likelihood}%</span>
                                          </div>
                                        ))}
                                      </div>
                                    </div>
                                  )}

                                  {/* Вредители */}
                                  {obj.description.object?.condition?.pests && 
                                   obj.description.object.condition.pests.length > 0 && (
                                    <div className="p-3 glass rounded-lg">
                                      <div className="text-white/60 mb-2">Возможные вредители</div>
                                      <div className="space-y-1 text-xs">
                                        {obj.description.object.condition.pests.map((pest: any, i: number) => (
                                          <div key={i} className="flex justify-between items-start">
                                            <span className="text-white/80">{pest.name_ru}</span>
                                            <span className="text-orange-400 ml-2">{pest.likelihood}%</span>
                                          </div>
                                        ))}
                                      </div>
                                    </div>
                                  )}

                                  {/* Уровень риска */}
                                  {obj.description.object?.risk && (
                                    <div className={`p-3 rounded-lg border ${
                                      obj.description.object.risk.level === 'low' ? 'bg-green-500/10 border-green-500/30' :
                                      obj.description.object.risk.level === 'medium' ? 'bg-yellow-500/10 border-yellow-500/30' :
                                      'bg-red-500/10 border-red-500/30'
                                    }`}>
                                      <div className="text-white/60 mb-1">Уровень риска</div>
                                      <div className="text-white font-semibold capitalize mb-2">
                                        {obj.description.object.risk.level}
                                      </div>
                                      {obj.description.object.risk.drivers && 
                                       obj.description.object.risk.drivers.length > 0 && (
                                        <div className="text-xs text-white/70">
                                          {obj.description.object.risk.drivers.join(', ')}
                                        </div>
                                      )}
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </motion.div>
                  ))}
                </div>
              </motion.div>
            )}
          </div>

          {/* Right column - Metadata */}
          <div className="space-y-6">
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              className="glass rounded-3xl p-8 space-y-6"
            >
              <h2 className="text-xl font-bold mb-4">Информация</h2>

              <div className="space-y-4 text-sm">
                {image.taken_at && (
                  <div className="flex items-start gap-3">
                    <Calendar className="w-5 h-5 text-blue-400 mt-0.5" />
                    <div>
                      <p className="text-white/60">Дата съемки</p>
                      <p className="text-white">
                        {new Date(image.taken_at).toLocaleDateString('ru-RU', {
                          year: 'numeric',
                          month: 'long',
                          day: 'numeric',
                        })}
                      </p>
                    </div>
                  </div>
                )}

                {image.location && (
                  <div className="flex items-start gap-3">
                    <MapPin className="w-5 h-5 text-green-400 mt-0.5" />
                    <div>
                      <p className="text-white/60">Локация</p>
                      <p className="text-white">{image.location}</p>
                    </div>
                  </div>
                )}

                {image.author && (
                  <div className="flex items-start gap-3">
                    <User className="w-5 h-5 text-purple-400 mt-0.5" />
                    <div>
                      <p className="text-white/60">Автор</p>
                      <p className="text-white">{image.author}</p>
                    </div>
                  </div>
                )}

                <div className="flex items-start gap-3">
                  <FileImage className="w-5 h-5 text-orange-400 mt-0.5" />
                  <div>
                    <p className="text-white/60">Размер файла</p>
                    <p className="text-white">
                      {(image.file_size / 1024 / 1024).toFixed(2)} MB
                    </p>
                  </div>
                </div>

                {image.width && image.height && (
                  <div className="flex items-start gap-3">
                    <FileImage className="w-5 h-5 text-pink-400 mt-0.5" />
                    <div>
                      <p className="text-white/60">Разрешение</p>
                      <p className="text-white">
                        {image.width} × {image.height}
                      </p>
                    </div>
                  </div>
                )}
              </div>
            </motion.div>

            {/* Processing Status */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.1 }}
              className="glass rounded-3xl p-8"
            >
              <h3 className="font-semibold mb-4">Статус обработки</h3>
              <div className="flex items-center gap-3">
                {image.processing_status === 'completed' ? (
                  <>
                    <CheckCircle className="w-5 h-5 text-green-400" />
                    <span className="text-green-400">Завершено</span>
                  </>
                ) : image.processing_status === 'processing' ? (
                  <>
                    <Loader2 className="w-5 h-5 text-blue-400 animate-spin" />
                    <span className="text-blue-400">Обработка...</span>
                  </>
                ) : (
                  <>
                    <Info className="w-5 h-5 text-yellow-400" />
                    <span className="text-yellow-400 capitalize">{image.processing_status}</span>
                  </>
                )}
              </div>
            </motion.div>
          </div>
        </div>
      </main>
    </div>
  );
}
