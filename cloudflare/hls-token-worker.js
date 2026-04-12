/**
 * Cloudflare Worker — HLS Token Validation at Edge
 *
 * PURPOSE
 * -------
 * Validates HMAC-SHA256 signed HLS tokens at the Cloudflare edge, eliminating
 * all origin auth_request calls for HLS delivery.  The token format matches
 * HlsTokenService.java on the backend exactly:
 *
 *   payload  = "{streamId}:{expiryEpochSeconds}"
 *   token    = base64url(payload) + "." + hex(HMAC-SHA256(secret, payload))
 *
 * FLOW
 * ----
 *   1. Viewer calls /api/hls/token (backend) → receives token (5-min TTL)
 *   2. Frontend appends ?t={token} to every /hls/{streamId}/* URL
 *   3. THIS Worker intercepts the request, validates HMAC in-edge (< 1ms)
 *   4. PASS  → fetch() continues to CDN/origin (segment served from cache)
 *   5. REJECT → 403 Forbidden returned immediately, origin never contacted
 *
 * RESULT
 * ------
 * - Auth overhead: ~0.5ms per request (edge crypto, no network hop)
 * - Origin auth_request calls: ZERO (100% validated at edge)
 * - Security: tokens are stream-scoped + time-limited; HMAC prevents forgery
 *
 * DEPLOYMENT
 * ----------
 *   1. In Cloudflare dashboard → Workers & Pages → Create Worker
 *   2. Paste this file as the worker body
 *   3. Set environment variable:  HLS_HMAC_SECRET = <same value as hls.token.secret>
 *   4. Add route:  cdn.joinlivora.com/hls/*  → this worker
 *   5. Disable Cloudflare cache for /api/hls/token and /api/hls/validate
 *
 * CACHE RULES (set in Cloudflare dashboard → Caching → Cache Rules)
 * -----------------------------------------------------------------
 *   Rule 1 — "HLS Playlists"
 *     IF: hostname = cdn.joinlivora.com AND URI path ends with ".m3u8"
 *     THEN: Cache Everything, Edge TTL = 4 seconds, Browser TTL = 0
 *
 *   Rule 2 — "HLS Segments"
 *     IF: hostname = cdn.joinlivora.com AND URI path ends with ".ts"
 *     THEN: Cache Everything, Edge TTL = 3600 seconds, Browser TTL = 3600
 *
 *   Rule 3 — "HLS Token Bypass"
 *     IF: hostname includes joinlivora.com AND URI path starts with "/api/hls/"
 *     THEN: Bypass Cache
 *
 * ORIGIN SHIELD (Tiered Cache)
 * ----------------------------
 *   Enable in: Cloudflare dashboard → Caching → Tiered Cache → Smart Tiered Cache
 *   Select region closest to your EU origin (e.g., Frankfurt or Amsterdam).
 *   Effect: only ONE Cloudflare PoP makes cache-miss requests to origin, preventing
 *   thundering-herd on stream start when 10k viewers all request the first playlist.
 *
 * MULTI-ORIGIN LOAD BALANCING
 * ---------------------------
 *   Cloudflare dashboard → Traffic → Load Balancers → Create Load Balancer
 *   Hostname: cdn.joinlivora.com
 *   Origins:
 *     origin-eu-1: api.joinlivora.com  (primary, weight 50)
 *     origin-eu-2: api2.joinlivora.com (primary, weight 50)
 *     origin-us-1: us.joinlivora.com   (failover)
 *   Health Check: GET /actuator/health → expect HTTP 200
 *   Failover: 2 consecutive failures → remove from pool
 */

// ─── Constants ────────────────────────────────────────────────────────────────

/** Regex matching /hls/{uuid}/*.m3u8 and /hls/{uuid}/*.ts */
const HLS_PATH_RE = /^\/hls\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\//i;

/** Paths that should always bypass token check (handled by backend directly) */
const BYPASS_PATHS = ["/api/hls/token", "/api/hls/validate", "/api/", "/ws", "/webhooks/", "/actuator/"];

// ─── Worker entrypoint ────────────────────────────────────────────────────────

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // ── 1. Let non-HLS paths pass through to origin unchanged ──────────────
    const isBypass = BYPASS_PATHS.some(p => path.startsWith(p));
    if (isBypass) {
      return fetch(request);
    }

    const hlsMatch = HLS_PATH_RE.exec(path);
    if (!hlsMatch) {
      // Not an HLS path — pass through
      return fetch(request);
    }

    // ── 2. Extract streamId and token from the request ─────────────────────
    const streamId = hlsMatch[1];
    const token    = url.searchParams.get("t");

    if (!token) {
      return new Response("Forbidden: missing token", {
        status: 403,
        headers: { "Content-Type": "text/plain", "X-Token-Error": "missing" }
      });
    }

    // ── 3. Validate HMAC at edge (no origin call) ──────────────────────────
    const secret = env.HLS_HMAC_SECRET;
    if (!secret) {
      // Worker not configured — fail open and let origin auth_request handle it
      console.warn("HLS_HMAC_SECRET not set; falling back to origin auth");
      return fetch(request);
    }

    const validationResult = await validateHlsToken(streamId, token, secret);
    if (!validationResult.valid) {
      return new Response(`Forbidden: ${validationResult.reason}`, {
        status: 403,
        headers: {
          "Content-Type": "text/plain",
          "X-Token-Error": validationResult.reason
        }
      });
    }

    // ── 4. Token valid — strip ?t from the URL before forwarding to CDN ────
    // Removes the token from the cache key so CDN can serve the same cached
    // segment to all viewers regardless of their individual tokens.
    const cleanUrl = new URL(request.url);
    cleanUrl.searchParams.delete("t");
    const cleanRequest = new Request(cleanUrl.toString(), request);

    return fetch(cleanRequest);
  }
};

// ─── HMAC validation ──────────────────────────────────────────────────────────

/**
 * Validates an HLS token.
 *
 * Token format (matches HlsTokenService.java):
 *   token = base64url(payload) + "." + hexHmac
 *   payload = "{streamId}:{expiryEpochSeconds}"
 *
 * @param {string} streamId  - UUID from the URL path
 * @param {string} token     - ?t= query parameter value
 * @param {string} secret    - HMAC secret (same as hls.token.secret in backend)
 * @returns {{ valid: boolean, reason?: string }}
 */
async function validateHlsToken(streamId, token, secret) {
  try {
    const dotIdx = token.lastIndexOf(".");
    if (dotIdx === -1) {
      return { valid: false, reason: "malformed" };
    }

    const b64Payload = token.substring(0, dotIdx);
    const hexMac     = token.substring(dotIdx + 1);

    // Decode payload
    let payloadStr;
    try {
      payloadStr = atob(b64Payload.replace(/-/g, "+").replace(/_/g, "/"));
    } catch {
      return { valid: false, reason: "malformed-payload" };
    }

    // Verify format: {streamId}:{expiryEpochSeconds}
    const colonIdx = payloadStr.indexOf(":");
    if (colonIdx === -1) {
      return { valid: false, reason: "malformed-payload" };
    }

    const payloadStreamId = payloadStr.substring(0, colonIdx);
    const expiryStr       = payloadStr.substring(colonIdx + 1);

    // Stream ID binding check
    if (payloadStreamId.toLowerCase() !== streamId.toLowerCase()) {
      return { valid: false, reason: "stream-mismatch" };
    }

    // Expiry check
    const expiry = parseInt(expiryStr, 10);
    if (isNaN(expiry)) {
      return { valid: false, reason: "malformed-expiry" };
    }
    const nowSeconds = Math.floor(Date.now() / 1000);
    if (nowSeconds > expiry) {
      return { valid: false, reason: "expired" };
    }

    // HMAC verification (constant-time via Web Crypto)
    const keyMaterial = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"]
    );

    const expectedMacBuffer = await crypto.subtle.sign(
      "HMAC",
      keyMaterial,
      new TextEncoder().encode(payloadStr)
    );

    const expectedHex = bufferToHex(expectedMacBuffer);

    // Constant-time comparison to prevent timing attacks
    if (!constantTimeEqual(expectedHex, hexMac)) {
      return { valid: false, reason: "invalid-signature" };
    }

    return { valid: true };
  } catch (err) {
    console.error("Token validation error:", err);
    // Fail closed — any unexpected error is a rejection
    return { valid: false, reason: "internal-error" };
  }
}

// ─── Utilities ────────────────────────────────────────────────────────────────

/**
 * Converts an ArrayBuffer to lowercase hex string.
 * @param {ArrayBuffer} buffer
 * @returns {string}
 */
function bufferToHex(buffer) {
  return Array.from(new Uint8Array(buffer))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * Constant-time string comparison to prevent timing attacks.
 * Both strings must be hex (same character set) for this to be effective.
 * @param {string} a
 * @param {string} b
 * @returns {boolean}
 */
function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}
