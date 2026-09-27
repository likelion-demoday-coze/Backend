package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.dto.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.dto.DailyQuizSessionCreateResponse;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.domain.FishTransaction;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyQuizService {

    private static final int DAILY_QUIZ_QUESTION_COUNT = 5;
    private static final long DAILY_QUIZ_FISH_COST = 50L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final EnumSet<DailyQuizSessionStatus> ACTIVE_STATUSES =
            EnumSet.of(
                    DailyQuizSessionStatus.IN_PROGRESS,
                    DailyQuizSessionStatus.ORIGINAL_COMPLETED
            );

    private final MemberRepository memberRepository;
    private final MemberPassRepository memberPassRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final DailyQuizSessionRepository dailyQuizSessionRepository;
    private final DailyQuizSessionQuestionRepository sessionQuestionRepository;
    private final FishTransactionRepository fishTransactionRepository;

    @Transactional(readOnly = true)
    public List<DailyQuizCategoryResponse> getCategories() {
        return Arrays.stream(QuizCategory.values())
                .map(DailyQuizCategoryResponse::from)
                .toList();
    }

    @Transactional
    public DailyQuizSessionCreateResponse createSession(
            Long memberId,
            DailyQuizSessionCreateRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(KST);

        // 회원을 비관적 락으로 조회
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(GeneralErrorCode.NOT_FOUND)
                );

        // 기존 진행 중 세션 확인
        validateActiveSession(memberId, now);

        // 현재 활성화된 일주일 패스가 있는지 확인
        boolean passApplied = memberPassRepository.findActivePass(
                memberId,
                PassStatus.ACTIVE,
                now
        ).isPresent();

        // 없다면 생선 50개 보유 여부 확인
        if (!passApplied
                && member.getFishBalance() < DAILY_QUIZ_FISH_COST) {
            throw new ProjectException(
                    DailyQuizErrorCode.INSUFFICIENT_FISH
            );
        }

        // 선택한 카테고리에서 출제할 문제 조회
        List<QuizQuestion> questions =
                quizQuestionRepository.findAvailableQuestions(
                        memberId,
                        request.category(),
                        PageRequest.of(
                                0,
                                DAILY_QUIZ_QUESTION_COUNT
                        )
                );

        // 문제가 5개보다 적으면 INSUFFICIENT_QUESTIONS
        if (questions.size() < DAILY_QUIZ_QUESTION_COUNT) {
            throw new ProjectException(
                    DailyQuizErrorCode.INSUFFICIENT_QUESTIONS
            );
        }

        // 세션 생성
        DailyQuizSession session = DailyQuizSession.create(
                member,
                request.category(),
                member.getCurrentStock(),
                now,
                passApplied
        );

        dailyQuizSessionRepository.save(session);

        // 선정한 문제 5개 저장
        List<DailyQuizSessionQuestion> sessionQuestions =
                questions.stream()
                        .map(question ->
                                DailyQuizSessionQuestion.create(
                                        question,
                                        session
                                )
                        )
                        .toList();

        sessionQuestionRepository.saveAll(sessionQuestions);

        // 패스가 없다면 생선 50개 차감 후 기록
        if (!passApplied) {
            deductFishAndRecordTransaction(
                    member,
                    session,
                    now
            );
        }

        // 생성된 세션 정보 응답
        return DailyQuizSessionCreateResponse.from(session);
    }

    private void validateActiveSession(
            Long memberId,
            LocalDateTime now
    ) {
        dailyQuizSessionRepository
                .findFirstByMemberMemberIdAndStatusInOrderByStartedAtDesc(
                        memberId,
                        ACTIVE_STATUSES
                )
                .filter(session -> !session.isExpired(now))
                .ifPresent(session -> {
                    throw new ProjectException(
                            DailyQuizErrorCode.ACTIVE_SESSION_ALREADY_EXISTS
                    );
                });
    }

    private void deductFishAndRecordTransaction(
            Member member,
            DailyQuizSession session,
            LocalDateTime now
    ) {
        member.deductFish(DAILY_QUIZ_FISH_COST);

        FishTransaction transaction =
                FishTransaction.create(
                        member,
                        FishTransactionType.DAILY_QUIZ_COST,
                        -DAILY_QUIZ_FISH_COST,
                        member.getFishBalance(),
                        session.getDailyQuizSessionId(),
                        "DAILY_QUIZ_SESSION:"
                                + session.getDailyQuizSessionId(),
                        now
                );

        fishTransactionRepository.save(transaction);
    }
}
