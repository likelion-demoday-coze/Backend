package demoday.backend.trend.code;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 퀴즈의 8개 분류 코드를 공유하되 PREVIEW 없이 트렌드에만 OTHER를 허용한다. */
@Getter
@RequiredArgsConstructor
public enum TrendCategory {
    MACRO_ECONOMY("거시경제"),
    FINANCIAL_MARKET("금융시장"),
    STOCK_INVESTMENT("주식/투자"),
    INTEREST_BOND("금리/채권"),
    EXCHANGE_GLOBAL_ECONOMY("환율/국제경제"),
    REAL_ESTATE("부동산"),
    CORPORATE_FINANCE("기업/재무"),
    LIVING_ECONOMY("생활경제"),
    OTHER("기타");

    private final String displayName;

    // 숫자를 enum 순서로 해석하거나 임의 코드를 OTHER로 숨기지 않는다.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TrendCategory fromCode(String code) { return valueOf(code); }

    public static TrendCategory forLegacy(TrendCategory category) { return category == null ? OTHER : category; }
}
