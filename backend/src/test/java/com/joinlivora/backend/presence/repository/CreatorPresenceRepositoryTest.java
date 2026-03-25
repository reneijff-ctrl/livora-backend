package com.joinlivora.backend.presence.repository;

import com.joinlivora.backend.creator.dto.OnlineCreatorDto;
import com.joinlivora.backend.presence.entity.CreatorPresence;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.model.ProfileVisibility;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CreatorPresenceRepositoryTest {

    @Autowired
    private CreatorPresenceRepository presenceRepository;

    @Autowired
    private CreatorProfileRepository profileRepository;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findAllOnlineCreators_ShouldReturnJoinedData() {
        // Given
        User user = TestUserFactory.createCreator("creator@test.com");
        user.setDisplayName("Test Creator");
        user = userRepository.save(user);

        CreatorProfile profile = CreatorProfile.builder()
                .user(user)
                .username("testcreator")
                .displayName("Test Creator")
                .status(ProfileStatus.ACTIVE)
                .visibility(ProfileVisibility.PUBLIC)
                .build();
        profileRepository.save(profile);

        Creator creatorEntity = Creator.builder()
                .user(user)
                .active(true)
                .build();
        creatorEntity = creatorRepository.save(creatorEntity);

        CreatorPresence presence = CreatorPresence.builder()
                .creatorId(creatorEntity.getId())
                .online(true)
                .lastSeen(Instant.now())
                .build();
        presenceRepository.save(presence);

        // When
        List<OnlineCreatorDto> onlineCreators = presenceRepository.findAllOnlineCreators();

        // Then
        assertThat(onlineCreators).isNotEmpty();
        OnlineCreatorDto dto = onlineCreators.get(0);
        assertThat(dto.getCreatorId()).isEqualTo(creatorEntity.getId());
        assertThat(dto.getUsername()).isEqualTo("testcreator");
        assertThat(dto.isOnline()).isTrue();
    }

    @Test
    void findAllOnlineCreators_ShouldReturnDRAFTProfilesToo() {
        // Given
        User user = TestUserFactory.createCreator("draft@test.com");
        user.setDisplayName("Draft Creator");
        user = userRepository.save(user);

        CreatorProfile profile = CreatorProfile.builder()
                .user(user)
                .username("draftcreator")
                .displayName("Draft Creator")
                .status(ProfileStatus.DRAFT) // NOT ACTIVE
                .visibility(ProfileVisibility.PRIVATE)
                .build();
        profileRepository.save(profile);

        Creator creatorEntity = Creator.builder()
                .user(user)
                .active(true)
                .build();
        creatorEntity = creatorRepository.save(creatorEntity);

        CreatorPresence presence = CreatorPresence.builder()
                .creatorId(creatorEntity.getId())
                .online(true)
                .lastSeen(Instant.now())
                .build();
        presenceRepository.save(presence);

        // When
        List<OnlineCreatorDto> onlineCreators = presenceRepository.findAllOnlineCreators();
        Long creatorEntityId = creatorEntity.getId();

        // Then
        assertThat(onlineCreators).anyMatch(dto -> dto.getCreatorId().equals(creatorEntityId) && dto.getUsername().equals("draftcreator"));
    }

    @Test
    void findByOnlineTrue_ShouldReturnList() {
        // Given
        CreatorPresence presence1 = CreatorPresence.builder()
                .creatorId(998L)
                .online(true)
                .lastSeen(Instant.now())
                .build();
        CreatorPresence presence2 = CreatorPresence.builder()
                .creatorId(999L)
                .online(false)
                .lastSeen(Instant.now())
                .build();
        presenceRepository.saveAll(List.of(presence1, presence2));

        // When
        List<CreatorPresence> onlinePresences = presenceRepository.findByOnlineTrue();

        // Then
        assertThat(onlinePresences).anyMatch(p -> p.getCreatorId().equals(998L));
        assertThat(onlinePresences).noneMatch(p -> p.getCreatorId().equals(999L));
    }
}








