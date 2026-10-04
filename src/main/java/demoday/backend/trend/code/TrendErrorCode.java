package demoday.backend.trend.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrendErrorCode implements BaseErrorCode {
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "TREND_403_1", "탈퇴한 회원은 경제 트렌드를 이용할 수 없습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
