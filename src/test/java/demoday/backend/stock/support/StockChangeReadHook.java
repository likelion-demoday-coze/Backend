package demoday.backend.stock.support;

import demoday.backend.stock.domain.StockChange;
import demoday.backend.stock.repository.StockChangeRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.Advised;

import java.util.Optional;

/** 실제 Spring Data 조회 체인을 실행한 뒤 테스트의 대기/결과 조작만 추가한다. */
public final class StockChangeReadHook implements MethodInterceptor, AutoCloseable {

    private final Advised repositoryProxy;
    private final AfterRead afterRead;

    private StockChangeReadHook(StockChangeRepository repository, AfterRead afterRead) {
        this.repositoryProxy = (Advised) repository;
        this.afterRead = afterRead;
        repositoryProxy.addAdvice(0, this);
    }

    public static StockChangeReadHook install(StockChangeRepository repository, AfterRead afterRead) {
        return new StockChangeReadHook(repository, afterRead);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (invocation.getMethod().getName().equals("findByIdempotencyKey")) {
            return afterRead.apply((Optional<StockChange>) result);
        }
        return result;
    }

    @Override
    public void close() {
        repositoryProxy.removeAdvice(this);
    }

    @FunctionalInterface
    public interface AfterRead {
        Optional<StockChange> apply(Optional<StockChange> result) throws Exception;
    }
}

