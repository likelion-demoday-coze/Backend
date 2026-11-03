package demoday.backend.character.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum CharacterStage {

    BABY(
            "아기냥",
            BigDecimal.ZERO
    ),
    BASIC(
            "기본냥",
            new BigDecimal("100.00")
    ),
    BIG_HAND(
            "큰손냥",
            new BigDecimal("250.00")
    ),
    ROYAL(
            "왕족냥",
            new BigDecimal("1500.00")
    );

    private final String displayName;
    private final BigDecimal minimumStock;

    public static CharacterStage fromStock(BigDecimal stock) {
        if (stock == null || stock.signum() < 0) {
            throw new IllegalArgumentException(
                    "주가는 0 이상이어야 합니다."
            );
        }

        if (stock.compareTo(ROYAL.minimumStock) >= 0) {
            return ROYAL;
        }

        if (stock.compareTo(BIG_HAND.minimumStock) >= 0) {
            return BIG_HAND;
        }

        if (stock.compareTo(BASIC.minimumStock) >= 0) {
            return BASIC;
        }

        return BABY;
    }

    public static CharacterStage higherOf(
            CharacterStage first,
            CharacterStage second
    ) {
        if (first == null || second == null) {
            throw new IllegalArgumentException(
                    "캐릭터 단계는 필수입니다."
            );
        }

        return first.ordinal() >= second.ordinal()
                ? first
                : second;
    }

    public CharacterStage next() {
        return switch (this) {
            case BABY -> BASIC;
            case BASIC -> BIG_HAND;
            case BIG_HAND -> ROYAL;
            case ROYAL -> null;
        };
    }

    public boolean isHigherThan(CharacterStage other) {
        return other != null
                && this.ordinal() > other.ordinal();
    }

    public boolean isLowerThan(CharacterStage other) {
        return other != null
                && this.ordinal() < other.ordinal();
    }
}
