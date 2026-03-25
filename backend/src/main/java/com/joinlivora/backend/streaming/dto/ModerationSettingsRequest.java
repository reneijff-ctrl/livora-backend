package com.joinlivora.backend.streaming.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class ModerationSettingsRequest {
    private Long creatorUserId;
    private boolean autoPinLargeTips;
    private boolean aiHighlightEnabled;
    private boolean strictMode;

    private List<String> bannedWords = new ArrayList<>();

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public void setBannedWords(Object value) {

        if (value == null) {
            this.bannedWords = new ArrayList<>();
            return;
        }

        if (value instanceof List<?> list) {
            this.bannedWords = list.stream()
                    .map(Object::toString)
                    .toList();
            return;
        }

        if (value instanceof String str) {
            if (str.isBlank()) {
                this.bannedWords = new ArrayList<>();
            } else {
                this.bannedWords = Arrays.stream(str.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        }
    }

    public List<String> getBannedWords() {
        return bannedWords;
    }
}
