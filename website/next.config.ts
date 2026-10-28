import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  // Model weights and the ONNX runtime are loaded with native require at request time.
  serverExternalPackages: ['@huggingface/transformers', 'onnxruntime-node'],
  poweredByHeader: false,
}

export default nextConfig
