package com.joinlivora.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRole(Role role);
    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByStripeAccountId(String stripeAccountId);
    Page<User> findAllByRole(Role role, Pageable pageable);
    Page<User> findAllByFraudRiskLevel(FraudRiskLevel fraudRiskLevel, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM User u " +
           "LEFT JOIN LegacyCreatorProfile lcp ON lcp.user = u " +
           "WHERE u.role = com.joinlivora.backend.user.Role.CREATOR " +
           "AND (u.id = :id OR lcp.username = :username OR u.email LIKE CONCAT(:username, '@%'))")
    Optional<User> findCreatorByIdOrUsername(@Param("id") Long id, @Param("username") String username);
}
