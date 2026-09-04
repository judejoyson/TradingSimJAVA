package com.tradingsim.persistence;

import com.tradingsim.security.AppUser;
import com.tradingsim.security.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/journal")
public class JournalController {
    private final JournalEntryRepository entries;
    private final UserService users;

    public JournalController(JournalEntryRepository entries, UserService users) {
        this.entries = entries;
        this.users = users;
    }

    @GetMapping
    public List<JournalView> entries(Authentication authentication) {
        AppUser owner = users.require(authentication.getName());
        return entries.findTop100ByOwnerOrderByCreatedAtDesc(owner).stream()
                .map(JournalView::from)
                .toList();
    }

    @PostMapping
    public JournalView create(
            @Valid @RequestBody JournalRequest request,
            Authentication authentication) {
        AppUser owner = users.require(authentication.getName());
        return JournalView.from(entries.save(new JournalEntry(
                owner,
                request.title().trim(),
                request.notes().trim())));
    }
}
