'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Home, TreeDeciduous, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import Link from 'next/link';
import { getImages, type Image } from '@/lib/api';
import ImageCard from '@/components/ImageCard';

export default function AnalysesPage() {
  const [images, setImages] = useState<Image[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const itemsPerPage = 20;

  useEffect(() => {
    loadImages();
  }, [currentPage]);

  const loadImages = async () => {
    try {
      setLoading(true);
      const skip = (currentPage - 1) * itemsPerPage;
      const data = await getImages(skip, itemsPerPage);
      setImages(data);
      setHasMore(data.length === itemsPerPage);
    } catch (error) {
      console.error('Failed to load images:', error);
      setImages([]);
    } finally {
      setLoading(false);
    }
  };

  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleNextPage = () => {
    if (hasMore) {
      setCurrentPage(currentPage + 1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

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
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-gradient-to-br from-green-500 to-blue-500">
                <TreeDeciduous className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-2xl font-bold">Все анализы</h1>
                <p className="text-sm text-white/60">История обработанных изображений</p>
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
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Loader2 className="w-12 h-12 animate-spin text-blue-400 mb-4" />
            <p className="text-white/60">Загрузка анализов...</p>
          </div>
        ) : images.length > 0 ? (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6"
            >
              {images.map((image, index) => (
                <motion.div
                  key={image.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.03 }}
                >
                  <ImageCard image={image} />
                </motion.div>
              ))}
            </motion.div>

            {/* Pagination */}
            <div className="mt-12 flex items-center justify-center gap-4">
              <button
                onClick={handlePrevPage}
                disabled={currentPage === 1}
                className="
                  flex items-center gap-2 px-6 py-3 rounded-xl
                  glass hover:bg-white/10 transition-all
                  disabled:opacity-50 disabled:cursor-not-allowed
                "
              >
                <ChevronLeft className="w-5 h-5" />
                <span>Назад</span>
              </button>

              <div className="glass px-6 py-3 rounded-xl">
                <span className="font-semibold">Страница {currentPage}</span>
              </div>

              <button
                onClick={handleNextPage}
                disabled={!hasMore}
                className="
                  flex items-center gap-2 px-6 py-3 rounded-xl
                  glass hover:bg-white/10 transition-all
                  disabled:opacity-50 disabled:cursor-not-allowed
                "
              >
                <span>Вперёд</span>
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>
          </>
        ) : (
          <div className="glass rounded-2xl p-12 text-center">
            <TreeDeciduous className="w-16 h-16 text-white/40 mx-auto mb-4" />
            <p className="text-xl text-white/60 mb-4">Пока нет загруженных изображений</p>
            <Link href="/">
              <button className="px-6 py-3 rounded-xl bg-gradient-to-r from-blue-500 to-purple-500 hover:from-blue-600 hover:to-purple-600 transition-all">
                Загрузить первое изображение
              </button>
            </Link>
          </div>
        )}
      </main>
    </div>
  );
}

