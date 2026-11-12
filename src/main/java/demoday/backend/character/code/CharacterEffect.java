package demoday.backend.character.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CharacterEffect {
    PAW_PRINT("발자국", 2),
    GLOWING_BASE("동전", 3),
    FEATHER("깃털", 5),
    AURA("은화", 7),
    GINKGO_LEAF("은행잎", 10),
    GOLDEN_HALO("금화", 14),
    GOLDEN_FLOWER("황금꽃", 17),
    COIN_BACKGROUND("시크릿", 21);

    private final String displayName;
    private final int minimumStreak;

    public static CharacterEffect highestFor(int streak) {
        CharacterEffect highest = null;
        for (CharacterEffect effect : values()) {
            if (streak >= effect.minimumStreak) highest = effect;
        }
        return highest;
    }

    public static CharacterEffect nextAfter(int streak) {
        for (CharacterEffect effect : values()) {
            if (streak < effect.minimumStreak) return effect;
        }
        return null;
    }
}
