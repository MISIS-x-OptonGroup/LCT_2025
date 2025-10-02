'use client';

import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle, XCircle, Info, AlertTriangle, X } from 'lucide-react';
import { useEffect } from 'react';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

interface ToastProps {
  message: string;
  type: ToastType;
  onClose: () => void;
  duration?: number;
}

export default function Toast({ message, type, onClose, duration = 3000 }: ToastProps) {
  useEffect(() => {
    if (duration > 0) {
      const timer = setTimeout(onClose, duration);
      return () => clearTimeout(timer);
    }
  }, [duration, onClose]);

  const icons = {
    success: <CheckCircle className="w-6 h-6 text-green-400" />,
    error: <XCircle className="w-6 h-6 text-red-400" />,
    info: <Info className="w-6 h-6 text-blue-400" />,
    warning: <AlertTriangle className="w-6 h-6 text-yellow-400" />,
  };

  const colors = {
    success: 'from-green-500/20 to-green-600/20 border-green-400/30',
    error: 'from-red-500/20 to-red-600/20 border-red-400/30',
    info: 'from-blue-500/20 to-blue-600/20 border-blue-400/30',
    warning: 'from-yellow-500/20 to-yellow-600/20 border-yellow-400/30',
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: -50, scale: 0.9 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -20, scale: 0.9 }}
      className={`
        glass border rounded-2xl p-4 min-w-[300px] max-w-md
        bg-gradient-to-br backdrop-blur-xl
        ${colors[type]}
      `}
    >
      <div className="flex items-center gap-3">
        {icons[type]}
        <p className="flex-1 text-sm font-medium">{message}</p>
        <button
          onClick={onClose}
          className="p-1 hover:bg-white/10 rounded-lg transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </motion.div>
  );
}

interface ToastContainerProps {
  toasts: Array<{ id: string; message: string; type: ToastType }>;
  onClose: (id: string) => void;
}

export function ToastContainer({ toasts, onClose }: ToastContainerProps) {
  return (
    <div className="fixed top-6 right-6 z-50 flex flex-col gap-3">
      <AnimatePresence>
        {toasts.map((toast) => (
          <Toast
            key={toast.id}
            message={toast.message}
            type={toast.type}
            onClose={() => onClose(toast.id)}
          />
        ))}
      </AnimatePresence>
    </div>
  );
}

