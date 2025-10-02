import { NextRequest, NextResponse } from 'next/server';

const API_BASE_URL = 'http://36.34.82.242:18087';

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const imageId = id;
    
    // Шаг 1: Получаем signed URL с бэкенда (как для фрагментов)
    const downloadUrlEndpoint = `${API_BASE_URL}/api/v1/images/${imageId}/download`;
    console.log(`[API] Getting signed URL for image ${imageId} from ${downloadUrlEndpoint}`);
    
    const urlResponse = await fetch(downloadUrlEndpoint, {
      method: 'GET',
      cache: 'no-store',
    });
    
    if (!urlResponse.ok) {
      console.error(`[API] Failed to get URL for image ${imageId}: ${urlResponse.statusText}`);
      return NextResponse.json(
        { error: 'Image not found' },
        { status: urlResponse.status }
      );
    }
    
    // Шаг 2: Парсим JSON и извлекаем download_url
    const urlData = await urlResponse.json();
    const signedUrl = urlData.download_url;
    
    if (!signedUrl) {
      console.error(`[API] No download_url in response for image ${imageId}`);
      return NextResponse.json(
        { error: 'Invalid response from backend' },
        { status: 500 }
      );
    }
    
    // Шаг 3: Заменяем внутренний Docker hostname на публичный адрес (как для фрагментов)
    const publicUrl = signedUrl.replace('http://minio:9000', 'http://36.34.82.242:17897');
    console.log(`[API] Image public URL: ${publicUrl}`);
    
    // Шаг 4: Скачиваем изображение из MinIO по signed URL
    const imageResponse = await fetch(publicUrl);
    
    if (!imageResponse.ok) {
      console.error(`[API] Failed to fetch image from storage: ${imageResponse.statusText}`);
      return NextResponse.json(
        { error: 'Failed to fetch image from storage' },
        { status: 500 }
      );
    }
    
    // Шаг 5: Возвращаем изображение клиенту
    const imageBlob = await imageResponse.blob();
    console.log(`[API] Returning image blob, size: ${imageBlob.size} bytes`);
    
    return new NextResponse(imageBlob, {
      headers: {
        'Content-Type': imageResponse.headers.get('Content-Type') || 'image/jpeg',
        'Cache-Control': 'public, max-age=3600',
      },
    });
  } catch (error) {
    console.error('[API] Error downloading image:', error);
    return NextResponse.json(
      { error: 'Failed to download image' },
      { status: 500 }
    );
  }
}

