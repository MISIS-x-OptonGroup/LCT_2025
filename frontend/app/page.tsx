'use client';

import { useState } from 'react';
import { motion } from 'framer-motion';
import { TreeDeciduous } from 'lucide-react';
import Link from 'next/link';
import ImageUpload from '@/components/ImageUpload';

export default function HomePage() {
  const [uploadSuccess, setUploadSuccess] = useState(false);

  const handleUploadSuccess = (imageId: number) => {
    setUploadSuccess(true);
    setTimeout(() => setUploadSuccess(false), 3000);
  };

  return (
    <div className="min-h-screen relative overflow-hidden">
      {/* Background decoration */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-20 -left-20 w-96 h-96 bg-blue-500/20 rounded-full blur-3xl" />
        <div className="absolute bottom-20 -right-20 w-96 h-96 bg-purple-500/20 rounded-full blur-3xl" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-green-500/10 rounded-full blur-3xl" />
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
                <h1 className="text-2xl font-bold">Анализ деревьев</h1>
                <p className="text-sm text-white/60">AI система мониторинга</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Link href="/analyses">
                <button className="flex items-center gap-2 px-6 py-3 rounded-xl glass hover:bg-white/10 transition-all">
                  <TreeDeciduous className="w-5 h-5" />
                  <span className="hidden sm:inline">Все анализы</span>
                </button>
              </Link>
            </div>
          </div>
        </div>
      </header>

      {/* Main content */}
      <main className="relative z-10 container mx-auto px-6 py-12">
        <div className="max-w-4xl mx-auto">
          {/* Hero section */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center mb-12"
          >
            <h2 className="text-4xl md:text-5xl font-bold mb-4 bg-gradient-to-r from-blue-400 via-purple-400 to-green-400 bg-clip-text text-transparent">
              Загрузите изображение дерева
            </h2>
            <p className="text-lg text-white/70">
              Наша AI система проанализирует состояние дерева и предоставит детальный отчет
            </p>
          </motion.div>

          {/* Upload section */}
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.1 }}
            className="flex justify-center"
          >
            <ImageUpload onUploadSuccess={handleUploadSuccess} />
          </motion.div>

          {/* Success message */}
          {uploadSuccess && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="mt-6 p-4 glass rounded-2xl border border-green-500/30 bg-green-500/10 text-center"
            >
              <p className="text-green-400 font-semibold">
                ✓ Изображение успешно загружено! Анализ начался...
              </p>
              <Link href="/analyses">
                <button className="mt-2 text-blue-400 hover:text-blue-300 underline">
                  Посмотреть все анализы
                </button>
              </Link>
            </motion.div>
          )}

          {/* Features */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-16"
          >
            <div className="glass rounded-2xl p-6 text-center">
              <div className="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center mx-auto mb-4">
                <TreeDeciduous className="w-6 h-6 text-blue-400" />
              </div>
              <h3 className="font-semibold mb-2">Определение породы</h3>
              <p className="text-sm text-white/60">
                Автоматическое определение вида дерева
              </p>
            </div>

            <div className="glass rounded-2xl p-6 text-center">
              <div className="w-12 h-12 rounded-xl bg-purple-500/20 flex items-center justify-center mx-auto mb-4">
                <TreeDeciduous className="w-6 h-6 text-purple-400" />
              </div>
              <h3 className="font-semibold mb-2">Анализ состояния</h3>
              <p className="text-sm text-white/60">
                Выявление болезней и повреждений
              </p>
            </div>

            <div className="glass rounded-2xl p-6 text-center">
              <div className="w-12 h-12 rounded-xl bg-green-500/20 flex items-center justify-center mx-auto mb-4">
                <TreeDeciduous className="w-6 h-6 text-green-400" />
              </div>
              <h3 className="font-semibold mb-2">Оценка рисков</h3>
              <p className="text-sm text-white/60">
                Определение уровня опасности
              </p>
            </div>
          </motion.div>
        </div>
      </main>
    </div>
  );
}
