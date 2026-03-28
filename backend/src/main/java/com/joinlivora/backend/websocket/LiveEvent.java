package com.joinlivora.backend.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical event envelope for all WebSocket broadcasts.
 * Provides a consistent structure across chat, monetization, goal, and system streams.
 *
 * @param <T> the payload data type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveEvent<T> {

    private String id;
    private String type;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private T data;

    /**
     * Creates a LiveEvent with an auto-generated ID and current timestamp.
     */
    public static <T> LiveEvent<T> of(String type, T data) {
        return LiveEvent.<T>builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .timestamp(Instant.now())
                .data(data)
                .build();
    }

    /**
     * Creates a LiveEvent with a specific ID and current timestamp.
     */
    public static <T> LiveEvent<T> of(String id, String type, T data) {
        return LiveEvent.<T>builder()
                .id(id)
                .type(type)
                .timestamp(Instant.now())
                .data(data)
                .build();
    }
}
