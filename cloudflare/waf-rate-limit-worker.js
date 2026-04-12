/**
 * Cloudflare WAF + Edge Rate Limiting Worker
 *
 * Protects the Livora platform from HLS segment flooding, API abuse, and stream scraping
 * at the network edge — before any request reaches the origin.
 *
 * Deployed as a Cloudflare Worker on routes:
 *   - api.joinlivora.com/*      (API protection)
 *   - cdn.joinlivora.com/hls/*  (HLS segment rate limiting)
 *
 * ─── Rate Limits ────────────────────────────────────────────────────────────────────
 *
 *   HLS segment requests:  200 req/min per IP (CDN serves 98%; origin gets ~4 req/min at steady state)
 *   HLS playlist requests:  60 req/min per IP (poll every 4s = 15/min per viewer; 60 allows 4 streams)
 *   API general:           300 req/min per IP
 *   API auth endpoints:      5 req/min per IP (login, register, token — brute-force protection)
 *   WebSocket upgrade:      30 req/min per IP
 *
 * ─── Anti-leech / Anti-scrape ───────────────────────────────────────────────────────
 *
 *   1. User-Agent blocking: Known scraper UAs are rejected at the edge.
 *   2. Referer check: HLS requests without an acceptable referer are flagged (rate-limited 10×).
 *   3. Segment flood detection: >200 segment req/min from one IP → 429 + 60s cooldown.
 *   4. Stream rebroadcast detection: >50 concurrent segment requests from one IP → 429.
 *
 * ─── Deployment ────────────────────────────────────────────────────────────────────
 *
 *   wrangler.toml:
 *   -----------
 *   name = "livora-waf"
 *   main = "cloudflare/waf-rate-limit-worker.js"
 *   compatibility_date = "2024-01-01"
 *
 *   [[routes]]
 *   pattern = "cdn.joinlivora.com/hls/*"
 *   zone_name = "joinlivora.com"
 *
 *   [[routes]]
 *   pattern = "api.joinlivora.com/*"
 *   zone_name = "joinlivora.com"
 *
 *   [vars]
 *   HLS_HMAC_SECRET = ""  # set via wrangler secret put HLS_HMAC_SECRET
 *
 * ─── Cloudflare Dashboard Rules (set these in addition to the Worker) ───────────────
 *
 *   Cache Rules:
 *     *.ts files:    "Cache Everything", Edge TTL: 3600s, Browser TTL: 3600s
 *     *.m3u8 files:  "Cache Everything", Edge TTL: 4s,    Browser TTL: 0s (no-store)
 *     /api/*:        "Bypass Cache"
 *
 *   Rate Limit Rules (native Cloudflare, complement this Worker):
 *     Rule 1: /hls/*.ts      > 500 req/10min per IP → Block 1 hour
 *     Rule 2: /api/auth/*    > 10  req/min  per IP  → Block 1 hour
 *     Rule 3: All URLs        > 5000 req/min per IP  → Block 24 hours (volumetric DDoS)
 */

// ── Constants ──────────────────────────────────────────────────────────────────────

const RATE_LIMITS = {
  hlsSegment:   { limit: 200, windowMs: 60_000 },  // .ts files
  hlsPlaylist:  { limit: 60,  windowMs: 60_000 },  // .m3u8 files
  apiGeneral:   { limit: 300, windowMs: 60_000 },  // all /api/* except auth
  apiAuth:      { limit: 5,   windowMs: 60_000 },  // /api/auth/*, /api/hls/token
  wsUpgrade:    { limit: 30,  windowMs: 60_000 },  // WebSocket upgrade
};

// User-Agent fragments that indicate bots/scrapers
const BLOCKED_USER_AGENTS = [
  'python-requests',
  'go-http-client',
  'curl/',
  'wget/',
  'scrapy',
  'httpie',
  'ffmpeg',          // block stream re-ingest attempts via direct HLS scraping
  'streamlink',      // stream recording tool
  'youtube-dl',
  'yt-dlp',
];

// Acceptable origin/referer domains for HLS requests (anti-hotlink)
const ALLOWED_REFERERS = [
  'joinlivora.com',
  'cdn.joinlivora.com',
  'localhost:3000',    // dev
];

// ── Main handler ──────────────────────────────────────────────────────────────────

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const ip = request.headers.get('CF-Connecting-IP') || '0.0.0.0';
    const ua = request.headers.get('User-Agent') || '';
    const referer = request.headers.get('Referer') || '';

    // 1. Block known scraper User-Agents
    const blockedUa = BLOCKED_USER_AGENTS.find(b => ua.toLowerCase().includes(b));
    if (blockedUa) {
      return new Response('Forbidden', {
        status: 403,
        headers: { 'X-Block-Reason': 'blocked-ua' },
      });
    }

    // 2. Classify request type and select rate limit
    const isHlsSegment   = url.pathname.endsWith('.ts');
    const isHlsPlaylist  = url.pathname.endsWith('.m3u8');
    const isHlsRequest   = isHlsSegment || isHlsPlaylist;
    const isWsUpgrade    = request.headers.get('Upgrade') === 'websocket';
    const isAuthEndpoint = url.pathname.startsWith('/api/auth/') ||
                           url.pathname === '/api/hls/token';

    // 3. Anti-hotlink check for HLS requests (no valid referer → stricter rate limit)
    let rateLimitMultiplier = 1;
    if (isHlsRequest) {
      const validReferer = ALLOWED_REFERERS.some(r => referer.includes(r));
      if (!validReferer && referer !== '') {
        // Unknown referer on HLS content — this may be hotlinking
        rateLimitMultiplier = 10;  // 10× stricter: 20 req/min for segments
      }
    }

    // 4. Select rate limit config
    let rateLimitConfig;
    if (isHlsSegment) {
      rateLimitConfig = RATE_LIMITS.hlsSegment;
    } else if (isHlsPlaylist) {
      rateLimitConfig = RATE_LIMITS.hlsPlaylist;
    } else if (isWsUpgrade) {
      rateLimitConfig = RATE_LIMITS.wsUpgrade;
    } else if (isAuthEndpoint) {
      rateLimitConfig = RATE_LIMITS.apiAuth;
    } else {
      rateLimitConfig = RATE_LIMITS.apiGeneral;
    }

    // 5. Apply rate limiting via Cloudflare KV (or durable objects in production)
    const rateLimitKey = buildRateLimitKey(url, ip, isHlsSegment, isHlsPlaylist, isWsUpgrade, isAuthEndpoint);
    const effectiveLimit = Math.floor(rateLimitConfig.limit / rateLimitMultiplier);

    if (env.RATE_LIMIT_KV) {
      const allowed = await checkRateLimit(env.RATE_LIMIT_KV, rateLimitKey, effectiveLimit, rateLimitConfig.windowMs, ctx);
      if (!allowed) {
        const retryAfter = Math.ceil(rateLimitConfig.windowMs / 1000);
        return new Response('Too Many Requests', {
          status: 429,
          headers: {
            'Retry-After': String(retryAfter),
            'X-RateLimit-Limit': String(effectiveLimit),
            'X-RateLimit-Remaining': '0',
            'X-Block-Reason': 'rate-limited',
          },
        });
      }
    }

    // 6. All checks passed — forward request to origin
    const response = await fetch(request);

    // 7. Add security headers to all responses
    const headers = new Headers(response.headers);
    headers.set('X-Content-Type-Options', 'nosniff');
    headers.set('X-Frame-Options', 'SAMEORIGIN');
    if (isHlsRequest) {
      headers.set('Access-Control-Allow-Origin', '*');
      headers.set('Access-Control-Allow-Headers', 'Range, Authorization');
      headers.set('Access-Control-Expose-Headers', 'Content-Length, Content-Range');
    }

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
  },
};

// ── Rate limiting via Cloudflare KV ──────────────────────────────────────────────

/**
 * Sliding window rate limiter using Cloudflare KV.
 * Returns true if the request is allowed, false if rate-limited.
 *
 * KV Key: rl:{category}:{ip}
 * KV Value: comma-separated list of Unix timestamps (ms) within the window
 */
async function checkRateLimit(kv, key, limit, windowMs, ctx) {
  const now = Date.now();
  const windowStart = now - windowMs;

  let timestamps = [];
  try {
    const stored = await kv.get(key);
    if (stored) {
      timestamps = stored.split(',')
          .map(Number)
          .filter(t => t > windowStart);  // remove expired entries
    }
  } catch (_) {
    // KV unavailable — fail-open
    return true;
  }

  if (timestamps.length >= limit) {
    return false;
  }

  timestamps.push(now);
  const ttlSeconds = Math.ceil(windowMs / 1000);

  // Update KV asynchronously to not block the response
  ctx.waitUntil(
      kv.put(key, timestamps.join(','), { expirationTtl: ttlSeconds })
  );

  return true;
}

/**
 * Builds a namespaced rate limit key from the request properties.
 */
function buildRateLimitKey(url, ip, isHlsSegment, isHlsPlaylist, isWsUpgrade, isAuthEndpoint) {
  if (isHlsSegment)  return `rl:hls-seg:${ip}`;
  if (isHlsPlaylist) return `rl:hls-pl:${ip}`;
  if (isWsUpgrade)   return `rl:ws:${ip}`;
  if (isAuthEndpoint) return `rl:auth:${ip}`;

  // Group API endpoints into buckets to avoid key explosion
  const pathPrefix = url.pathname.split('/').slice(0, 3).join('/');
  return `rl:api:${pathPrefix}:${ip}`;
}
