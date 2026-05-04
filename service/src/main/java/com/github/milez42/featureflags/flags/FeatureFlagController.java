package com.github.milez42.featureflags.flags;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class FeatureFlagController {
    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @PostMapping("/flags")
    public ResponseEntity<FeatureFlagResponse> create(@Valid @RequestBody CreateFeatureFlagRequest request) {
        FeatureFlagResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/flags/" + response.flagKey()))
                .body(response);
    }

    @GetMapping("/flags/{flagKey}")
    public FeatureFlagResponse get(@PathVariable String flagKey) {
        return service.get(flagKey);
    }

    @PatchMapping("/flags/{flagKey}")
    public FeatureFlagResponse update(
            @PathVariable String flagKey,
            @Valid @RequestBody UpdateFeatureFlagRequest request
    ) {
        return service.update(flagKey, request);
    }

    @PostMapping("/evaluate")
    public EvaluateFeatureFlagResponse evaluate(@Valid @RequestBody EvaluateFeatureFlagRequest request) {
        return service.evaluate(request);
    }
}
