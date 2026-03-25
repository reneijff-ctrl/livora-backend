package com.joinlivora.backend.creator.model;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity(name = "Creator")
@Table(name = "creator_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Creator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String profileImageUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private boolean active;
}
