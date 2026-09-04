package com.tradingsim.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsim.backtest.BacktestResult;
import com.tradingsim.security.AppUser;
import com.tradingsim.security.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BacktestHistoryService {
    private final SavedBacktestRepository repository;
    private final UserService users;
    private final ObjectMapper objectMapper;

    public BacktestHistoryService(
            SavedBacktestRepository repository,
            UserService users,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BacktestResult save(String email, BacktestResult result) {
        AppUser owner = users.require(email);
        try {
            repository.save(new SavedBacktest(
                    owner,
                    result.symbol(),
                    result.strategy(),
                    objectMapper.writeValueAsString(result)));
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Backtest result could not be saved.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<SavedBacktestView> history(String email) {
        AppUser owner = users.require(email);
        return repository.findTop50ByOwnerOrderByCreatedAtDesc(owner).stream()
                .map(this::view)
                .toList();
    }

    private SavedBacktestView view(SavedBacktest saved) {
        try {
            return new SavedBacktestView(
                    saved.getId(),
                    saved.getSymbol(),
                    saved.getStrategy(),
                    saved.getCreatedAt(),
                    objectMapper.readTree(saved.getResultJson()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Saved backtest data is invalid.", exception);
        }
    }
}
