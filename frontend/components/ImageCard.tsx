'use client';

import { motion } from 'framer-motion';
import { Calendar, MapPin, User, Eye } from 'lucide-react';
import type { Image } from '@/lib/api';
import Link from 'next/link';
import OptimizedImage from './OptimizedImage';

interface ImageCardProps {
  image: Image;
}

export default function ImageCard({ image }: ImageCardProps) {
  const statusColors = {
    uploaded: 'bg-yellow-500',
    processing: 'bg-blue-500',
    completed: 'bg-green-500',
    error: 'bg-red-500',
  };

  const statusLabels = {
    uploaded: 'Загружено',
    processing: 'Обработка',
    completed: 'Завершено',
    error: 'Ошибка',
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      whileHover={{ scale: 1.02, y: -4 }}
      transition={{ duration: 0.2 }}
      className="glass rounded-2xl overflow-hidden group"
    >
      <div className="relative h-48 overflow-hidden">
        <OptimizedImage 
          imageId={image.id}
          alt={image.original_filename}
          className="w-full h-full object-cover"
        />
        
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />
        
        <div className="absolute top-3 left-3 z-20">
          <span
            className={`
              px-3 py-1 rounded-full text-xs font-semibold
              ${statusColors[image.processing_status as keyof typeof statusColors] || 'bg-gray-500'}
              backdrop-blur-sm
            `}
          >
            {statusLabels[image.processing_status as keyof typeof statusLabels] || image.processing_status}
          </span>
        </div>

        <div className="absolute top-3 right-3 z-20 opacity-0 group-hover:opacity-100 transition-opacity">
          <Link href={`/image/${image.id}`}>
            <button
              className="p-2 bg-blue-500/80 hover:bg-blue-500 rounded-full backdrop-blur-sm transition-colors"
            >
              <Eye className="w-4 h-4" />
            </button>
          </Link>
        </div>
      </div>

      <div className="p-5 space-y-3">
        <h3 className="font-semibold text-lg truncate">
          {image.original_filename}
        </h3>

        <div className="space-y-2 text-sm text-white/70">
          {image.taken_at && (
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-blue-400" />
              <span>{new Date(image.taken_at).toLocaleDateString('ru-RU')}</span>
            </div>
          )}
          
          {image.location && (
            <div className="flex items-center gap-2">
              <MapPin className="w-4 h-4 text-green-400" />
              <span className="truncate">{image.location}</span>
            </div>
          )}
          
          {image.author && (
            <div className="flex items-center gap-2">
              <User className="w-4 h-4 text-purple-400" />
              <span className="truncate">{image.author}</span>
            </div>
          )}
        </div>

        {image.detected_objects && image.detected_objects.length > 0 && (
          <div className="pt-3 border-t border-white/10">
            <p className="text-xs text-white/60">
              Обнаружено объектов: <span className="text-white font-semibold">{image.detected_objects.length}</span>
            </p>
          </div>
        )}
      </div>
    </motion.div>
  );
}
