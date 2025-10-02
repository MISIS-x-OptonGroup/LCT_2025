import { NextRequest, NextResponse } from 'next/server';

const API_BASE_URL = 'http://36.34.82.242:18087';

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  try {
    const params = await context.params;
    const id = params.id;
    
    const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}`);
    
    if (!response.ok) {
      return NextResponse.json(
        { error: 'Failed to fetch image' },
        { status: response.status }
      );
    }

    const image = await response.json();
    
    return NextResponse.json(image);
  } catch (error) {
    console.error('Error fetching image:', error);
    return NextResponse.json(
      { error: 'Internal server error' },
      { status: 500 }
    );
  }
}

