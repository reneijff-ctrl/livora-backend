package com.joinlivora.backend.privateshow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateSessionAvailabilityDto {
    private boolean hasActivePrivate;
    private boolean allowSpyOnPrivate;
    private Long spyPricePerMinute;
    private boolean canCurrentUserSpy;
    private boolean isCurrentUserPrivateViewer;
    private boolean isCurrentUserActiveSpy;
    private String activeSessionId;
}
