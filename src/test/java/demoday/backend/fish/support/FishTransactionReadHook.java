package demoday.backend.fish.support;

import demoday.backend.fish.domain.FishTransaction;
import demoday.backend.fish.repository.FishTransactionRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.Advised;

import java.util.Optional;

/** 실제 Spring Data 조회 체인을 실행한 뒤 테스트의 대기/결과 조작만 추가한다. */
public final class FishTransactionReadHook implements MethodInterceptor, AutoCloseable {

    private final Advised repositoryProxy;
    private final AfterRead afterRead;

    private FishTransactionReadHook(FishTransactionRepository repository, AfterRead afterRead) {
        this.repositoryProxy = (Advised) repository;
        this.afterRead = afterRead;
        repositoryProxy.addAdvice(0, this);
    }

    public static FishTransactionReadHook install(FishTransactionRepository repository, AfterRead afterRead) {
        return new FishTransactionReadHook(repository, afterRead);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (invocation.getMethod().getName().equals("findByIdempotencyKey")) {
            return afterRead.apply((Optional<FishTransaction>) result);
        }
        return result;
    }

    @Override
    public void close() {
        repositoryProxy.removeAdvice(this);
    }

    @FunctionalInterface
    public interface AfterRead {
        Optional<FishTransaction> apply(Optional<FishTransaction> result) throws Exception;
    }
}
