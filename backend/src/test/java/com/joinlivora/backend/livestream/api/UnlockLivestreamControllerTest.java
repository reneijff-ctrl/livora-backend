package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.livestream.service.UnlockLivestreamService;
import com.joinlivora.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UnlockLivestreamControllerTest {

    private UnlockLivestreamService unlockService;
    private UnlockLivestreamController controller;

    @BeforeEach
    void setUp() {
        unlockService = mock(UnlockLivestreamService.class);
        controller = new UnlockLivestreamController(unlockService);
    }

    @Test
    void unlockStream_Success() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        UserPrincipal principal = new UserPrincipal(viewerUserId, "viewer@test.com", "pwd", Collections.emptyList());

        UnlockResponse expectedResponse = UnlockResponse.builder()
                .success(true)
                .remainingTokens(100L)
                .build();
        when(unlockService.unlockStream(creatorUserId, viewerUserId)).thenReturn(expectedResponse);

        ResponseEntity<UnlockResponse> response = controller.unlockStream(principal, creatorUserId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(100L, response.getBody().getRemainingTokens());

        verify(unlockService).unlockStream(creatorUserId, viewerUserId);
    }
}








