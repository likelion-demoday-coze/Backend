package demoday.backend.trend.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrendGenerationFailure {
    NETWORK(true), RATE_LIMIT(true), PROVIDER_ERROR(true), INVALID_CONTENT(true), STORAGE_UNAVAILABLE(true),
    AUTHENTICATION(false), ACCOUNT_SUSPENDED(false), INSUFFICIENT_CREDITS(false),
    PROVIDER_REJECTED(false), BAD_REQUEST(false), NOT_CONFIGURED(false), STORAGE_ERROR(false);
    private final boolean retryable;
}
