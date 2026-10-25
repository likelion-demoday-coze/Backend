package demoday.backend.character.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CharacterEffect {
    GLOWING_BASE("빛나는 받침", 3),
    AURA("은은한 오라", 7),
    GOLDEN_HALO("금빛 후광", 14),
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
