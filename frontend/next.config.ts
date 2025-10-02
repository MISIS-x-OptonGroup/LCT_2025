import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: 'localhost',
        port: '8000',
        pathname: '/**',
      },
      {
        protocol: 'http',
        hostname: 'localhost',
        port: '9000',
        pathname: '/**',
      },
      {
        protocol: 'http',
        hostname: 'minio',
        port: '9000',
        pathname: '/**',
      },
    ],
  },
  // Отключаем source maps для устранения ошибок 404
  productionBrowserSourceMaps: false,
  webpack: (config, { dev, isServer }) => {
    // Отключаем devtool source maps в dev режиме
    if (dev && !isServer) {
      config.devtool = false;
    }
    return config;
  },
  // Отключаем React DevTools overlay
  reactStrictMode: false,
};

export default nextConfig;

