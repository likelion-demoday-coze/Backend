package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.dto.result.DailyQuizResultQuestionResponse;
import demoday.backend.dailyquiz.dto.result.DailyQuizResultResponse;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.repository.QuizOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static demoday.backend.dailyquiz.policy.DailyQuizPolicy.QUESTIONS_PER_SESSION;

@Service
@RequiredArgsConstructor
public class DailyQuizResultService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyQuizSessionRepository dailyQuizSessionRepository;
    private final DailyQuizAttemptRepository dailyQuizAttemptRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final Clock clock;

    // 원본 풀이 결과 팝업과 최종 총정리 화면에 필요한 데이터 조회
    @Transactional(readOnly = true)
    public DailyQuizResultResponse getResult(
            Long memberId,
            Long sessionId
    ) {

        // 본인 세션 조회
        DailyQuizSession session = dailyQuizSessionRepository
                .findByDailyQuizSessionIdAndMemberMemberId(sessionId, memberId)
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.SESSION_NOT_FOUND)
                );

        // 세션 상태 확인
        if (session.getStatus() != DailyQuizSessionStatus.ORIGINAL_COMPLETED
                && session.getStatus() != DailyQuizSessionStatus.COMPLETED) {
            throw new ProjectException(
                    DailyQuizErrorCode.INVALID_SESSION_STATE
            );
        }

        // 원본 답안 5개 확인
        List<DailyQuizAttempt> originalAttempts = dailyQuizAttemptRepository
                .findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
                        sessionId,
                        DailyQuizAttemptType.ORIGINAL
                );

        if (originalAttempts.size() != QUESTIONS_PER_SESSION) {
            throw new ProjectException(
                    DailyQuizErrorCode.INVALID_SESSION_QUESTION_COUNT
            );
        }

        // 재풀이 답안 조회
        List<DailyQuizAttempt> retryAttempts = dailyQuizAttemptRepository
                .findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
                        sessionId,
                        DailyQuizAttemptType.RETRY
                );

        Map<Long, DailyQuizAttempt> retryAttemptBySessionQuestionId =
                retryAttempts.stream()
                        .collect(Collectors.toMap(
                                attempt -> attempt.getSessionQuestion()
                                        .getSessionQuestionId(),
                                attempt -> attempt
                        ));

        List<Long> questionIds = originalAttempts.stream()
                .map(attempt -> attempt.getSessionQuestion()
                        .getQuestion()
                        .getQuestionId())
                .toList();

        // 정답 선택지 일괄 조회
        Map<Long, QuizOption> correctOptionByQuestionId =
                quizOptionRepository
                        .findAllByQuestionQuestionIdInOrderByQuestionQuestionIdAscOptionNumberAsc(
                                questionIds
                        )
                        .stream()
                        .filter(option ->
                                Boolean.TRUE.equals(
                                        option.getCorrect()
                                )
                        )
                        .collect(
                                Collectors.toMap(
                                        option ->
                                                option.getQuestion()
                                                        .getQuestionId(),
                                        option -> option
                                )
                        );

        if (correctOptionByQuestionId.size()
                != QUESTIONS_PER_SESSION) {
            throw new ProjectException(
                    DailyQuizErrorCode.CORRECT_OPTION_NOT_FOUND
            );
        }

        // 문제별 주가 변화 및 결과 생성
        List<DailyQuizResultQuestionResponse> questionResponses =
                new ArrayList<>();

        for (DailyQuizAttempt originalAttempt : originalAttempts) {
            Long sessionQuestionId = originalAttempt
                    .getSessionQuestion()
                    .getSessionQuestionId();

            Long questionId = originalAttempt
                    .getSessionQuestion()
                    .getQuestion()
                    .getQuestionId();

            questionResponses.add(
                    DailyQuizResultQuestionResponse.of(
                            originalAttempt,
                            correctOptionByQuestionId.get(questionId),
                            retryAttemptBySessionQuestionId.get(
                                    sessionQuestionId
                            ),
                            originalAttempt.getStockAfter()
                    )
            );
        }

        // 정답 및 오답 개수 계산
        int correctCount = (int) originalAttempts.stream()
                .filter(attempt -> Boolean.TRUE.equals(attempt.getCorrect()))
                .count();

        int incorrectCount = QUESTIONS_PER_SESSION - correctCount;

        BigDecimal endStock = session.getEndStock();

        // 주가 결과 계산
        BigDecimal totalProfit = endStock
                .subtract(session.getStartStock())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalReturnPercent = totalProfit
                .divide(session.getStartStock(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return DailyQuizResultResponse.of(
                session,
                correctCount,
                incorrectCount,
                retryAttempts.size(),
                endStock,
                totalProfit,
                totalReturnPercent,
                questionResponses
        );
    }

    // 사용자가 재풀이하지 않고 NEXT 눌렀을 때
    @Transactional
    public DailyQuizResultResponse completeSession(
            Long memberId,
            Long sessionId
    ) {
        // 세션 락 조회
        DailyQuizSession session = findSessionForUpdate(sessionId, memberId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));

        // 이미 완료된 세션 처리
        if (session.getStatus() == DailyQuizSessionStatus.COMPLETED) {
            return getResult(memberId, sessionId);
        }

        // 만료 검사
        validateSessionNotExpired(session, now);

        // 원본 완료 상태 확인
        if (session.getStatus() != DailyQuizSessionStatus.ORIGINAL_COMPLETED) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_SESSION_STATE);
        }

        // 종료 주가 확정
        session.complete();

        // 최종 결과 반환
        return getResult(memberId, sessionId);
    }

    private DailyQuizSession findSessionForUpdate(
            Long sessionId,
            Long memberId
    ) {
        return dailyQuizSessionRepository
                .findByIdAndMemberIdForUpdate(sessionId, memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                DailyQuizErrorCode.SESSION_NOT_FOUND
                        )
                );
    }

    private void validateSessionNotExpired(
            DailyQuizSession session,
            LocalDateTime now
    ) {
        if (session.isExpired(now)) {
            throw new ProjectException(
                    DailyQuizErrorCode.SESSION_EXPIRED
            );
        }
    }
}
