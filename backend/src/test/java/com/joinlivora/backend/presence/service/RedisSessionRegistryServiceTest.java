package com.joinlivora.backend.presence.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSessionRegistryServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    private RedisSessionRegistryService service;

    private static final String SESSION_ID = "ws-session-abc123";
    private static final String PRINCIPAL = "42";
    private static final Long USER_ID = 42L;
    private static final Long CREATOR_ID = 7L;
    private static final String IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final Duration TTL = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        service = new RedisSessionRegistryService(redis);
    }

    // ── registerSession ───────────────────────────────────────────────────

    @Test
    void registerSession_ShouldStoreAllFieldsAndSetTTL() {
        service.registerSession(SESSION_ID, PRINCIPAL, USER_ID, CREATOR_ID, IP, USER_AGENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("ws:session:" + SESSION_ID), captor.capture());

        Map<String, String> fields = captor.getValue();
        assertEquals(PRINCIPAL, fields.get("principal"));
        assertEquals(USER_ID.toString(), fields.get("userId"));
        assertEquals(CREATOR_ID.toString(), fields.get("creatorId"));
        assertEquals(IP, fields.get("ip"));
        assertEquals(USER_AGENT, fields.get("userAgent"));
        assertNotNull(fields.get("registeredAt"));

        verify(redis).expire("ws:session:" + SESSION_ID, TTL);
        verify(setOps).add("ws:sessions:active", SESSION_ID);
        verify(setOps).add("ws:user:42:sessions", SESSION_ID);
    }

    @Test
    void registerSession_NullOptionalFields_ShouldOnlyStoreNonNull() {
        service.registerSession(SESSION_ID, null, null, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("ws:session:" + SESSION_ID), captor.capture());

        Map<String, String> fields = captor.getValue();
        assertNull(fields.get("principal"));
        assertNull(fields.get("userId"));
        assertNotNull(fields.get("registeredAt"));

        // Active sessions SET is always updated, but user reverse index is not
        verify(setOps).add("ws:sessions:active", SESSION_ID);
        verify(setOps, never()).add(eq("ws:user:" + USER_ID + ":sessions"), anyString());
    }

    // ── unregisterSession ─────────────────────────────────────────────────

    @Test
    void unregisterSession_ShouldDeleteAllKeysAndCleanReverseIndex() {
        when(hashOps.get("ws:session:" + SESSION_ID, "userId")).thenReturn("42");

        service.unregisterSession(SESSION_ID);

        verify(redis).delete(argThat((java.util.List<String> keys) ->
                keys.size() == 5 &&
                keys.contains("ws:session:" + SESSION_ID) &&
                keys.contains("ws:session:" + SESSION_ID + ":subs") &&
                keys.contains("ws:session:" + SESSION_ID + ":submap") &&
                keys.contains("ws:session:" + SESSION_ID + ":jointimes") &&
                keys.contains("ws:session:" + SESSION_ID + ":streams")
        ));
        verify(setOps).remove("ws:sessions:active", SESSION_ID);
        verify(setOps).remove("ws:user:42:sessions", SESSION_ID);
    }

    @Test
    void unregisterSession_NoUserId_ShouldStillCleanActiveSetButSkipReverseIndex() {
        when(hashOps.get("ws:session:" + SESSION_ID, "userId")).thenReturn(null);

        service.unregisterSession(SESSION_ID);

        verify(redis).delete(anyList());
        verify(setOps).remove("ws:sessions:active", SESSION_ID);
        verify(setOps, never()).remove(eq("ws:user:42:sessions"), any());
    }

    // ── Getters ───────────────────────────────────────────────────────────

    @Test
    void getUserId_ShouldReturnParsedLong() {
        when(hashOps.get("ws:session:" + SESSION_ID, "userId")).thenReturn("42");
        assertEquals(42L, service.getUserId(SESSION_ID));
    }

    @Test
    void getUserId_NotFound_ShouldReturnNull() {
        when(hashOps.get("ws:session:" + SESSION_ID, "userId")).thenReturn(null);
        assertNull(service.getUserId(SESSION_ID));
    }

    @Test
    void getCreatorId_ShouldReturnParsedLong() {
        when(hashOps.get("ws:session:" + SESSION_ID, "creatorId")).thenReturn("7");
        assertEquals(7L, service.getCreatorId(SESSION_ID));
    }

    @Test
    void getPrincipal_ShouldReturnString() {
        when(hashOps.get("ws:session:" + SESSION_ID, "principal")).thenReturn("42");
        assertEquals("42", service.getPrincipal(SESSION_ID));
    }

    @Test
    void getIp_ShouldReturnString() {
        when(hashOps.get("ws:session:" + SESSION_ID, "ip")).thenReturn(IP);
        assertEquals(IP, service.getIp(SESSION_ID));
    }

    @Test
    void getUserAgent_ShouldReturnString() {
        when(hashOps.get("ws:session:" + SESSION_ID, "userAgent")).thenReturn(USER_AGENT);
        assertEquals(USER_AGENT, service.getUserAgent(SESSION_ID));
    }

    // ── Subscriptions ─────────────────────────────────────────────────────

    @Test
    void addSubscription_NewDestination_ShouldReturnTrue() {
        when(setOps.add("ws:session:" + SESSION_ID + ":subs", "/topic/chat/1")).thenReturn(1L);

        boolean result = service.addSubscription(SESSION_ID, "sub-0", "/topic/chat/1");

        assertTrue(result);
        verify(hashOps).put("ws:session:" + SESSION_ID + ":submap", "sub-0", "/topic/chat/1");
        verify(redis).expire("ws:session:" + SESSION_ID + ":subs", TTL);
    }

    @Test
    void addSubscription_ExistingDestination_ShouldReturnFalse() {
        when(setOps.add("ws:session:" + SESSION_ID + ":subs", "/topic/chat/1")).thenReturn(0L);

        boolean result = service.addSubscription(SESSION_ID, "sub-1", "/topic/chat/1");

        assertFalse(result);
    }

    @Test
    void removeSubscription_Existing_ShouldReturnDestination() {
        when(hashOps.get("ws:session:" + SESSION_ID + ":submap", "sub-0")).thenReturn("/topic/chat/1");

        String dest = service.removeSubscription(SESSION_ID, "sub-0");

        assertEquals("/topic/chat/1", dest);
        verify(hashOps).delete("ws:session:" + SESSION_ID + ":submap", "sub-0");
        verify(setOps).remove("ws:session:" + SESSION_ID + ":subs", "/topic/chat/1");
    }

    @Test
    void removeSubscription_NotFound_ShouldReturnNull() {
        when(hashOps.get("ws:session:" + SESSION_ID + ":submap", "sub-99")).thenReturn(null);

        assertNull(service.removeSubscription(SESSION_ID, "sub-99"));
    }

    @Test
    void isSubscribedTo_ShouldDelegateToRedis() {
        when(setOps.isMember("ws:session:" + SESSION_ID + ":subs", "/topic/chat/1")).thenReturn(true);
        assertTrue(service.isSubscribedTo(SESSION_ID, "/topic/chat/1"));

        when(setOps.isMember("ws:session:" + SESSION_ID + ":subs", "/topic/chat/2")).thenReturn(false);
        assertFalse(service.isSubscribedTo(SESSION_ID, "/topic/chat/2"));
    }

    @Test
    void getSubscriptions_ShouldReturnMembersOrEmpty() {
        when(setOps.members("ws:session:" + SESSION_ID + ":subs"))
                .thenReturn(Set.of("/topic/chat/1", "/topic/stream/5"));
        assertEquals(2, service.getSubscriptions(SESSION_ID).size());

        when(setOps.members("ws:session:unknown:subs")).thenReturn(null);
        assertEquals(Collections.emptySet(), service.getSubscriptions("unknown"));
    }

    // ── Join Times ────────────────────────────────────────────────────────

    @Test
    void trackJoinTime_ShouldStoreInstantAsString() {
        Instant now = Instant.parse("2026-03-26T12:00:00Z");
        service.trackJoinTime(SESSION_ID, "/topic/stream/5", now);

        verify(hashOps).put("ws:session:" + SESSION_ID + ":jointimes", "/topic/stream/5", "2026-03-26T12:00:00Z");
        verify(redis).expire("ws:session:" + SESSION_ID + ":jointimes", TTL);
    }

    @Test
    void getJoinTime_ShouldParseInstant() {
        when(hashOps.get("ws:session:" + SESSION_ID + ":jointimes", "/topic/stream/5"))
                .thenReturn("2026-03-26T12:00:00Z");
        assertEquals(Instant.parse("2026-03-26T12:00:00Z"), service.getJoinTime(SESSION_ID, "/topic/stream/5"));
    }

    @Test
    void removeJoinTime_ShouldReturnAndDelete() {
        when(hashOps.get("ws:session:" + SESSION_ID + ":jointimes", "/topic/stream/5"))
                .thenReturn("2026-03-26T12:00:00Z");

        Instant result = service.removeJoinTime(SESSION_ID, "/topic/stream/5");

        assertEquals(Instant.parse("2026-03-26T12:00:00Z"), result);
        verify(hashOps).delete("ws:session:" + SESSION_ID + ":jointimes", "/topic/stream/5");
    }

    @Test
    void removeJoinTime_NotFound_ShouldReturnNull() {
        when(hashOps.get("ws:session:" + SESSION_ID + ":jointimes", "/topic/x")).thenReturn(null);
        assertNull(service.removeJoinTime(SESSION_ID, "/topic/x"));
    }

    // ── Joined Streams ────────────────────────────────────────────────────

    @Test
    void markStreamJoined_NewStream_ShouldReturnTrue() {
        when(setOps.add("ws:session:" + SESSION_ID + ":streams", "101")).thenReturn(1L);
        assertTrue(service.markStreamJoined(SESSION_ID, 101L));
        verify(redis).expire("ws:session:" + SESSION_ID + ":streams", TTL);
    }

    @Test
    void markStreamJoined_AlreadyJoined_ShouldReturnFalse() {
        when(setOps.add("ws:session:" + SESSION_ID + ":streams", "101")).thenReturn(0L);
        assertFalse(service.markStreamJoined(SESSION_ID, 101L));
    }

    @Test
    void markStreamJoined_NullStreamId_ShouldReturnFalse() {
        assertFalse(service.markStreamJoined(SESSION_ID, null));
        verifyNoInteractions(setOps);
    }

    @Test
    void markStreamLeft_Existing_ShouldReturnTrue() {
        when(setOps.remove("ws:session:" + SESSION_ID + ":streams", "101")).thenReturn(1L);
        assertTrue(service.markStreamLeft(SESSION_ID, 101L));
    }

    @Test
    void markStreamLeft_NotJoined_ShouldReturnFalse() {
        when(setOps.remove("ws:session:" + SESSION_ID + ":streams", "101")).thenReturn(0L);
        assertFalse(service.markStreamLeft(SESSION_ID, 101L));
    }

    @Test
    void getJoinedStreams_ShouldReturnParsedLongSet() {
        when(setOps.members("ws:session:" + SESSION_ID + ":streams"))
                .thenReturn(Set.of("101", "202"));

        Set<Long> streams = service.getJoinedStreams(SESSION_ID);
        assertEquals(2, streams.size());
        assertTrue(streams.contains(101L));
        assertTrue(streams.contains(202L));
    }

    @Test
    void getJoinedStreams_Empty_ShouldReturnEmptySet() {
        when(setOps.members("ws:session:" + SESSION_ID + ":streams")).thenReturn(null);
        assertTrue(service.getJoinedStreams(SESSION_ID).isEmpty());
    }

    // ── TTL Refresh ───────────────────────────────────────────────────────

    @Test
    void refreshSession_ExistingSession_ShouldRefreshAllKeys() {
        when(redis.hasKey("ws:session:" + SESSION_ID)).thenReturn(true);

        assertTrue(service.refreshSession(SESSION_ID));

        verify(redis).expire("ws:session:" + SESSION_ID, TTL);
        verify(redis).expire("ws:session:" + SESSION_ID + ":subs", TTL);
        verify(redis).expire("ws:session:" + SESSION_ID + ":submap", TTL);
        verify(redis).expire("ws:session:" + SESSION_ID + ":jointimes", TTL);
        verify(redis).expire("ws:session:" + SESSION_ID + ":streams", TTL);
    }

    @Test
    void refreshSession_ExpiredSession_ShouldReturnFalse() {
        when(redis.hasKey("ws:session:" + SESSION_ID)).thenReturn(false);

        assertFalse(service.refreshSession(SESSION_ID));

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    // ── Active Sessions ────────────────────────────────────────────────────

    @Test
    void getActiveSessionsCount_ShouldUseSCARD() {
        when(setOps.size("ws:sessions:active")).thenReturn(42L);

        assertEquals(42L, service.getActiveSessionsCount());

        verify(redis, never()).keys(anyString());
    }

    @Test
    void getActiveSessionsCount_NullSize_ShouldReturnZero() {
        when(setOps.size("ws:sessions:active")).thenReturn(null);

        assertEquals(0L, service.getActiveSessionsCount());
    }

    @Test
    void getAllActiveSessions_ShouldUseActiveSetNotKeys() {
        when(setOps.members("ws:sessions:active")).thenReturn(Set.of("s1", "s2"));
        when(hashOps.get("ws:session:s1", "principal")).thenReturn("user1");
        when(hashOps.get("ws:session:s2", "principal")).thenReturn("user2");

        Map<String, String> result = service.getAllActiveSessions();

        assertEquals(2, result.size());
        assertEquals("user1", result.get("s1"));
        assertEquals("user2", result.get("s2"));
        verify(redis, never()).keys(anyString());
    }

    @Test
    void getAllActiveSessions_StaleEntry_ShouldBeRemovedLazily() {
        when(setOps.members("ws:sessions:active")).thenReturn(Set.of("alive", "stale"));
        when(hashOps.get("ws:session:alive", "principal")).thenReturn("user1");
        when(hashOps.get("ws:session:stale", "principal")).thenReturn(null);

        Map<String, String> result = service.getAllActiveSessions();

        assertEquals(1, result.size());
        assertEquals("user1", result.get("alive"));
        verify(setOps).remove("ws:sessions:active", "stale");
    }

    // ── Reconciliation ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Cursor<String> mockScanCursor(Set<String> keys) {
        Iterator<String> iterator = keys.iterator();
        Cursor<String> cursor = mock(Cursor.class);
        lenient().when(cursor.hasNext()).thenAnswer(inv -> iterator.hasNext());
        lenient().when(cursor.next()).thenAnswer(inv -> iterator.next());
        return cursor;
    }

    @Test
    void reconcileReverseIndexes_ShouldUseScanNotKeys() {
        String userKey = "ws:user:42:sessions";
        Cursor<String> cursor = mockScanCursor(Set.of(userKey));
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(setOps.members(userKey)).thenReturn(Set.of("alive-session", "dead-session"));
        when(redis.hasKey("ws:session:alive-session")).thenReturn(true);
        when(redis.hasKey("ws:session:dead-session")).thenReturn(false);
        when(setOps.size(userKey)).thenReturn(1L);
        // Active sessions SET reconciliation
        when(setOps.members("ws:sessions:active")).thenReturn(Set.of("alive-session"));

        service.reconcileReverseIndexes();

        verify(setOps).remove(userKey, "dead-session");
        verify(setOps, never()).remove(userKey, "alive-session");
        verify(redis, never()).delete(userKey);
        verify(redis, never()).keys(anyString());
    }

    @Test
    void reconcileReverseIndexes_EmptyIndex_ShouldDeleteKey() {
        String userKey = "ws:user:99:sessions";
        Cursor<String> cursor = mockScanCursor(Set.of(userKey));
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(setOps.members(userKey)).thenReturn(Set.of("dead-session"));
        when(redis.hasKey("ws:session:dead-session")).thenReturn(false);
        when(setOps.size(userKey)).thenReturn(0L);
        // Active sessions SET reconciliation
        when(setOps.members("ws:sessions:active")).thenReturn(Collections.emptySet());

        service.reconcileReverseIndexes();

        verify(setOps).remove(userKey, "dead-session");
        verify(redis).delete(userKey);
        verify(redis, never()).keys(anyString());
    }

    @Test
    void reconcileReverseIndexes_ShouldCleanStaleActiveSessionEntries() {
        Cursor<String> cursor = mockScanCursor(Collections.emptySet());
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(setOps.members("ws:sessions:active")).thenReturn(Set.of("alive", "expired"));
        when(redis.hasKey("ws:session:alive")).thenReturn(true);
        when(redis.hasKey("ws:session:expired")).thenReturn(false);

        service.reconcileReverseIndexes();

        verify(setOps).remove("ws:sessions:active", "expired");
        verify(setOps, never()).remove("ws:sessions:active", "alive");
    }
}
