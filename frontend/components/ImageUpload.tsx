'use client';

import { useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Upload, X, Loader2, Image as ImageIcon } from 'lucide-react';
import { uploadImage } from '@/lib/api';

interface ImageUploadProps {
  onUploadSuccess?: (imageId: number) => void;
}

export default function ImageUpload({ onUploadSuccess }: ImageUploadProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    
    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile && droppedFile.type.startsWith('image/')) {
      setFile(droppedFile);
      const reader = new FileReader();
      reader.onload = () => setPreview(reader.result as string);
      reader.readAsDataURL(droppedFile);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      setFile(selectedFile);
      const reader = new FileReader();
      reader.onload = () => setPreview(reader.result as string);
      reader.readAsDataURL(selectedFile);
    }
  };

  const handleUpload = async () => {
    if (!file) return;

    setUploading(true);
    setProgress(0);

    // Simulate progress
    const progressInterval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 90) {
          clearInterval(progressInterval);
          return 90;
        }
        return prev + 10;
      });
    }, 200);

    try {
      const result = await uploadImage(file);
      setProgress(100);
      setTimeout(() => {
        onUploadSuccess?.(result.id);
        resetUpload();
      }, 500);
    } catch (error) {
      console.error('Upload failed:', error);
      alert('Ошибка при загрузке изображения');
    } finally {
      clearInterval(progressInterval);
      setUploading(false);
    }
  };

  const resetUpload = () => {
    setFile(null);
    setPreview(null);
    setProgress(0);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="glass rounded-3xl p-8 w-full max-w-2xl mx-auto"
    >
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileSelect}
        className="hidden"
      />

      <AnimatePresence mode="wait">
        {!preview ? (
          <motion.div
            key="upload"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`
              relative border-2 border-dashed rounded-2xl p-12 text-center cursor-pointer
              transition-all duration-300
              ${isDragging 
                ? 'border-blue-400 bg-blue-500/10 scale-105' 
                : 'border-white/20 hover:border-white/40 hover:bg-white/5'
              }
            `}
          >
            <motion.div
              animate={isDragging ? { scale: 1.1 } : { scale: 1 }}
              transition={{ duration: 0.2 }}
            >
              <Upload className="w-16 h-16 mx-auto mb-4 text-blue-400" />
              <h3 className="text-xl font-semibold mb-2">
                Загрузите изображение
              </h3>
              <p className="text-sm text-white/60">
                Перетащите файл сюда или нажмите для выбора
              </p>
            </motion.div>
          </motion.div>
        ) : (
          <motion.div
            key="preview"
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            className="space-y-4"
          >
            <div className="relative rounded-2xl overflow-hidden">
              <img
                src={preview}
                alt="Preview"
                className="w-full h-64 object-cover"
              />
              {!uploading && (
                <button
                  onClick={resetUpload}
                  className="absolute top-4 right-4 p-2 bg-red-500/80 hover:bg-red-500 rounded-full backdrop-blur-sm transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              )}
            </div>

            {uploading && (
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/80">Загрузка...</span>
                  <span className="text-blue-400">{progress}%</span>
                </div>
                <div className="h-2 bg-white/10 rounded-full overflow-hidden">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: `${progress}%` }}
                    className="h-full bg-gradient-to-r from-blue-500 to-purple-500"
                  />
                </div>
              </div>
            )}

            <button
              onClick={handleUpload}
              disabled={uploading}
              className="
                w-full py-4 px-6 rounded-xl font-semibold
                bg-gradient-to-r from-blue-500 to-purple-500
                hover:from-blue-600 hover:to-purple-600
                disabled:opacity-50 disabled:cursor-not-allowed
                transition-all duration-300
                flex items-center justify-center gap-2
              "
            >
              {uploading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  Обработка...
                </>
              ) : (
                <>
                  <ImageIcon className="w-5 h-5" />
                  Анализировать
                </>
              )}
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

