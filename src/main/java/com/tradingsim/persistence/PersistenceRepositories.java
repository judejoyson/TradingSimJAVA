package com.tradingsim.persistence;

import com.tradingsim.account.AccountMode;
import com.tradingsim.security.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

interface PaperAccountRepository extends JpaRepository<PaperAccountEntity, Long> {
    Optional<PaperAccountEntity> findByOwner(AppUser owner);

    List<PaperAccountEntity> findByMode(AccountMode mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaperAccountEntity> findForUpdateByOwner(AppUser owner);
}

interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    List<PositionEntity> findByAccountOrderBySymbol(PaperAccountEntity account);

    Optional<PositionEntity> findByAccountAndSymbol(PaperAccountEntity account, String symbol);

    void deleteByAccount(PaperAccountEntity account);
}

interface PersistedTradeRepository extends JpaRepository<PersistedTrade, Long> {
    List<PersistedTrade> findTop50ByOwnerOrderByExecutedAtDesc(AppUser owner);

    List<PersistedTrade> findByOwnerOrderByExecutedAtAsc(AppUser owner);

    void deleteByOwner(AppUser owner);
}

interface SavedBacktestRepository extends JpaRepository<SavedBacktest, Long> {
    List<SavedBacktest> findTop50ByOwnerOrderByCreatedAtDesc(AppUser owner);
}

interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findTop100ByOwnerOrderByCreatedAtDesc(AppUser owner);
}
