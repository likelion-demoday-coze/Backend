package demoday.backend.global.transaction;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** DB 작업 전체를 가장 바깥 경계에서 재실행한다. 콜백 안에 외부 API 호출을 넣지 않는다. */
@Component
public class TransactionRetryExecutor {

    private static final int MAX_ATTEMPTS = 3;
    private final TransactionTemplate transactionTemplate;

    public TransactionRetryExecutor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("트랜잭션 재시도는 기존 트랜잭션 밖에서 업무 전체를 감싸야 합니다.");
        }

        for (int attempt = 1; ; attempt++) {
            try {
                // execute가 롤백/커밋을 끝낸 다음에만 catch로 진입하므로 다음 시도는 새 트랜잭션이다.
                return transactionTemplate.execute(status -> operation.get());
            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException exception) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            }
        }
    }

    private void pauseBeforeRetry(int attempt) {
        long minimumDelayMillis = 25L << (attempt - 1);
        try {
            // 롤백 후 잠금을 놓은 상태에서 지수 백오프 + 지터로 반복 충돌을 줄인다.
            Thread.sleep(ThreadLocalRandom.current().nextLong(minimumDelayMillis, minimumDelayMillis * 2));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CannotAcquireLockException("트랜잭션 재시도 대기가 중단되었습니다.", exception);
        }
    }
}
