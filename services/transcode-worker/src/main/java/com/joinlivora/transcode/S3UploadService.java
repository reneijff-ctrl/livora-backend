package com.joinlivora.transcode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Async S3 / Cloudflare R2 upload helper for HLS segments and playlists.
 *
 * <p>When {@link TranscodeProperties#isS3Enabled()} is {@code true} this service
 * provides a non-blocking {@link #upload(Path, String)} method that puts a local
 * file to the configured bucket under the key
 * {@code <s3KeyPrefix>/<relativeKey>}.
 *
 * <p>The underlying {@link S3AsyncClient} is created lazily at startup only when
 * S3 is enabled, so the transcode worker starts in ≤ 1 second even when S3 is
 * disabled (default for single-region dev deployments).
 *
 * <p><strong>Cloudflare R2 compatibility:</strong> R2 uses an S3-compatible API
 * exposed at {@code https://<account-id>.r2.cloudflarestorage.com}.  Set
 * {@code transcode.s3-endpoint} to that URL and {@code transcode.s3-region} to
 * {@code auto}.  R2 has zero egress cost to Cloudflare CDN — the recommended
 * production storage for HLS delivery.
 */
@Service
@Slf4j
public class S3UploadService {

    private final TranscodeProperties props;

    /** Nullable — only initialised when s3Enabled=true. */
    private S3AsyncClient s3Client;

    /** Small dedicated executor so S3 uploads never block FFmpeg threads. */
    private ExecutorService uploadExecutor;

    public S3UploadService(TranscodeProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        if (!props.isS3Enabled()) {
            log.info("S3_UPLOAD_DISABLED: HLS files will be served from local volume only");
            return;
        }

        uploadExecutor = Executors.newFixedThreadPool(
                Math.max(2, props.getMaxConcurrent() * 2),
                r -> new Thread(r, "s3-upload")
        );

        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getS3AccessKeyId(), props.getS3SecretAccessKey())
        );

        var builder = S3AsyncClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(props.getS3Region()));

        // Override endpoint for R2 or custom S3-compatible stores
        if (props.getS3Endpoint() != null && !props.getS3Endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.getS3Endpoint()));
            // R2 requires path-style addressing
            builder.serviceConfiguration(
                    software.amazon.awssdk.services.s3.S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build()
            );
        }

        s3Client = builder.build();
        log.info("S3_UPLOAD_ENABLED: bucket={} endpoint={} region={} keyPrefix={}",
                props.getS3Bucket(),
                props.getS3Endpoint().isBlank() ? "AWS default" : props.getS3Endpoint(),
                props.getS3Region(),
                props.getS3KeyPrefix());
    }

    @PreDestroy
    public void shutdown() {
        if (uploadExecutor != null) {
            uploadExecutor.shutdown();
            try {
                if (!uploadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    uploadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                uploadExecutor.shutdownNow();
            }
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    /**
     * Uploads {@code localFile} to S3/R2 at key
     * {@code <s3KeyPrefix>/<relativeKey>} asynchronously.
     *
     * <p>The returned future completes with {@code true} on success or {@code false}
     * on failure (the error is logged but never propagated — uploads are best-effort
     * for live-streaming resilience).
     *
     * <p>If S3 is disabled, returns an immediately-completed {@code true} future.
     *
     * @param localFile   path to the file to upload
     * @param relativeKey key suffix after the prefix, e.g. {@code <streamId>/720p.m3u8}
     */
    public CompletableFuture<Boolean> upload(Path localFile, String relativeKey) {
        if (!props.isS3Enabled() || s3Client == null) {
            return CompletableFuture.completedFuture(true);
        }

        String fullKey = props.getS3KeyPrefix() + "/" + relativeKey;
        String contentType = resolveContentType(localFile.getFileName().toString());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.getS3Bucket())
                .key(fullKey)
                .contentType(contentType)
                // Segments are immutable once written; playlists should not be cached aggressively
                .cacheControl(localFile.toString().endsWith(".ts") ? "public, max-age=3600, immutable"
                                                                    : "public, max-age=4")
                .build();

        return s3Client.putObject(request, AsyncRequestBody.fromFile(localFile))
                .handle((PutObjectResponse response, Throwable ex) -> {
                    if (ex != null) {
                        log.warn("S3_UPLOAD_FAILED: key={} reason={}", fullKey, ex.getMessage());
                        return false;
                    }
                    log.debug("S3_UPLOAD_OK: key={} etag={}", fullKey, response.eTag());
                    return true;
                });
    }

    /**
     * Uploads the master playlist for a stream.
     * Convenience wrapper that sets the correct relative key.
     */
    public CompletableFuture<Boolean> uploadMasterPlaylist(Path masterFile, String streamId) {
        return upload(masterFile, streamId + "/master.m3u8");
    }

    /**
     * Returns {@code true} if S3 uploading is currently active.
     */
    public boolean isEnabled() {
        return props.isS3Enabled() && s3Client != null;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String resolveContentType(String filename) {
        if (filename.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (filename.endsWith(".ts"))   return "video/mp2t";
        return "application/octet-stream";
    }
}
