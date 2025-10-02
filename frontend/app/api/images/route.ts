import { NextResponse } from 'next/server';

const API_BASE_URL = 'http://36.34.82.242:18087';

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const skip = searchParams.get('skip') || '0';
    const limit = searchParams.get('limit') || '1000';

    const response = await fetch(`${API_BASE_URL}/api/v1/images/?skip=${skip}&limit=${limit}`);
    
    if (!response.ok) {
      return NextResponse.json(
        { error: 'Failed to fetch images' },
        { status: response.status }
      );
    }

    const images = await response.json();
    
    return NextResponse.json({ images });
  } catch (error) {
    console.error('Error fetching images:', error);
    return NextResponse.json(
      { error: 'Internal server error' },
      { status: 500 }
    );
  }
}

