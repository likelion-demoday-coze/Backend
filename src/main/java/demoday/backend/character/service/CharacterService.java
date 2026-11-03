package demoday.backend.character.service;

import demoday.backend.character.code.CharacterEffect;
import demoday.backend.character.code.CharacterErrorCode;
import demoday.backend.character.code.CharacterStage;
import demoday.backend.character.dto.CharacterEvolutionConfirmRequest;
import demoday.backend.character.dto.CharacterResponse;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.streak.service.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final MemberRepository members;
    private final StreakService streaks;

    /** 같은 DB 스냅샷에서 주가와 유효 스트릭을 읽으며 캐릭터 상태를 따로 저장하지 않는다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CharacterResponse getCurrent(Long memberId) {
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        var member = members.findById(memberId).orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(CharacterErrorCode.INACTIVE_MEMBER);
        var streak = streaks.getCurrent(memberId);
        var stage = member.getCharacterStage();
        var nextStage = stage.next();

        BigDecimal nextStageStock =
                stage == CharacterStage.BABY
                        || nextStage == null
                        ? null
                        : nextStage.getMinimumStock();

        BigDecimal stockToNextStage =
                nextStageStock == null
                        ? null
                        : nextStageStock.subtract(
                        member.getCurrentStock()
                );

        Integer correctAnswersToNextStage =
                stage == CharacterStage.BABY
                        ? 1
                        : null;

        var highestEffect =
                CharacterEffect.highestFor(
                        streak.currentStreak()
                );

        var nextEffect =
                CharacterEffect.nextAfter(
                        streak.currentStreak()
                );

        return new CharacterResponse(
                streak.date(),
                member.getCurrentStock(),
                stage,
                stage.getDisplayName(),
                nextStage,
                nextStageStock,
                stockToNextStage,
                correctAnswersToNextStage,
                streak.currentStreak(),
                highestEffect == null
                        ? List.of()
                        : List.of(highestEffect),
                nextEffect,
                nextEffect == null
                        ? null
                        : nextEffect.getMinimumStreak()
                        - streak.currentStreak(),
                member.getPendingEvolutionStage()
        );
    }

    @Transactional
    public void confirmEvolution(
            Long memberId,
            CharacterEvolutionConfirmRequest request
    ) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        if (request == null || request.stage() == null) {
            throw new ProjectException(
                    GeneralErrorCode.BAD_REQUEST
            );
        }

        var member = members.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(
                    CharacterErrorCode.INACTIVE_MEMBER
            );
        }

        member.confirmEvolution(request.stage());
    }
}
