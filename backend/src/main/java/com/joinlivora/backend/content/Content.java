package com.joinlivora.backend.content;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content")
@Getter
@Setter
@ToString(exclude = "creator")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String thumbnailUrl;

    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentAccessLevel accessLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Builder.Default
    private boolean disabled = false;

    @CreationTimestamp
    private Instant createdAt;
}
