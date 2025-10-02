# Makefile для управления Docker окружением

.PHONY: help build up down logs clean dev-up dev-down migrate test

# Цвета для вывода
GREEN=\033[0;32m
YELLOW=\033[1;33m
RED=\033[0;31m
NC=\033[0m # No Color

help: ## Показать справку
	@echo "$(GREEN)Доступные команды:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-15s$(NC) %s\n", $$1, $$2}'

build: ## Собрать Docker образы
	@echo "$(GREEN)Сборка Docker образов...$(NC)"
	docker-compose build

up: ## Запустить продакшн окружение
	@echo "$(GREEN)Запуск продакшн окружения...$(NC)"
	docker-compose up -d
	@echo "$(GREEN)Сервисы запущены:$(NC)"
	@echo "  - API: http://localhost:8000"
	@echo "  - MinIO Console: http://localhost:9001 (minioadmin/minioadmin123)"
	@echo "  - MinIO API: http://localhost:9000"
	@echo "  - Nginx: http://localhost:80"

down: ## Остановить продакшн окружение
	@echo "$(YELLOW)Остановка продакшн окружения...$(NC)"
	docker-compose down

logs: ## Показать логи всех сервисов
	docker-compose logs -f

logs-app: ## Показать логи только приложения
	docker-compose logs -f app

dev-up: ## Запустить окружение разработки
	@echo "$(GREEN)Запуск окружения разработки...$(NC)"
	docker-compose -f docker-compose.dev.yml up -d
	@echo "$(GREEN)Сервисы разработки запущены:$(NC)"
	@echo "  - PostgreSQL: localhost:5433"
	@echo "  - MinIO Console: http://localhost:9001 (minioadmin/minioadmin123)"
	@echo "  - MinIO API: http://localhost:9000"
	@echo "  - Redis: localhost:6379"

dev-down: ## Остановить окружение разработки
	@echo "$(YELLOW)Остановка окружения разработки...$(NC)"
	docker-compose -f docker-compose.dev.yml down

migrate: ## Применить миграции базы данных
	@echo "$(GREEN)Применение миграций...$(NC)"
	docker-compose exec app alembic upgrade head

migrate-create: ## Создать новую миграцию (использование: make migrate-create MESSAGE="описание")
	@echo "$(GREEN)Создание новой миграции...$(NC)"
	docker-compose exec app alembic revision --autogenerate -m "$(MESSAGE)"

shell: ## Подключиться к контейнеру приложения
	docker-compose exec app /bin/bash

db-shell: ## Подключиться к базе данных
	docker-compose exec postgres psql -U postgres -d lct_backend1

minio-shell: ## Подключиться к MinIO контейнеру
	docker-compose exec minio /bin/sh

clean: ## Удалить все контейнеры, образы и volumes
	@echo "$(RED)Удаление всех контейнеров, образов и volumes...$(NC)"
	docker-compose down -v --rmi all --remove-orphans
	docker-compose -f docker-compose.dev.yml down -v --rmi all --remove-orphans

restart: ## Перезапустить все сервисы
	@echo "$(YELLOW)Перезапуск сервисов...$(NC)"
	docker-compose restart

restart-app: ## Перезапустить только приложение
	@echo "$(YELLOW)Перезапуск приложения...$(NC)"
	docker-compose restart app

status: ## Показать статус всех сервисов
	docker-compose ps

test: ## Запустить тесты
	@echo "$(GREEN)Запуск тестов...$(NC)"
	docker-compose exec app python -m pytest

test-api: ## Запустить тест API
	@echo "$(GREEN)Запуск теста API...$(NC)"
	python test_api.py

backup-db: ## Создать бэкап базы данных
	@echo "$(GREEN)Создание бэкапа базы данных...$(NC)"
	docker-compose exec postgres pg_dump -U postgres lct_backend1 > backup_$(shell date +%Y%m%d_%H%M%S).sql

restore-db: ## Восстановить базу данных из бэкапа (использование: make restore-db BACKUP=backup_file.sql)
	@echo "$(GREEN)Восстановление базы данных...$(NC)"
	docker-compose exec -T postgres psql -U postgres lct_backend1 < $(BACKUP)

setup: ## Первоначальная настройка проекта
	@echo "$(GREEN)Первоначальная настройка проекта...$(NC)"
	@if [ ! -f .env ]; then \
		echo "$(YELLOW)Создание .env файла из примера...$(NC)"; \
		cp env_example.txt .env; \
		echo "$(RED)⚠️  Не забудьте настроить .env файл!$(NC)"; \
	fi
	$(MAKE) build
	$(MAKE) up
	@echo "$(GREEN)✅ Проект настроен и запущен!$(NC)"

# Команды для мониторинга
monitor: ## Мониторинг ресурсов контейнеров
	docker stats

health: ## Проверка здоровья сервисов
	@echo "$(GREEN)Проверка здоровья сервисов:$(NC)"
	@curl -s http://localhost:8000/health | jq . || echo "$(RED)API недоступен$(NC)"
	@curl -s http://localhost:9000/minio/health/live && echo "$(GREEN)MinIO работает$(NC)" || echo "$(RED)MinIO недоступен$(NC)"
