package com.joinlivora.backend.creator.model;

import com.joinlivora.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "creator_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;

    @Column(unique = true)
    private String username;

    @Column(unique = true, name = "public_handle")
    private String publicHandle;

    @Column(name = "display_name")
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 512, name = "avatar_url")
    private String avatarUrl;

    @Column(length = 512, name = "banner_url")
    private String bannerUrl;

    @Column(name = "real_name")
    private String realName;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    private String gender;

    @Column(name = "interested_in")
    private String interestedIn;

    private String languages;
    private String location;

    @Column(name = "body_type")
    private String bodyType;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "weight_kg")
    private Integer weightKg;
    private String ethnicity;

    @Column(name = "hair_color")
    private String hairColor;

    @Column(name = "eye_color")
    private String eyeColor;

    @Column(name = "onlyfans_url", length = 512)
    private String onlyfansUrl;

    @Column(name = "throne_url", length = 512)
    private String throneUrl;

    @Column(name = "wishlist_url", length = 512)
    private String wishlistUrl;

    @Column(name = "twitter_url", length = 512)
    private String twitterUrl;

    @Column(name = "instagram_url", length = 512)
    private String instagramUrl;

    @Column(name = "show_age")
    @Builder.Default
    private boolean showAge = true;

    @Column(name = "show_location")
    @Builder.Default
    private boolean showLocation = true;

    @Column(name = "show_languages")
    @Builder.Default
    private boolean showLanguages = true;

    @Column(name = "show_body_type")
    @Builder.Default
    private boolean showBodyType = true;

    @Column(name = "show_ethnicity")
    @Builder.Default
    private boolean showEthnicity = true;

    @Column(name = "show_height_weight")
    @Builder.Default
    private boolean showHeightWeight = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProfileStatus status = ProfileStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProfileVisibility visibility = ProfileVisibility.PRIVATE;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
