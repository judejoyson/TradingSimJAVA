package com.tradingsim.backtest;

import com.tradingsim.persistence.BacktestHistoryService;
import com.tradingsim.persistence.SavedBacktestView;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/backtests")
public final class BacktestController {
    private final BacktestService backtestService;
    private final CsvBacktestDataService csvDataService;
    private final BacktestHistoryService history;

    public BacktestController(
            BacktestService backtestService,
            CsvBacktestDataService csvDataService,
            BacktestHistoryService history) {
        this.backtestService = backtestService;
        this.csvDataService = csvDataService;
        this.history = history;
    }

    @GetMapping("/options")
    public BacktestOptions options() {
        return backtestService.options();
    }

    @PostMapping
    public BacktestResult run(
            @Valid @RequestBody BacktestRequest request,
            Authentication authentication) {
        BacktestResult result = backtestService.run(request);
        return isSignedIn(authentication)
                ? history.save(authentication.getName(), result)
                : result;
    }

    @PostMapping(path = "/csv", consumes = "multipart/form-data")
    public BacktestResult runCsv(
            @Valid @RequestPart("request") BacktestRequest request,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        BacktestResult result = backtestService.runUploaded(
                request,
                csvDataService.parse(file),
                file.getOriginalFilename());
        return isSignedIn(authentication)
                ? history.save(authentication.getName(), result)
                : result;
    }

    @GetMapping("/history")
    public List<SavedBacktestView> history(Authentication authentication) {
        return history.history(authentication.getName());
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
