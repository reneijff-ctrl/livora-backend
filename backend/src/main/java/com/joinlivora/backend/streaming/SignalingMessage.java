package com.joinlivora.backend.streaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessage {
    public enum Type {
        OFFER, ANSWER, ICE_CANDIDATE, JOIN_ROOM, LEAVE_ROOM, STREAM_START, STREAM_STOP, ERROR, ACCESS_DENIED
    }

    private Type type;
    private UUID roomId;
    private String senderId;
    private String receiverId;
    private Object payload; // SDP or ICE candidate JSON
    private String message;
}
