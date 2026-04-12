FROM node:20-slim

# mediasoup requires build tools for native compilation
RUN apt-get update && apt-get install -y \
    python3 \
    make \
    g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy package files first for better Docker caching
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Copy application code
COPY . .

# Default port (can be overridden via MEDIASOUP_PORT env var)
EXPOSE 4000

# RTC port range for WebRTC traffic (UDP)
EXPOSE 40000-40100/udp

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=10s \
  CMD node -e "require('http').get('http://localhost:' + (process.env.MEDIASOUP_PORT || 4000) + '/health', r => { process.exit(r.statusCode === 200 ? 0 : 1) }).on('error', () => process.exit(1))"

CMD ["node", "server.js"]
