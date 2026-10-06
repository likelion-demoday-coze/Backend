package demoday.backend.character.code;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class CharacterStageTest {
    @ParameterizedTest
    @CsvSource({"0,BEGINNER", "80.00,BEGINNER", "999.99,BEGINNER", "1000.000,BIG_HAND",
            "1000.01,BIG_HAND", "9999.99,BIG_HAND", "10000.00,NOBLE", "10000.01,NOBLE"})
    void stageIncludesExactThresholdAndSupportsBelowStartingStock(String stock, CharacterStage expected) {
        assertThat(CharacterStage.fromStock(new BigDecimal(stock))).isEqualTo(expected);
    }
}
