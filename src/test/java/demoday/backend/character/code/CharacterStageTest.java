package demoday.backend.character.code;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterStageTest {

    @ParameterizedTest
    @CsvSource({
            "0.00,BABY",
            "99.99,BABY",
            "100.00,BASIC",
            "249.99,BASIC",
            "250.00,BIG_HAND",
            "1499.99,BIG_HAND",
            "1500.00,ROYAL",
            "999999.99,ROYAL"
    })
    void determinesStageFromStock(
            String stock,
            CharacterStage expected
    ) {
        assertThat(
                CharacterStage.fromStock(
                        new BigDecimal(stock)
                )
        ).isEqualTo(expected);
    }
}
