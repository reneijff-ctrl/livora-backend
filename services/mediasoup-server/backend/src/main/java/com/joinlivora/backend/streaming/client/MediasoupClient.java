package com.joinlivora.backend.streaming.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Collections;

@Component
@Slf4j
public class MediasoupClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String MEDIASOUP_URL = "http://localhost:4000";

    public MediasoupRoomsResponse getRooms() {
        try {
            return restTemplate.getForObject(MEDIASOUP_URL + "/rooms", MediasoupRoomsResponse.class);
        } catch (Exception e) {
            log.error("LIVESTREAM-SFU: Failed to fetch rooms from Mediasoup: {}", e.getMessage());
            return new MediasoupRoomsResponse(Collections.emptyList());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupRoomsResponse {
        private List<MediasoupRoom> rooms;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediasoupRoom {
        private String roomId;
        private int producers;
        private int consumers;
        private int transports;
    }
}
