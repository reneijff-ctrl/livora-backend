package com.joinlivora.backend.pm;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.domain.ChatRoomType;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PmServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserService userService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PmReadStateRepository pmReadStateRepository;

    @InjectMocks
    private PmService pmService;

    private User creator;
    private User viewer;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setUsername("creator1");
        creator.setRole(Role.CREATOR);

        viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");
        viewer.setUsername("viewer1");
        viewer.setRole(Role.USER);

        lenient().when(pmReadStateRepository.findByRoomIdAndUserId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(anyLong()))
                .thenReturn(Optional.empty());
    }

    // =========================================================================
    // TEST 1 — START SESSION
    // =========================================================================
    @Nested
    @DisplayName("TEST 1 — Start Session")
    class StartSessionTests {

        @Test
        @DisplayName("Should create PM room and return correct DTO")
        void startSession_ShouldCreateRoom() {
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
                ChatRoom room = invocation.getArgument(0);
                room.setId(100L);
                return room;
            });

            PmSessionDto dto = pmService.startSession(1L, 2L);

            assertThat(dto).isNotNull();
            assertThat(dto.roomId()).isEqualTo(100L);
            assertThat(dto.creatorId()).isEqualTo(1L);
            assertThat(dto.creatorUsername()).isEqualTo("creator1");
            assertThat(dto.viewerId()).isEqualTo(2L);
            assertThat(dto.viewerUsername()).isEqualTo("viewer1");

            System.out.println("[DEBUG_LOG] TEST 1 — Start Session: PASS");
        }

        @Test
        @DisplayName("Should save room with correct fields: roomType=PM, isPrivate=true, viewerId set")
        void startSession_ShouldSaveCorrectRoomFields() {
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
                ChatRoom room = invocation.getArgument(0);
                room.setId(100L);
                return room;
            });

            pmService.startSession(1L, 2L);

            ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
            verify(chatRoomRepository).save(captor.capture());

            ChatRoom saved = captor.getValue();
            assertThat(saved.getRoomType()).isEqualTo(ChatRoomType.PM);
            assertThat(saved.isPrivate()).isTrue();
            assertThat(saved.getViewerId()).isEqualTo(2L);
            assertThat(saved.getCreatorId()).isEqualTo(1L);
            assertThat(saved.getName()).startsWith("pm-1-2-");
            assertThat(saved.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
            assertThat(saved.isLive()).isTrue();

            System.out.println("[DEBUG_LOG] TEST 1 — Room fields validation: PASS");
        }

        @Test
        @DisplayName("Should send WebSocket PM_SESSION_STARTED event to viewer")
        void startSession_ShouldSendWebSocketEvent() {
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
                ChatRoom room = invocation.getArgument(0);
                room.setId(100L);
                return room;
            });

            pmService.startSession(1L, 2L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("2"),
                    eq("/queue/pm-events"),
                    payloadCaptor.capture()
            );

            Map<String, Object> payload = payloadCaptor.getValue();
            assertThat(payload.get("type")).isEqualTo("PM_SESSION_STARTED");
            assertThat(payload.get("roomId")).isEqualTo(100L);
            assertThat(payload.get("creatorId")).isEqualTo(1L);
            assertThat(payload.get("creatorUsername")).isEqualTo("creator1");

            System.out.println("[DEBUG_LOG] TEST 8 — WebSocket event: PASS");
        }

        @Test
        @DisplayName("WebSocket failure should NOT break session creation")
        void startSession_WebSocketFailure_ShouldNotBreak() {
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
                ChatRoom room = invocation.getArgument(0);
                room.setId(100L);
                return room;
            });
            doThrow(new RuntimeException("Broker down"))
                    .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

            PmSessionDto dto = pmService.startSession(1L, 2L);

            assertThat(dto).isNotNull();
            assertThat(dto.roomId()).isEqualTo(100L);
            verify(chatRoomRepository).save(any(ChatRoom.class));

            System.out.println("[DEBUG_LOG] WebSocket failure safety: PASS");
        }
    }

    // =========================================================================
    // TEST 2 — DUPLICATE PREVENTION
    // =========================================================================
    @Nested
    @DisplayName("TEST 2 — Duplicate Prevention")
    class DuplicatePreventionTests {

        @Test
        @DisplayName("Should return existing room when PM already exists")
        void startSession_Duplicate_ShouldReturnExisting() {
            ChatRoom existingRoom = ChatRoom.builder()
                    .creatorId(1L)
                    .viewerId(2L)
                    .roomType(ChatRoomType.PM)
                    .isPrivate(true)
                    .name("pm-1-2")
                    .status(ChatRoomStatus.ACTIVE)
                    .build();
            existingRoom.setId(50L);

            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.of(existingRoom));

            PmSessionDto dto = pmService.startSession(1L, 2L);

            assertThat(dto.roomId()).isEqualTo(50L);
            verify(chatRoomRepository, never()).save(any());
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());

            System.out.println("[DEBUG_LOG] TEST 2 — Duplicate prevention: PASS");
        }

        @Test
        @DisplayName("Should NOT create duplicate rows in DB")
        void startSession_Duplicate_ShouldNotSave() {
            ChatRoom existingRoom = ChatRoom.builder()
                    .creatorId(1L)
                    .viewerId(2L)
                    .roomType(ChatRoomType.PM)
                    .isPrivate(true)
                    .name("pm-1-2")
                    .status(ChatRoomStatus.ACTIVE)
                    .build();
            existingRoom.setId(50L);

            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.of(existingRoom));

            // Call twice
            PmSessionDto dto1 = pmService.startSession(1L, 2L);
            PmSessionDto dto2 = pmService.startSession(1L, 2L);

            assertThat(dto1.roomId()).isEqualTo(dto2.roomId());
            verify(chatRoomRepository, never()).save(any());

            System.out.println("[DEBUG_LOG] TEST 2 — No duplicate rows: PASS");
        }
    }

    // =========================================================================
    // TEST 3 — ACTIVE SESSIONS
    // =========================================================================
    @Nested
    @DisplayName("TEST 3 — Active Sessions")
    class ActiveSessionsTests {

        @Test
        @DisplayName("Creator should see PM sessions where creatorId = userId")
        void getActiveSessions_AsCreator() {
            ChatRoom room = ChatRoom.builder()
                    .creatorId(1L)
                    .viewerId(2L)
                    .roomType(ChatRoomType.PM)
                    .isPrivate(true)
                    .name("pm-1-2")
                    .status(ChatRoomStatus.ACTIVE)
                    .build();
            room.setId(100L);

            when(chatRoomRepository.findByCreatorIdAndRoomType(1L, ChatRoomType.PM))
                    .thenReturn(List.of(room));
            when(chatRoomRepository.findByViewerIdAndRoomType(1L, ChatRoomType.PM))
                    .thenReturn(List.of());
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);

            List<PmSessionDto> sessions = pmService.getActiveSessions(1L);

            assertThat(sessions).hasSize(1);
            assertThat(sessions.get(0).creatorId()).isEqualTo(1L);
            assertThat(sessions.get(0).viewerId()).isEqualTo(2L);

            System.out.println("[DEBUG_LOG] TEST 3 — Creator active sessions: PASS");
        }

        @Test
        @DisplayName("Viewer should see PM sessions where viewerId = userId")
        void getActiveSessions_AsViewer() {
            ChatRoom room = ChatRoom.builder()
                    .creatorId(1L)
                    .viewerId(2L)
                    .roomType(ChatRoomType.PM)
                    .isPrivate(true)
                    .name("pm-1-2")
                    .status(ChatRoomStatus.ACTIVE)
                    .build();
            room.setId(100L);

            when(chatRoomRepository.findByCreatorIdAndRoomType(2L, ChatRoomType.PM))
                    .thenReturn(List.of());
            when(chatRoomRepository.findByViewerIdAndRoomType(2L, ChatRoomType.PM))
                    .thenReturn(List.of(room));
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);

            List<PmSessionDto> sessions = pmService.getActiveSessions(2L);

            assertThat(sessions).hasSize(1);
            assertThat(sessions.get(0).creatorId()).isEqualTo(1L);
            assertThat(sessions.get(0).viewerId()).isEqualTo(2L);

            System.out.println("[DEBUG_LOG] TEST 3 — Viewer active sessions: PASS");
        }

        @Test
        @DisplayName("User with no PM sessions should get empty list")
        void getActiveSessions_NoSessions() {
            when(chatRoomRepository.findByCreatorIdAndRoomType(99L, ChatRoomType.PM))
                    .thenReturn(List.of());
            when(chatRoomRepository.findByViewerIdAndRoomType(99L, ChatRoomType.PM))
                    .thenReturn(List.of());

            List<PmSessionDto> sessions = pmService.getActiveSessions(99L);

            assertThat(sessions).isEmpty();

            System.out.println("[DEBUG_LOG] TEST 3 — No sessions: PASS");
        }
    }

    // =========================================================================
    // TEST 9 — EDGE VALIDATION
    // =========================================================================
    @Nested
    @DisplayName("TEST 9 — Edge Validation")
    class EdgeValidationTests {

        @Test
        @DisplayName("Creator cannot PM himself")
        void startSession_SelfPm_ShouldThrow() {
            assertThatThrownBy(() -> pmService.startSession(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot start PM with yourself");

            verify(chatRoomRepository, never()).save(any());

            System.out.println("[DEBUG_LOG] TEST 9 — Self-PM guard: PASS");
        }

        @Test
        @DisplayName("viewerId must exist (UserService throws if not found)")
        void startSession_NonExistentViewer_ShouldThrow() {
            // PmService calls getById(viewerId) first (line 35), then getById(creatorId) (line 36)
            when(userService.getById(999L)).thenThrow(
                    new com.joinlivora.backend.exception.ResourceNotFoundException("User not found: 999"));

            assertThatThrownBy(() -> pmService.startSession(1L, 999L))
                    .isInstanceOf(com.joinlivora.backend.exception.ResourceNotFoundException.class);

            verify(chatRoomRepository, never()).save(any());

            System.out.println("[DEBUG_LOG] TEST 9 — Non-existent viewer: PASS");
        }

        @Test
        @DisplayName("roomType is always PM for PM sessions")
        void startSession_RoomType_AlwaysPM() {
            when(userService.getById(1L)).thenReturn(creator);
            when(userService.getById(2L)).thenReturn(viewer);
            when(chatRoomRepository.findByCreatorIdAndViewerIdAndRoomTypeAndStatus(1L, 2L, ChatRoomType.PM, ChatRoomStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
                ChatRoom room = invocation.getArgument(0);
                room.setId(100L);
                return room;
            });

            pmService.startSession(1L, 2L);

            ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
            verify(chatRoomRepository).save(captor.capture());
            assertThat(captor.getValue().getRoomType()).isEqualTo(ChatRoomType.PM);

            System.out.println("[DEBUG_LOG] TEST 9 — roomType always PM: PASS");
        }
    }

    // =========================================================================
    // TEST — getRoomById
    // =========================================================================
    @Nested
    @DisplayName("getRoomById")
    class GetRoomByIdTests {

        @Test
        @DisplayName("Should return room when found")
        void getRoomById_Found() {
            ChatRoom room = ChatRoom.builder()
                    .creatorId(1L)
                    .viewerId(2L)
                    .roomType(ChatRoomType.PM)
                    .build();
            room.setId(100L);

            when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));

            ChatRoom result = pmService.getRoomById(100L);
            assertThat(result.getId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Should throw when room not found")
        void getRoomById_NotFound() {
            when(chatRoomRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pmService.getRoomById(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PM room not found: 999");
        }
    }
}
