package demoday.backend.ranking.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RankingErrorCode implements BaseErrorCode {

    INVALID_PAGE(
            HttpStatus.BAD_REQUEST,
            "RANKING_400_1",
            "페이지 번호와 크기가 올바르지 않습니다."
    ),
    INVALID_SETTLEMENT_DATE(
            HttpStatus.BAD_REQUEST,
            "RANKING_400_2",
            "랭킹을 확정할 수 없는 날짜입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
