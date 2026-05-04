package com.github.milez42.featureflags.flags;

import com.github.milez42.featureflags.error.HttpException;
import org.springframework.http.HttpStatus;

public class FeatureFlagDuplicateException extends HttpException {
    public FeatureFlagDuplicateException(String flagKey) {
        super("Feature flag already exists: " + flagKey);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String title() {
        return "Feature flag conflict";
    }
}
