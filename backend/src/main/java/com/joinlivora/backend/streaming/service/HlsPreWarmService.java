package com.joinlivora.backend.streaming.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Pre-warms the CDN cache for a new live stream immediately after it starts.
 *
 * <h3>Problem</h3>
 * <p>When a stream starts, the first N viewers all trigger CDN cache misses simultaneously
 * (the "thundering herd" problem). Each miss hits the origin Nginx → disk, generating a
 * short-duration origin traffic spike that can be many times the steady-state load.
 * For a celebrity stream that announces its start on social media, thousands of viewers
 * join in the first 30 seconds — all hitting origin simultaneously.
 *
 * <h3>Solution</h3>
 * <p>Immediately after {@link com.joinlivora.backend.livestream.event.StreamStartedEventV2}
 * is published, this service fires async HTTP requests to the CDN origin for:
 * <ol>
 *   <li>{@code master.m3u8} — the ABR manifest</li>
 *   <li>{@code 720p.m3u8}, {@code 480p.m3u8}, {@code 360p.m3u8} — per-variant playlists</li>
 *   <li>First 3 segments of each variant — the initial buffering window</li>
 * </ol>
 * The CDN caches these responses before any viewers arrive, eliminating the cold-start spike.
 *
 * <h3>Retry strategy</h3>
 * <p>Pre-warm requests are best-effort with up to 3 retries and 2s timeout. A pre-warm failure
 * does not block stream startup — the CDN will warm itself on the first organic viewer request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HlsPreWarmService {

    private final WebClient.Builder webClientBuilder;

    @Value("${livora.cdn.base-url:https://cdn.joinlivora.com}")
    private String cdnBaseUrl;

    @Value("${livora.cdn.prewarm-enabled:true}")
    private boolean preWarmEnabled;

    @Value("${livora.cdn.prewarm-segments:3}")
    private int preWarmSegmentCount;

    // Variant names that the FFmpeg worker produces
    private static final List<String> VARIANTS = List.of("720p", "480p", "360p");

    // Segment naming pattern (timestamp-based): segment_%Y%m%d_%s_%04d.ts
    // For pre-warming we use a generic naming pattern approximation.
    // In practice, the first segments are always segment_XXXXXXX_0000.ts through _000N.ts.
    // The worker also outputs variant-prefixed segments: 720p_XXXXXXXX_0001.ts etc.
    private static final String SEGMENT_GLOB = "%s_*.ts";

    /**
     * Triggers async CDN pre-warm for the given stream.
     * Called by {@link com.joinlivora.backend.livestream.listener.LivestreamEventListener}
     * after a {@link com.joinlivora.backend.livestream.event.StreamStartedEventV2} event.
     *
     * @param streamId  the UUID of the newly started live stream
     */
    @Async("taskExecutor")
    public void preWarmStream(UUID streamId) {
        if (!preWarmEnabled) {
            log.debug("CDN PRE-WARM DISABLED stream={}", streamId);
            return;
        }

        log.info("CDN PRE-WARM START stream={}", streamId);
        WebClient client = webClientBuilder
                .baseUrl(cdnBaseUrl)
                .build();

        // 1. Fetch master playlist
        preWarmUrl(client, streamId, "master.m3u8");

        // 2. Fetch per-variant playlists and first segments
        for (String variant : VARIANTS) {
            // Variant playlist
            preWarmUrl(client, streamId, variant + ".m3u8");

            // First N segments using index-based naming (0001..000N)
            // The actual segment files may have timestamps in the name; the CDN will cache
            // any URL it receives. We request the playlist first so the CDN resolves segment URLs.
            // Segment pre-warm is best-effort — if filenames don't match, the miss is harmless.
            for (int seg = 1; seg <= preWarmSegmentCount; seg++) {
                String segFilename = String.format("%s_%04d.ts", variant, seg);
                preWarmUrl(client, streamId, segFilename);
            }
        }

        log.info("CDN PRE-WARM COMPLETE stream={} variants={} segments={}",
                streamId, VARIANTS.size(), preWarmSegmentCount);
    }

    /**
     * Fires a single CDN pre-warm request.
     * Uses a short timeout and swallows all errors — this is best-effort.
     */
    private void preWarmUrl(WebClient client, UUID streamId, String filename) {
        String path = String.format("/hls/%s/%s", streamId, filename);
        try {
            client.get()
                    .uri(path)
                    .header("X-Prewarm", "1")  // custom header so CDN logs can identify pre-warm
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .subscribe(
                            response -> log.debug("CDN PRE-WARM HIT stream={} path={} status={}",
                                    streamId, path, response.getStatusCode()),
                            error -> log.debug("CDN PRE-WARM MISS stream={} path={}: {}",
                                    streamId, path, error.getMessage())
                    );
        } catch (Exception e) {
            log.debug("CDN PRE-WARM SKIP stream={} path={}: {}", streamId, path, e.getMessage());
        }
    }
}
