// Node.js adapter for TanStack Start — serves static assets from dist/client
// and delegates everything else to the SSR fetch handler.
import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const clientDir = join(__dirname, 'dist', 'client')
const port = process.env.PORT || 3000

const MIME_TYPES = {
  '.js': 'application/javascript',
  '.mjs': 'application/javascript',
  '.css': 'text/css',
  '.html': 'text/html',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.webp': 'image/webp',
  '.webmanifest': 'application/manifest+json',
  '.txt': 'text/plain',
}

async function tryServeStatic(pathname, nodeRes) {
  const filePath = join(clientDir, pathname)
  // Prevent path traversal
  if (!filePath.startsWith(clientDir)) return false
  try {
    const info = await stat(filePath)
    if (!info.isFile()) return false
    const ext = extname(filePath)
    const mime = MIME_TYPES[ext] || 'application/octet-stream'
    const data = await readFile(filePath)
    const headers = { 'Content-Type': mime, 'Content-Length': data.length }
    // Cache hashed assets for 1 year
    if (pathname.startsWith('/assets/')) {
      headers['Cache-Control'] = 'public, max-age=31536000, immutable'
    }
    nodeRes.writeHead(200, headers)
    nodeRes.end(data)
    return true
  } catch {
    return false
  }
}

const server = (await import('./dist/server/server.js')).default

createServer(async (nodeReq, nodeRes) => {
  try {
    const pathname = new URL(nodeReq.url, 'http://localhost').pathname

    // Try static files first
    if (await tryServeStatic(pathname, nodeRes)) return

    // SSR handler
    const protocol = nodeReq.headers['x-forwarded-proto'] || 'http'
    const host = nodeReq.headers.host || `localhost:${port}`
    const url = `${protocol}://${host}${nodeReq.url}`
    const headers = new Headers()
    for (const [key, value] of Object.entries(nodeReq.headers)) {
      if (value) headers.set(key, Array.isArray(value) ? value.join(', ') : value)
    }

    let body = undefined
    if (nodeReq.method !== 'GET' && nodeReq.method !== 'HEAD') {
      body = await new Promise((resolve) => {
        const chunks = []
        nodeReq.on('data', (chunk) => chunks.push(chunk))
        nodeReq.on('end', () => resolve(Buffer.concat(chunks)))
      })
    }

    const request = new Request(url, {
      method: nodeReq.method,
      headers,
      body,
      duplex: 'half',
    })

    const response = await server.fetch(request)

    const resHeaders = {}
    response.headers.forEach((value, key) => {
      const existing = resHeaders[key]
      if (existing) {
        resHeaders[key] = Array.isArray(existing) ? [...existing, value] : [existing, value]
      } else {
        resHeaders[key] = value
      }
    })

    nodeRes.writeHead(response.status, resHeaders)

    if (response.body) {
      const reader = response.body.getReader()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        nodeRes.write(value)
      }
    }
    nodeRes.end()
  } catch (err) {
    console.error('Request error:', err)
    if (!nodeRes.headersSent) {
      nodeRes.writeHead(500, { 'Content-Type': 'text/plain' })
    }
    nodeRes.end('Internal Server Error')
  }
}).listen(port, () => {
  console.log(`Nettbank listening on :${port}`)
})
