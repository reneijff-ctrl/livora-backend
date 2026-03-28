package com.joinlivora.backend.pm;

import com.joinlivora.backend.chat.domain.ChatMessage;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomType;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
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

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PmWebSocketControllerTest {

    @Mock
    private PmService pmService;

    @Mock
    private UserService userService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PmWebSocketController controller;

    private User creator;
    private User viewer;
    private User outsider;
    private ChatRoom pmRoom;
    private Principal creatorPrincipal;
    private Principal viewerPrincipal;
    private Principal outsiderPrincipal;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setUsername("creator1");
        creator.setRole(Role.CREATOR);

        viewer = new User();
        viewer.setId(2L);
        viewer.setUsername("viewer1");
        viewer.setRole(Role.USER);

        outsider = new User();
        outsider.setId(99L);
        outsider.setUsername("outsider");
        outsider.setRole(Role.USER);

        pmRoom = ChatRoom.builder()
                .creatorId(1L)
                .viewerId(2L)
                .roomType(ChatRoomType.PM)
                .isPrivate(true)
                .name("pm-1-2")
                .build();
        pmRoom.setId(100L);

        creatorPrincipal = () -> "1";
        viewerPrincipal = () -> "2";
        outsiderPrincipal = () -> "99";
    }

    private PmWebSocketController.PmInboundMessage makePayload(Long roomId, String content) {
        PmWebSocketController.PmInboundMessage msg = new PmWebSocketController.PmInboundMessage();
        msg.setRoomId(roomId);
        msg.setContent(content);
        return msg;
    }

    // =========================================================================
    // TEST 5 — MESSAGE SEND
    // =========================================================================
    @Nested
    @DisplayName("TEST 5 — Message Send")
    class MessageSendTests {

        @Test
        @DisplayName("Creator can send message — saved in ChatMessage with correct roomId and senderId")
        void sendMessage_AsCreator_ShouldSaveMessage() {
            when(userService.resolveUserFromSubject("1")).thenReturn(Optional.of(creator));
            when(pmService.getRoomById(100L)).thenReturn(pmRoom);
            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
                ChatMessage msg = invocation.getArgument(0);
                msg.setId(1L);
                msg.setCreatedAt(Instant.now());
                return msg;
            });

            controller.handlePmMessage(makePayload(100L, "Hello from creator"), creatorPrincipal);

            ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(captor.capture());

            ChatMessage saved = captor.getValue();
            assertThat(saved.getRoomId()).isEqualTo(100L);
            assertThat(saved.getSenderId()).isEqualTo(1L);
            assertThat(saved.getSenderRole()).isEqualTo("CREATOR");
            assertThat(saved.getContent()).isEqualTo("Hello from creator");

            System.out.println("[DEBUG_LOG] TEST 5 — Creator message saved: PASS");
        }

        @Test
        @DisplayName("Viewer can send message — saved correctly")
        void sendMessage_AsViewer_ShouldSaveMessage() {
            when(userService.resolveUserFromSubject("2")).thenReturn(Optional.of(viewer));
            when(pmService.getRoomById(100L)).thenReturn(pmRoom);
            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
                ChatMessage msg = invocation.getArgument(0);
                msg.setId(2L);
                msg.setCreatedAt(Instant.now());
                return msg;
            });

            controller.handlePmMessage(makePayload(100L, "Hello from viewer"), viewerPrincipal);

            ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(captor.capture());

            ChatMessage saved = captor.getValue();
            assertThat(saved.getRoomId()).isEqualTo(100L);
            assertThat(saved.getSenderId()).isEqualTo(2L);
            assertThat(saved.getSenderRole()).isEqualTo("USER");
            assertThat(saved.getContent()).isEqualTo("Hello from viewer");

            System.out.println("[DEBUG_LOG] TEST 5 — Viewer message saved: PASS");
        }
    }

    // =========================================================================
    // TEST 6 — MESSAGE DELIVERY
    // =========================================================================
    @Nested
    @DisplayName("TEST 6 — Message Delivery")
    class MessageDeliveryTests {

        @Test
        @DisplayName("Message delivered to BOTH creator and viewer via /user/queue/pm-messages")
        void sendMessage_ShouldDeliverToBothUsers() {
            when(userService.resolveUserFromSubject("1")).thenReturn(Optional.of(creator));
            when(pmService.getRoomById(100L)).thenReturn(pmRoom);
            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
                ChatMessage msg = invocation.getArgument(0);
                msg.setId(1L);
                msg.setCreatedAt(Instant.now());
                return msg;
            });

            controller.handlePmMessage(makePayload(100L, "test message"), creatorPrincipal);

            // Verify sent to creator
            verify(messagingTemplate).convertAndSendToUser(
                    eq("1"),
                    eq("/queue/pm-messages"),
                    any(Map.class)
            );

            // Verify sent to viewer
            verify(messagingTemplate).convertAndSendToUser(
                    eq("2"),
                    eq("/queue/pm-messages"),
                    any(Map.class)
            );

            // Verify NOT sent to any other destination (public chat topic)
            verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any());
            verifyNoMoreInteractions(messagingTemplate);

            System.out.println("[DEBUG_LOG] TEST 6 — Delivery to both users: PASS");
        }

        @Test
        @DisplayName("Message payload contains correct fields")
        void sendMessage_PayloadContainsCorrectFields() {
            when(userService.resolveUserFromSubject("1")).thenReturn(Optional.of(creator));
            when(pmService.getRoomById(100L)).thenReturn(pmRoom);
            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
                ChatMessage msg = invocation.getArgument(0);
                msg.setId(1L);
                msg.setCreatedAt(Instant.now());
                return msg;
            });

            controller.handlePmMessage(makePayload(100L, "hello"), creatorPrincipal);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                    anyString(), eq("/queue/pm-messages"), payloadCaptor.capture()
            );

            Map<String, Object> payload = payloadCaptor.getValue();
            assertThat(payload.get("type")).isEqualTo("PM_MESSAGE");
            assertThat(payload.get("roomId")).isEqualTo(100L);
            assertThat(payload.get("senderId")).isEqualTo(1L);
            assertThat(payload.get("senderUsername")).isEqualTo("creator1");
            assertThat(payload.get("senderRole")).isEqualTo("CREATOR");
            assertThat(payload.get("content")).isEqualTo("hello");
            assertThat(payload.get("createdAt")).isNotNull();

            System.out.println("[DEBUG_LOG] TEST 6 — Payload fields: PASS");
        }

        @Test
        @DisplayName("Message is NOT sent to public chat exchange topic")
        void sendMessage_ShouldNotSendToPublicChat() {
            when(userService.resolveUserFromSubject("1")).thenReturn(Optional.of(creator));
            when(pmService.getRoomById(100L)).thenReturn(pmRoom);
            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
                ChatMessage msg = invocation.getArgument(0);
                msg.setId(1L);
                msg.setCreatedAt(Instant.now());
                return msg;
            });

            controller.handlePmMessage(makePayload(100L, "private message"), creatorPrincipal);

            // Only convertAndSendToUser should be called — never convertAndSend (broadcast)
            verify(messagingTemplate, never()).convertAndSend(anyString(), Optional.ofNullable(any()));

            System.out.println("[DEBUG_LOG] TEST 6 — No public chat leakage: PASS");
        }
    }

    // =========================================================================
    // TEST 7 — ACCESS CONTROL
    // =========================================================================
    @Nested
    @DisplayName("TEST 7 — Access Control")
    class AccessControlTests {

        @Test
        @DisplayName("Non-participant should be rejected")
        void sendMessage_AsOutsider_ShouldThrow() {
            when(userService.resolveUserFromSubject("99")).thenReturn(Optional.of(outsider));
            when(pmService.getRoomById(100L)).thenReturn(pmRoom);

            assertThatThrownBy(() ->
                    controller.handlePmMessage(makePayload(100L, "sneaky message"), outsiderPrincipal)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("Not a participant");

            verify(chatMessageRepository, never()).save(any());
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());

            System.out.println("[DEBUG_LOG] TEST 7 — Non-participant rejected: PASS");
        }

        @Test
        @DisplayName("Unauthenticated user (null principal) should be rejected")
        void sendMessage_NullPrincipal_ShouldThrow() {
            assertThatThrownBy(() ->
                    controller.handlePmMessage(makePayload(100L, "test"), null)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("not authenticated");

            verify(chatMessageRepository, never()).save(any());

            System.out.println("[DEBUG_LOG] TEST 7 — Null principal rejected: PASS");
        }

        @Test
        @DisplayName("Non-PM room type should be rejected")
        void sendMessage_NonPmRoom_ShouldThrow() {
            ChatRoom streamRoom = ChatRoom.builder()
                    .creatorId(1L)
                    .roomType(ChatRoomType.STREAM)
                    .name("stream-room")
                    .build();
            streamRoom.setId(200L);

            when(userService.resolveUserFromSubject("1")).thenReturn(Optional.of(creator));
            when(pmService.getRoomById(200L)).thenReturn(streamRoom);

            assertThatThrownBy(() ->
                    controller.handlePmMessage(makePayload(200L, "test"), creatorPrincipal)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("Not a PM room");

            verify(chatMessageRepository, never()).save(any());

            System.out.println("[DEBUG_LOG] TEST 7 — Non-PM room rejected: PASS");
        }

        @Test
        @DisplayName("Unknown user should be rejected")
        void sendMessage_UnknownUser_ShouldThrow() {
            when(userService.resolveUserFromSubject("999")).thenReturn(Optional.empty());

            Principal unknownPrincipal = () -> "999";

            assertThatThrownBy(() ->
                    controller.handlePmMessage(makePayload(100L, "test"), unknownPrincipal)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("User not found");

            verify(chatMessageRepository, never()).save(any());

            System.out.println("[DEBUG_LOG] TEST 7 — Unknown user rejected: PASS");
        }
    }

    // =========================================================================
    // TEST — Exception handler
    // =========================================================================
    @Nested
    @DisplayName("Exception Handler")
    class ExceptionHandlerTests {

        @Test
        @DisplayName("handleException returns error map with correct fields")
        void handleException_ShouldReturnErrorMap() {
            RuntimeException ex = new RuntimeException("Test error");

            Map<String, Object> result = controller.handleException(ex);

            assertThat(result.get("error")).isEqualTo("RuntimeException");
            assertThat(result.get("message")).isEqualTo("Test error");
            assertThat(result.get("timestamp")).isNotNull();

            System.out.println("[DEBUG_LOG] Exception handler: PASS");
        }
    }
}
