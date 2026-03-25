package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.ProfileStatus;
import lombok.Data;

@Data
public class UpdateCreatorStatusRequest {
    private ProfileStatus status;
}
