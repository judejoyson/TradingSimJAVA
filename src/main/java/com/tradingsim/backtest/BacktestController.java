package com.tradingsim.backtest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/backtests")
public final class BacktestController {
    private final BacktestService backtestService;
    private final CsvBacktestDataService csvDataService;

    public BacktestController(
            BacktestService backtestService,
            CsvBacktestDataService csvDataService) {
        this.backtestService = backtestService;
        this.csvDataService = csvDataService;
    }

    @GetMapping("/options")
    public BacktestOptions options() {
        return backtestService.options();
    }

    @PostMapping
    public BacktestResult run(@Valid @RequestBody BacktestRequest request) {
        return backtestService.run(request);
    }

    @PostMapping(path = "/csv", consumes = "multipart/form-data")
    public BacktestResult runCsv(
            @Valid @RequestPart("request") BacktestRequest request,
            @RequestPart("file") MultipartFile file) {
        return backtestService.runUploaded(
                request,
                csvDataService.parse(file),
                file.getOriginalFilename());
    }
}
