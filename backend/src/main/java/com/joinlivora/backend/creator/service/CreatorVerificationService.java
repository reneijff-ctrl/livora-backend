package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.DocumentType;
import com.joinlivora.backend.creator.dto.CreatorVerificationRequest;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreatorVerificationService {

    private final CreatorVerificationRepository repository;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final UserService userService;

    @Transactional
    public void updateIdImage(Long userId, String url) {
        CreatorVerification verification = getOrCreateVerification(userId);
        verification.setIdDocumentUrl(url);
        verification.setStatus(VerificationStatus.UNDER_REVIEW);
        repository.save(verification);
    }

    @Transactional
    public void updateSelfieImage(Long userId, String url) {
        CreatorVerification verification = getOrCreateVerification(userId);
        verification.setSelfieDocumentUrl(url);
        verification.setStatus(VerificationStatus.UNDER_REVIEW);
        repository.save(verification);
    }

    private CreatorVerification getOrCreateVerification(Long userId) {
        return repository.findByCreator_User_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

                    Creator creator = creatorRepository.findByUser_Id(userId)
                            .orElseGet(() -> creatorRepository.save(
                                    Creator.builder()
                                            .user(user)
                                            .active(false)
                                            .build()
                            ));

                    Optional<CreatorProfile> profileOpt = creatorProfileRepository.findByUserId(userId);

                    return CreatorVerification.builder()
                            .creator(creator)
                            .status(VerificationStatus.PENDING)
                            .legalFirstName(profileOpt.map(CreatorProfile::getRealName).filter(s -> !s.isBlank()).orElse("Unknown"))
                            .legalLastName("")
                            .dateOfBirth(profileOpt.map(CreatorProfile::getBirthDate).orElse(java.time.LocalDate.now().minusYears(18)))
                            .country(profileOpt.map(CreatorProfile::getLocation).filter(s -> !s.isBlank()).orElse("Unknown"))
                            .documentType(DocumentType.ID_CARD)
                            .idDocumentUrl("")
                            .selfieDocumentUrl("")
                            .build();
                });
    }

    @Transactional
    public CreatorVerification createVerification(CreatorVerification verification) {
        return repository.save(verification);
    }

    @Transactional
    public CreatorVerification createOrResubmit(Long creatorId, CreatorVerificationRequest request) {
        // Ensure creator record exists
        Creator creatorEntity = creatorRepository.findByUser_Id(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found for user id: " + creatorId));

        Optional<CreatorVerification> existingOpt = repository.findByCreator_User_Id(creatorId);
        if (existingOpt.isPresent()) {
            CreatorVerification existing = existingOpt.get();
            if (existing.getStatus() == VerificationStatus.PENDING || existing.getStatus() == VerificationStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An active verification already exists for this creator");
            }
            // REJECTED → allow resubmission by updating existing record
            existing.setLegalFirstName(request.getLegalFirstName());
            existing.setLegalLastName(request.getLegalLastName());
            existing.setDateOfBirth(request.getDateOfBirth());
            existing.setCountry(request.getCountry());
            existing.setDocumentType(request.getDocumentType());
            existing.setIdDocumentUrl(request.getIdDocumentUrl());
            existing.setDocumentBackUrl(request.getDocumentBackUrl());
            existing.setSelfieDocumentUrl(request.getSelfieDocumentUrl());
            existing.setStatus(VerificationStatus.PENDING);
            existing.setRejectionReason(null);
            return repository.save(existing);
        }

        // No existing record → create fresh PENDING verification
        CreatorVerification entity = CreatorVerification.builder()
                .creator(creatorEntity)
                .legalFirstName(request.getLegalFirstName())
                .legalLastName(request.getLegalLastName())
                .dateOfBirth(request.getDateOfBirth())
                .country(request.getCountry())
                .documentType(request.getDocumentType())
                .idDocumentUrl(request.getIdDocumentUrl())
                .documentBackUrl(request.getDocumentBackUrl())
                .selfieDocumentUrl(request.getSelfieDocumentUrl())
                .status(VerificationStatus.PENDING)
                .build();
        return repository.save(entity);
    }

    public Optional<CreatorVerification> findByCreatorId(Long creatorId) {
        return repository.findByCreator_User_Id(creatorId);
    }

    public CreatorVerification getVerificationById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreatorVerification not found with id: " + id));
    }

    public CreatorVerification getVerificationByCreatorId(Long creatorId) {
        return repository.findByCreator_User_Id(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("CreatorVerification not found for creator id: " + creatorId));
    }

    public List<CreatorVerification> getVerificationsByStatus(VerificationStatus status) {
        return repository.findByStatus(status);
    }

    public Page<CreatorVerification> getAllVerifications(VerificationStatus status, Pageable pageable) {
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional
    public CreatorVerification updateStatus(Long id, VerificationStatus status, String rejectionReason) {
        CreatorVerification verification = getVerificationById(id);
        verification.setStatus(status);
        if (status == VerificationStatus.REJECTED) {
            verification.setRejectionReason(rejectionReason);
        } else {
            verification.setRejectionReason(null);
        }

        CreatorVerification saved = repository.save(verification);

        if (status == VerificationStatus.APPROVED) {
            User user = saved.getCreator().getUser();
            userService.upgradeToCreator(user.getEmail());
        }

        return saved;
    }
}
