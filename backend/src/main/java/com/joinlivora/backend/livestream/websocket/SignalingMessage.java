package com.joinlivora.backend.livestream.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessage {
    private String type;
    private String requestId;
    private Long senderId;
    private String roomId;
    private String streamId;
    private Object data;
    private Object sdp;
    private Object candidate;
    
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public static SignalingMessage error(String message) {
        return error(message, null, null, null);
    }

    public static SignalingMessage error(String message, String roomId) {
        return error(message, null, roomId, null);
    }

    public static SignalingMessage error(String message, Long senderId, String roomId, String streamId) {
        SignalingMessage msg = new SignalingMessage();
        msg.setType("ERROR");
        msg.setSdp(message);
        msg.setSenderId(senderId);
        msg.setRoomId(roomId);
        msg.setStreamId(streamId);
        return msg;
    }
}
