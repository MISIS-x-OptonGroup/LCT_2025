# Фронтенд для системы анализа деревьев

Современный веб-интерфейс для анализа состояния деревьев с использованием AI.

## Особенности

- 🎨 Современный glassmorphic дизайн
- ⚡ Плавные анимации с Framer Motion
- 🗺️ Интерактивная карта с Leaflet
- 📱 Полностью адаптивный дизайн
- 🔄 Real-time обновления статуса обработки
- 🖼️ Drag & drop загрузка изображений

## Технологии

- Next.js 15
- React 19
- TypeScript
- Tailwind CSS
- Framer Motion
- Leaflet / React-Leaflet
- Lucide Icons

## Установка

```bash
npm install
```

## Настройка

Создайте файл `.env.local`:

```bash
cp .env.local.example .env.local
```

Укажите URL вашего бэкенда:

```env
NEXT_PUBLIC_API_URL=http://localhost:8000
```

## Запуск

### Режим разработки

```bash
npm run dev
```

Откройте [http://localhost:3000](http://localhost:3000)

### Production сборка

```bash
npm run build
npm start
```

## Структура проекта

```
frontend/
├── app/                    # Next.js App Router
│   ├── map/               # Страница карты
│   ├── image/[id]/        # Детальный просмотр изображения
│   ├── layout.tsx         # Основной layout
│   ├── page.tsx           # Главная страница
│   └── globals.css        # Глобальные стили
├── components/            # Переиспользуемые компоненты
│   ├── ImageUpload.tsx   # Компонент загрузки
│   ├── ImageCard.tsx     # Карточка изображения
│   └── MapView.tsx       # Компонент карты
└── lib/                  # Утилиты и API
    └── api.ts            # API клиент
```

## API

Приложение взаимодействует с бэкендом через следующие эндпоинты:

- `POST /api/v1/images/upload` - Загрузка изображения
- `GET /api/v1/images/` - Список изображений
- `GET /api/v1/images/{id}` - Детали изображения
- `GET /api/v1/images/{id}/download` - Скачивание
- `GET /api/v1/images/{id}/objects` - Обнаруженные объекты
- `DELETE /api/v1/images/{id}` - Удаление изображения

## Особенности дизайна

### Glassmorphic эффекты

Используются классы `glass` и `glass-dark` для создания прозрачных размытых фонов:

```tsx
<div className="glass rounded-3xl p-8">
  {/* content */}
</div>
```

### Анимации

Все элементы имеют плавные анимации входа и взаимодействия с использованием Framer Motion:

```tsx
<motion.div
  initial={{ opacity: 0, y: 20 }}
  animate={{ opacity: 1, y: 0 }}
>
  {/* content */}
</motion.div>
```

### Адаптивность

Дизайн полностью адаптивен с использованием Tailwind CSS breakpoints:

- Mobile-first подход
- Адаптивные сетки (grid)
- Скрытие/показ элементов на разных экранах

## Кнопки с иконками

Используются иконки из Lucide React без текста или с минимальным текстом:

```tsx
<button className="p-3 rounded-xl glass hover:bg-white/10">
  <Map className="w-5 h-5" />
</button>
```

## Карта

Интерактивная карта показывает локации проанализированных деревьев:

- Автоматическое определение координат из поля `location`
- Кластеризация меток
- Попапы с превью и быстрыми действиями
- Статистика на карте

