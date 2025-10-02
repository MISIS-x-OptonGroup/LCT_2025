#!/bin/bash

echo "🚀 Запуск очистки и пересборки Docker..."

# Команда 1: Очистка системы Docker
echo "🧹 Очищаем систему Docker..."
docker system prune -y

# Команда 2: Очистка томов Docker
echo "🗑️ Очищаем тома Docker..."
docker volume prune -y

# Команда 3: Пересборка и запуск
echo "🔨 Запускаем пересборку и запуск контейнеров..."
docker compose up --build

echo "✅ Все команды выполнены!"