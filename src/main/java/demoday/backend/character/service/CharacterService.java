package demoday.backend.character.service;

import demoday.backend.character.code.CharacterEffect;
import demoday.backend.character.code.CharacterErrorCode;
import demoday.backend.character.code.CharacterStage;
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
        var stage = CharacterStage.fromStock(member.getCurrentStock());
        var nextStage = stage.next();
        var highestEffect = CharacterEffect.highestFor(streak.currentStreak());
        var nextEffect = CharacterEffect.nextAfter(streak.currentStreak());
        return new CharacterResponse(streak.date(), member.getCurrentStock(), stage, stage.getDisplayName(), nextStage,
                nextStage == null ? null : nextStage.getMinimumStock(),
                nextStage == null ? null : nextStage.getMinimumStock().subtract(member.getCurrentStock()),
                streak.currentStreak(), highestEffect == null ? List.of() : List.of(highestEffect), nextEffect,
                nextEffect == null ? null : nextEffect.getMinimumStreak() - streak.currentStreak());
    }
}
