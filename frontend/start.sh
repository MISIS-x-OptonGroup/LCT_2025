#!/bin/bash

echo "🚀 Запуск фронтенда..."

# Проверяем наличие node_modules
if [ ! -d "node_modules" ]; then
    echo "📦 Установка зависимостей..."
    npm install --legacy-peer-deps
fi

# Проверяем наличие .env.local
if [ ! -f ".env.local" ]; then
    echo "⚙️  Создание .env.local..."
    echo "NEXT_PUBLIC_API_URL=http://localhost:8000" > .env.local
fi

echo "✨ Запуск в режиме разработки..."
npm run dev

