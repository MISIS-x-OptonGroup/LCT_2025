const API_BASE_URL = 'http://36.34.82.242:18087';

export interface ImageMetadata {
  taken_at?: string;
  location?: string;
  author?: string;
}

export interface DetectedObject {
  label: string;
  confidence: number;
  bbox: number[];
  description?: any;
  fragment_url?: string;
}

export interface ImageFragment {
  id: number;
  image_id: number;
  filename: string;
  s3_key: string;
  s3_url: string;
  description_text?: string;
  created_at: string;
}

export interface Image {
  id: number;
  filename: string;
  original_filename: string;
  content_type: string;
  file_size: number;
  s3_key: string;
  s3_url: string;
  s3_bucket?: string;
  width?: number;
  height?: number;
  taken_at?: string;
  location?: string;
  author?: string;
  processing_status: string;
  description_text?: string;
  detected_objects?: DetectedObject[];
  created_at: string;
  updated_at: string;
  fragments: ImageFragment[];
}

export async function uploadImage(file: File, metadata?: ImageMetadata): Promise<Image> {
  const formData = new FormData();
  formData.append('file', file);
  
  if (metadata) {
    formData.append('metadata', JSON.stringify(metadata));
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/images/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error('Failed to upload image');
  }

  return response.json();
}

export async function getImages(skip = 0, limit = 100): Promise<Image[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/?skip=${skip}&limit=${limit}`);
  
  if (!response.ok) {
    throw new Error('Failed to fetch images');
  }

  return response.json();
}

export async function getImage(id: number): Promise<Image> {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}`);
  
  if (!response.ok) {
    throw new Error('Failed to fetch image');
  }

  return response.json();
}

export async function getImageDownloadUrl(id: number): Promise<{ download_url: string; filename: string }> {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}/download`);
  
  if (!response.ok) {
    throw new Error('Failed to get download URL');
  }

  return response.json();
}

export async function deleteImage(id: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error('Failed to delete image');
  }
}

export async function reprocessImage(id: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}/reprocess`, {
    method: 'POST',
  });

  if (!response.ok) {
    throw new Error('Failed to reprocess image');
  }
}

export async function getImageObjects(id: number) {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}/objects`);
  
  if (!response.ok) {
    throw new Error('Failed to fetch image objects');
  }

  return response.json();
}

export async function getImageFragments(id: number) {
  const response = await fetch(`${API_BASE_URL}/api/v1/images/${id}/fragments`);
  
  if (!response.ok) {
    throw new Error('Failed to fetch image fragments');
  }

  return response.json();
}

