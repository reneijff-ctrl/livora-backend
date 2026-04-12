package com.joinlivora.backend.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_role", columnList = "role"),
    @Index(name = "idx_user_created_at", columnList = "created_at")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column
    private String displayName;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_role")
    private AdminRole adminRole;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_risk_level", nullable = false)
    private FraudRiskLevel fraudRiskLevel = FraudRiskLevel.LOW;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "trust_score", nullable = false)
    private int trustScore = 100;

    @Column(name = "payouts_enabled", nullable = false)
    private boolean payoutsEnabled = false;

    @Column(name = "stripe_account_id", unique = true)
    private String stripeAccountId;

    @Column(name = "stripe_onboarding_complete", nullable = false)
    private boolean stripeOnboardingComplete = false;

    @Column(name = "shadowbanned", nullable = false)
    private boolean shadowbanned = false;

    @Column(name = "sessions_invalidated_at")
    private Instant sessionsInvalidatedAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public User() {
    }

    public User(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

    // ===== getters =====
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public AdminRole getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(AdminRole adminRole) {
        this.adminRole = adminRole;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public FraudRiskLevel getFraudRiskLevel() {
        return fraudRiskLevel;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public boolean isPayoutsEnabled() {
        return payoutsEnabled;
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public boolean isStripeOnboardingComplete() {
        return stripeOnboardingComplete;
    }

    public boolean isShadowbanned() {
        return shadowbanned;
    }

    public Instant getSessionsInvalidatedAt() {
        return sessionsInvalidatedAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ===== setters =====
    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public void setFraudRiskLevel(FraudRiskLevel fraudRiskLevel) {
        this.fraudRiskLevel = fraudRiskLevel;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setTrustScore(int trustScore) {
        this.trustScore = trustScore;
    }

    public void setPayoutsEnabled(boolean payoutsEnabled) {
        this.payoutsEnabled = payoutsEnabled;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }

    public void setStripeOnboardingComplete(boolean stripeOnboardingComplete) {
        this.stripeOnboardingComplete = stripeOnboardingComplete;
    }

    public void setShadowbanned(boolean shadowbanned) {
        this.shadowbanned = shadowbanned;
    }

    public void setSessionsInvalidatedAt(Instant sessionsInvalidatedAt) {
        this.sessionsInvalidatedAt = sessionsInvalidatedAt;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
