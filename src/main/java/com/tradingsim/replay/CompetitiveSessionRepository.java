package com.tradingsim.replay;

import com.tradingsim.security.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CompetitiveSessionRepository
        extends JpaRepository<CompetitiveSessionEntity, Long> {
    Optional<CompetitiveSessionEntity> findByOwner(AppUser owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompetitiveSessionEntity> findForUpdateByOwner(AppUser owner);
}
