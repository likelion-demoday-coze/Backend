package demoday.backend.trend.client;

import demoday.backend.trend.code.TrendGenerationFailure;
import lombok.Getter;

/** 응답 원문·API 키 대신 정제된 오류 유형만 보존한다. */
@Getter
public class TrendGenerationException extends RuntimeException {
    private final TrendGenerationFailure failure;
    public TrendGenerationException(TrendGenerationFailure failure) {
        super(failure.name());
        this.failure = failure;
    }
}
