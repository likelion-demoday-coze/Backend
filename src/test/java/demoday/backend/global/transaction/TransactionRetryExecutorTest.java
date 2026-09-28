package demoday.backend.global.transaction;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionRetryExecutorTest {

    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final TransactionRetryExecutor executor = new TransactionRetryExecutor(manager);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackBeforeOpeningNextTransaction(boolean legacyDeadlockException) {
        TransactionStatus first = mock(TransactionStatus.class);
        TransactionStatus second = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(first, second);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw legacyDeadlockException
                        ? new DeadlockLoserDataAccessException("deadlock", null)
                        : new CannotAcquireLockException("deadlock");
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        var order = inOrder(manager);
        order.verify(manager).getTransaction(any());
        order.verify(manager).rollback(first);
        order.verify(manager).getTransaction(any());
        order.verify(manager).commit(second);
        verify(manager, never()).commit(first);
    }

    @Test
    void retriesFailureReportedAtCommit() {
        TransactionStatus first = mock(TransactionStatus.class);
        TransactionStatus second = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(first, second);
        doThrow(new CannotAcquireLockException("commit lock failure")).when(manager).commit(first);
        AtomicInteger attempts = new AtomicInteger();

        assertThat(executor.execute(attempts::incrementAndGet)).isEqualTo(2);
        verify(manager).commit(second);
    }

    @Test
    void stopsAfterThreeAttempts() {
        when(manager.getTransaction(any())).thenAnswer(invocation -> mock(TransactionStatus.class));
        AtomicInteger attempts = new AtomicInteger();
        CannotAcquireLockException failure = new CannotAcquireLockException("busy");

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(attempts).hasValue(3);
        verify(manager, times(3)).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void doesNotRetryIntegrityOrBusinessFailures() {
        when(manager.getTransaction(any())).thenAnswer(invocation -> mock(TransactionStatus.class));
        RuntimeException[] failures = {
                new DataIntegrityViolationException("constraint"),
                new ProjectException(GeneralErrorCode.CONFLICT)
        };
        for (RuntimeException failure : failures) {
            AtomicInteger attempts = new AtomicInteger();
            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw failure;
            })).isSameAs(failure);
            assertThat(attempts).hasValue(1);
        }
    }

    @Test
    void rejectsAnAlreadyActiveTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatIllegalStateException().isThrownBy(() -> executor.execute(() -> "invalid"));
            verifyNoInteractions(manager);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void preservesInterruptAndStopsRetrying() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        AtomicInteger attempts = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new CannotAcquireLockException("busy");
            })).isInstanceOf(CannotAcquireLockException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(attempts).hasValue(1);
        } finally {
            Thread.interrupted();
        }
    }
}
