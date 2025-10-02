'use client';

import { motion } from 'framer-motion';
import { Loader2, TreeDeciduous } from 'lucide-react';

interface LoadingAnimationProps {
  message?: string;
}

export default function LoadingAnimation({ message = 'Анализируем изображение...' }: LoadingAnimationProps) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-md"
    >
      <div className="glass rounded-3xl p-12 max-w-md text-center">
        <motion.div
          animate={{
            scale: [1, 1.2, 1],
            rotate: [0, 180, 360],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: "easeInOut",
          }}
          className="mb-6 inline-block"
        >
          <div className="p-6 bg-gradient-to-br from-blue-500 to-purple-500 rounded-full">
            <TreeDeciduous className="w-12 h-12" />
          </div>
        </motion.div>

        <h3 className="text-2xl font-bold mb-4">{message}</h3>

        <div className="space-y-3 text-sm text-white/70">
          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.2 }}
            className="flex items-center gap-3"
          >
            <motion.div
              animate={{ scale: [1, 1.5, 1] }}
              transition={{ duration: 1, repeat: Infinity, delay: 0 }}
              className="w-2 h-2 rounded-full bg-blue-400"
            />
            <span>Обнаружение объектов...</span>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.4 }}
            className="flex items-center gap-3"
          >
            <motion.div
              animate={{ scale: [1, 1.5, 1] }}
              transition={{ duration: 1, repeat: Infinity, delay: 0.3 }}
              className="w-2 h-2 rounded-full bg-purple-400"
            />
            <span>Анализ состояния...</span>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.6 }}
            className="flex items-center gap-3"
          >
            <motion.div
              animate={{ scale: [1, 1.5, 1] }}
              transition={{ duration: 1, repeat: Infinity, delay: 0.6 }}
              className="w-2 h-2 rounded-full bg-green-400"
            />
            <span>Генерация отчета...</span>
          </motion.div>
        </div>

        <motion.div
          animate={{ opacity: [0.5, 1, 0.5] }}
          transition={{ duration: 2, repeat: Infinity }}
          className="mt-8 text-xs text-white/50"
        >
          Это может занять несколько секунд...
        </motion.div>
      </div>
    </motion.div>
  );
}

