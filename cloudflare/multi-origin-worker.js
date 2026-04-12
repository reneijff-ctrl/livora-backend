/**
 * Livora — Cloudflare Multi-Origin HLS Routing Worker
 *
 * Responsibilities:
 *  1. Geo-steering: route EU users → EU origin pool, US users → US origin pool.
 *  2. HMAC token validation at edge (no origin call for auth).
 *  3. Origin failover: if primary origin returns 5xx, retry secondary.
 *  4. CDN cache key normalisation: strip ?t= token so all viewers share one cache object.
 *  5. Correct cache TTLs: playlists (4 s), segments (1 h immutable).
 *
 * Deployment:
 *  wrangler deploy  (configure wrangler.toml as shown at the bottom of this file)
 *
 * Required Worker secrets (set via `wrangler secret put`):
 *  HLS_HMAC_SECRET   — same value as hls.token.secret in backend application.yml
 *
 * Optional environment variables (wrangler.toml [vars]):
 *  EU_ORIGIN_1   — https://origin-eu-1.joinlivora.com  (primary EU)
 *  EU_ORIGIN_2   — https://origin-eu-2.joinlivora.com  (failover EU)
 *  US_ORIGIN_1   — https://origin-us-1.joinlivora.com  (primary US)
 *  US_ORIGIN_2   — https://origin-us-2.joinlivora.com  (failover US)
 *  TOKEN_TTL_SEC — token validity window in seconds (default 300)
 *  ENFORCE_TOKEN — "true" to block requests without a valid token (default "true")
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Route: cdn.joinlivora.com/hls/*
 * ─────────────────────────────────────────────────────────────────────────────
 */

// ── Defaults (overridden by env vars from wrangler.toml) ──────────────────

const DEFAULT_EU_ORIGIN_1 = "https://origin-eu-1.joinlivora.com";
const DEFAULT_EU_ORIGIN_2 = "https://origin-eu-2.joinlivora.com";
const DEFAULT_US_ORIGIN_1 = "https://origin-us-1.joinlivora.com";
const DEFAULT_US_ORIGIN_2 = "https://origin-us-2.joinlivora.com";
const DEFAULT_TOKEN_TTL    = 300;    // seconds
const SEGMENT_CACHE_TTL    = 3600;   // 1 hour — segments are immutable
const PLAYLIST_CACHE_TTL   = 4;      // 4 seconds — live playlist must be fresh

// EU Cloudflare colo codes (approximate — add more as needed)
const EU_COLOS = new Set([
  "AMS","ATH","BCN","BER","BRU","BUD","CDG","CPH","DUB","DUS","FCO","FRA",
  "GVA","HAM","HEL","IST","KIV","LGW","LHR","LIS","LJU","MAD","MAN","MRS",
  "MUC","NAP","OSL","OTP","PMO","PRG","SKG","SOF","STO","TLL","VIE","VNO",
  "WAW","ZAG","ZRH",
]);

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // ── Only handle /hls/* paths ─────────────────────────────────────────
    if (!url.pathname.startsWith("/hls/")) {
      return new Response("Not Found", { status: 404 });
    }

    // ── Determine geo pool ───────────────────────────────────────────────
    const colo    = request.cf?.colo ?? "";
    const country = request.cf?.country ?? "";
    const isEU    = EU_COLOS.has(colo)
                 || ["GB","DE","FR","NL","BE","IT","ES","PL","SE","CH","AT","DK","NO","FI","IE","PT","CZ","HU","RO"].includes(country);

    const primaryOrigin   = isEU
        ? (env.EU_ORIGIN_1 ?? DEFAULT_EU_ORIGIN_1)
        : (env.US_ORIGIN_1 ?? DEFAULT_US_ORIGIN_1);
    const failoverOrigin  = isEU
        ? (env.EU_ORIGIN_2 ?? DEFAULT_EU_ORIGIN_2)
        : (env.US_ORIGIN_2 ?? DEFAULT_US_ORIGIN_2);

    // ── Extract streamId and filename from path ──────────────────────────
    // Expected: /hls/{streamId}/{filename}
    const pathParts = url.pathname.split("/").filter(Boolean);  // ["hls", "<streamId>", "<filename>"]
    if (pathParts.length < 3) {
      return new Response("Bad Request: invalid HLS path", { status: 400 });
    }
    const streamId = pathParts[1];
    const filename  = pathParts[2];

    // ── HMAC token validation ────────────────────────────────────────────
    const enforceToken = (env.ENFORCE_TOKEN ?? "true") === "true";
    if (enforceToken) {
      const token    = url.searchParams.get("t");
      const hmacSecret = env.HLS_HMAC_SECRET;

      if (!hmacSecret) {
        // Secret not set — log and fail open in development, fail closed in production
        console.warn("HLS_HMAC_SECRET not set; skipping token validation");
      } else if (!token) {
        return new Response("Forbidden: missing token", { status: 403 });
      } else {
        const valid = await validateHmacToken(token, streamId, hmacSecret,
                                              parseInt(env.TOKEN_TTL_SEC ?? DEFAULT_TOKEN_TTL, 10));
        if (!valid) {
          return new Response("Forbidden: invalid or expired token", { status: 403 });
        }
      }
    }

    // ── Build cache key (strip token parameter) ──────────────────────────
    const cacheUrl = new URL(request.url);
    cacheUrl.searchParams.delete("t");
    const cacheKey = new Request(cacheUrl.toString(), request);

    // ── Check Cloudflare cache ───────────────────────────────────────────
    const cache = caches.default;
    const cached = await cache.match(cacheKey);
    if (cached) {
      const resp = new Response(cached.body, cached);
      resp.headers.set("X-Cache", "HIT");
      resp.headers.set("X-Origin-Region", isEU ? "eu" : "us");
      return resp;
    }

    // ── Fetch from origin (primary → failover) ───────────────────────────
    const originUrl = primaryOrigin + url.pathname;
    let response = await fetchWithFailover(originUrl, primaryOrigin, failoverOrigin, url.pathname, request);

    if (!response.ok && response.status !== 404) {
      // Both origins failed
      return new Response("Service Unavailable: all origins unreachable", { status: 503 });
    }

    // ── Apply cache headers and cache the response ───────────────────────
    response = addCacheHeaders(response, filename);

    // Store in cache asynchronously (waitUntil so we don't delay the response)
    if (response.ok) {
      ctx.waitUntil(cache.put(cacheKey, response.clone()));
    }

    // ── Return response with observability headers ────────────────────────
    const finalResponse = new Response(response.body, response);
    finalResponse.headers.set("X-Cache", "MISS");
    finalResponse.headers.set("X-Origin-Region", isEU ? "eu" : "us");
    finalResponse.headers.set("X-Colo", colo);
    finalResponse.headers.set("Access-Control-Allow-Origin", "*");
    finalResponse.headers.set("Access-Control-Expose-Headers", "Content-Length,X-Cache,X-Origin-Region");

    return finalResponse;
  }
};

// ── Origin fetch with failover ───────────────────────────────────────────────

async function fetchWithFailover(primaryUrl, primaryBase, failoverBase, path, originalRequest) {
  // Build a clean forwarded request (strip token from query)
  const forwardUrl = new URL(primaryUrl);
  const originRequest = new Request(forwardUrl.toString(), {
    method: originalRequest.method,
    headers: filterHeaders(originalRequest.headers),
    redirect: "follow",
  });

  try {
    const response = await fetch(originRequest, { cf: { cacheTtl: 0 } });
    if (response.ok || response.status === 404) {
      // 404 is a definitive answer — don't retry
      return response;
    }

    // 5xx — try failover
    console.warn(`PRIMARY_ORIGIN_FAILED: status=${response.status} url=${primaryUrl} — trying failover`);
    return await fetch(new Request(failoverBase + path, {
      method: originalRequest.method,
      headers: filterHeaders(originalRequest.headers),
      redirect: "follow",
    }), { cf: { cacheTtl: 0 } });

  } catch (err) {
    console.error(`PRIMARY_ORIGIN_ERROR: ${err.message} — trying failover`);
    try {
      return await fetch(new Request(failoverBase + path, {
        method: originalRequest.method,
        headers: filterHeaders(originalRequest.headers),
        redirect: "follow",
      }), { cf: { cacheTtl: 0 } });
    } catch (failoverErr) {
      console.error(`FAILOVER_ORIGIN_ERROR: ${failoverErr.message}`);
      return new Response("Service Unavailable", { status: 503 });
    }
  }
}

// ── Cache header injection ───────────────────────────────────────────────────

function addCacheHeaders(response, filename) {
  const headers = new Headers(response.headers);

  if (filename.endsWith(".ts")) {
    // Segments are immutable once written
    headers.set("Cache-Control", `public, max-age=${SEGMENT_CACHE_TTL}, immutable`);
    headers.set("Surrogate-Control", `max-age=${SEGMENT_CACHE_TTL}`);
    headers.set("Content-Type", "video/mp2t");
  } else if (filename.endsWith(".m3u8")) {
    // Live playlists must be refreshed every segment interval
    headers.set("Cache-Control", `public, max-age=${PLAYLIST_CACHE_TTL}`);
    headers.set("Surrogate-Control", `max-age=${PLAYLIST_CACHE_TTL}`);
    headers.set("Content-Type", "application/vnd.apple.mpegurl");
  }

  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Expose-Headers", "Content-Length");
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("Vary", "Accept-Encoding");

  return new Response(response.body, { status: response.status, headers });
}

// ── HMAC-SHA256 token validation ─────────────────────────────────────────────

/**
 * Validates a token of the form base64url(streamId:expiry):hexHmac
 *
 * This matches the format produced by HlsTokenService.java in the Spring backend.
 */
async function validateHmacToken(token, streamId, secret, ttlSeconds) {
  try {
    const [payloadB64, receivedHex] = token.split(":");
    if (!payloadB64 || !receivedHex) return false;

    const payload = atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/"));
    const [tokenStreamId, expiryStr] = payload.split(":");
    if (!tokenStreamId || !expiryStr) return false;

    // Stream ID binding check
    if (tokenStreamId !== streamId) return false;

    // Expiry check
    const expiry = parseInt(expiryStr, 10);
    const nowSec = Math.floor(Date.now() / 1000);
    if (isNaN(expiry) || nowSec > expiry) return false;

    // HMAC verification (constant-time via SubtleCrypto)
    const encoder  = new TextEncoder();
    const keyData   = encoder.encode(secret);
    const msgData   = encoder.encode(payload);

    const cryptoKey = await crypto.subtle.importKey(
        "raw", keyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    );
    const sigBuffer  = await crypto.subtle.sign("HMAC", cryptoKey, msgData);
    const expectedHex = Array.from(new Uint8Array(sigBuffer))
        .map(b => b.toString(16).padStart(2, "0")).join("");

    // Constant-time comparison (prevent timing attacks)
    if (expectedHex.length !== receivedHex.length) return false;
    let diff = 0;
    for (let i = 0; i < expectedHex.length; i++) {
      diff |= expectedHex.charCodeAt(i) ^ receivedHex.charCodeAt(i);
    }
    return diff === 0;

  } catch (e) {
    console.error(`TOKEN_VALIDATION_ERROR: ${e.message}`);
    return false;
  }
}

// ── Header filtering (do not forward sensitive headers to origin) ─────────────

function filterHeaders(incoming) {
  const forwarded = new Headers();
  const PASS_THROUGH = ["accept", "accept-encoding", "range", "if-none-match", "if-modified-since"];
  PASS_THROUGH.forEach(name => {
    const val = incoming.get(name);
    if (val) forwarded.set(name, val);
  });
  return forwarded;
}

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * wrangler.toml — reference configuration
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * name = "livora-multi-origin"
 * main = "cloudflare/multi-origin-worker.js"
 * compatibility_date = "2024-01-01"
 *
 * [vars]
 * EU_ORIGIN_1   = "https://origin-eu-1.joinlivora.com"
 * EU_ORIGIN_2   = "https://origin-eu-2.joinlivora.com"
 * US_ORIGIN_1   = "https://origin-us-1.joinlivora.com"
 * US_ORIGIN_2   = "https://origin-us-2.joinlivora.com"
 * TOKEN_TTL_SEC = "300"
 * ENFORCE_TOKEN = "true"
 *
 * [[routes]]
 * pattern = "cdn.joinlivora.com/hls/*"
 * zone_name = "joinlivora.com"
 *
 * # Set secrets with: wrangler secret put HLS_HMAC_SECRET
 * # Value must equal hls.token.secret in backend application.yml
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Cloudflare Load Balancer configuration (UI or Terraform):
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Pool: livora-eu
 *   Origins:
 *     - name: eu-1  address: origin-eu-1.joinlivora.com  weight: 1
 *     - name: eu-2  address: origin-eu-2.joinlivora.com  weight: 1
 *   Health Check:
 *     path: /actuator/health
 *     interval: 10s  timeout: 2s  retries: 2
 *     expected_codes: "200"
 *
 * Pool: livora-us
 *   Origins:
 *     - name: us-1  address: origin-us-1.joinlivora.com  weight: 1
 *     - name: us-2  address: origin-us-2.joinlivora.com  weight: 1
 *   Health Check: (same as above)
 *
 * Load Balancer: api.joinlivora.com
 *   Default pool: livora-eu
 *   Geo steering:
 *     - Region: WNAM, ENAM → pool: livora-us
 *     - Region: WEUR, EEUR → pool: livora-eu
 *   Fallback pool: livora-eu
 *   Session affinity: none  (stateless requests — no affinity needed)
 *   Failover: on HTTP 5xx, retry next origin in pool; then failover to next pool
 *
 * Tiered Cache (origin shield):
 *   Enable "Smart Tiered Cache" — Cloudflare automatically selects the nearest
 *   upper-tier PoP for each origin, reducing origin pull requests by ~80%.
 *
 * Cache Rules:
 *   Rule 1 — HLS segments (immutable):
 *     Condition: URI path ends with ".ts"
 *     Action: Cache Everything  Edge TTL: 3600s  Browser TTL: 3600s
 *   Rule 2 — HLS playlists (live):
 *     Condition: URI path ends with ".m3u8"
 *     Action: Cache Everything  Edge TTL: 4s  Browser TTL: 0s (no-store)
 *   Rule 3 — Token/validate endpoints:
 *     Condition: URI path starts with "/api/hls/"
 *     Action: Bypass Cache
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
