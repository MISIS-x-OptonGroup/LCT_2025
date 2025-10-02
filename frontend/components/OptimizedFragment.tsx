'use client';

import { useState } from 'react';
import { Loader2 } from 'lucide-react';

interface OptimizedFragmentProps {
  imageId: number;
  index: number;
  alt: string;
  className?: string;
}

export default function OptimizedFragment({ imageId, index, alt, className }: OptimizedFragmentProps) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  
  const imageUrl = `/api/fragment/${imageId}/${index}`;

  const handleLoad = () => {
    setLoading(false);
  };

  const handleError = () => {
    setLoading(false);
    setError(true);
  };

  if (error) {
    return (
      <div className={`flex items-center justify-center bg-black/20 ${className}`}>
        <span className="text-white/40 text-sm">Не удалось загрузить</span>
      </div>
    );
  }

  return (
    <div className="relative w-full h-full">
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/20 z-10">
          <Loader2 className="w-6 h-6 animate-spin text-blue-400" />
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

