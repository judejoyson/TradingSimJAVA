package com.tradingsim.persistence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/competitive/leaderboard")
public class CompetitiveLeaderboardController {
    private final CompetitiveLeaderboardService leaderboards;

    public CompetitiveLeaderboardController(CompetitiveLeaderboardService leaderboards) {
        this.leaderboards = leaderboards;
    }

    @GetMapping
    public List<LeaderboardEntry> leaderboard(
            @RequestParam(defaultValue = "RETURN") LeaderboardMetric sort) {
        return leaderboards.leaderboard(sort);
    }
}
