package demoday.backend.member.domain;

import demoday.backend.character.code.CharacterErrorCode;
import demoday.backend.character.code.CharacterStage;
import demoday.backend.global.exception.ProjectException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCharacterStageTest {

    @Test
    void newMemberStartsAsBasicWithoutPendingEvolution() {
        Member member = Member.create(1L, "기본회원");

        assertThat(member.getCurrentStock()).isEqualByComparingTo("100.00");
        assertThat(member.getCharacterStage()).isEqualTo(CharacterStage.BASIC);
        assertThat(member.getPendingEvolutionStage()).isNull();
    }

    @Test
    void latestHigherEvolutionReplacesOlderUnconfirmedStage() {
        Member member = Member.create(1L, "진화회원");

        member.changeStock(new BigDecimal("250.00"), CharacterStage.BIG_HAND);
        member.changeStock(new BigDecimal("1500.00"), CharacterStage.ROYAL);

        assertThat(member.getCharacterStage()).isEqualTo(CharacterStage.ROYAL);
        assertThat(member.getPendingEvolutionStage()).isEqualTo(CharacterStage.ROYAL);
    }

    @Test
    void downgradeBelowPendingStageRemovesObsoletePopup() {
        Member member = Member.create(1L, "하락회원");
        member.changeStock(new BigDecimal("250.00"), CharacterStage.BIG_HAND);

        member.changeStock(new BigDecimal("200.00"), CharacterStage.BASIC);

        assertThat(member.getCharacterStage()).isEqualTo(CharacterStage.BASIC);
        assertThat(member.getPendingEvolutionStage()).isNull();
    }

    @Test
    void onlyMatchingPendingEvolutionCanBeConfirmedAndReplayIsIdempotent() {
        Member member = Member.create(1L, "확인회원");
        member.changeStock(new BigDecimal("250.00"), CharacterStage.BIG_HAND);

        assertThatThrownBy(() -> member.confirmEvolution(CharacterStage.BASIC))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CharacterErrorCode.EVOLUTION_STAGE_MISMATCH));
        assertThat(member.getPendingEvolutionStage()).isEqualTo(CharacterStage.BIG_HAND);

        member.confirmEvolution(CharacterStage.BIG_HAND);
        member.confirmEvolution(CharacterStage.BIG_HAND);

        assertThat(member.getPendingEvolutionStage()).isNull();
    }
}
