package demoday.backend.character.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum CharacterStage {
    BEGINNER("초보냥", BigDecimal.ZERO),
    BIG_HAND("큰손냥", new BigDecimal("1000.00")),
    NOBLE("귀족냥", new BigDecimal("10000.00"));

    private final String displayName;
    private final BigDecimal minimumStock;

    /** 시초가 100 아래로 하락해도 기본 외형을 유지한다. 기준값은 해당 단계에 포함한다. */
    public static CharacterStage fromStock(BigDecimal stock) {
        if (stock == null || stock.signum() < 0) throw new IllegalArgumentException("주가는 0 이상이어야 합니다.");
        if (stock.compareTo(NOBLE.minimumStock) >= 0) return NOBLE;
        if (stock.compareTo(BIG_HAND.minimumStock) >= 0) return BIG_HAND;
        return BEGINNER;
    }

    public CharacterStage next() {
        return switch (this) {
            case BEGINNER -> BIG_HAND;
            case BIG_HAND -> NOBLE;
            case NOBLE -> null;
        };
    }
}
