'use client';

import { useState } from 'react';
import { Loader2 } from 'lucide-react';

interface OptimizedImageProps {
  imageId: number;
  alt: string;
  className?: string;
  fallback?: React.ReactNode;
}

export default function OptimizedImage({ imageId, alt, className, fallback }: OptimizedImageProps) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  
  const imageUrl = `/api/image/${imageId}`;

  const handleLoad = () => {
    setLoading(false);
  };

  const handleError = () => {
    setLoading(false);
    setError(true);
  };

  if (error) {
    return fallback || (
      <div className={`flex items-center justify-center bg-gradient-to-br from-slate-800 to-slate-900 ${className}`}>
        <span className="text-white/40">Не удалось загрузить</span>
      </div>
    );
  }

  return (
    <div className="relative w-full h-full">
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-slate-800 to-slate-900 z-10">
          <Loader2 className="w-8 h-8 animate-spin text-blue-400" />
        </div>
      )}
      <img 
        src={imageUrl} 
        alt={alt}
        className={`${className} ${loading ? 'opacity-0' : 'opacity-100'} transition-opacity duration-300`}
        onLoad={handleLoad}
        onError={handleError}
      />
    </div>
  );
}

