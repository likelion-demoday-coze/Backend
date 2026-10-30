package demoday.backend.global.config;

import demoday.backend.global.logging.JobRunContext;
import demoday.backend.stock.scheduler.StockSnapshotScheduler;
import demoday.backend.stock.service.StockSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlueGreenStateIntegrationTest {
    private DriverManagerDataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:bg-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private AnnotationConfigApplicationContext node(DataSource db, StockSnapshotService service) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> db);
        context.register(SchedulingConfig.class);
        context.registerBean(StockSnapshotScheduler.class, () -> new StockSnapshotScheduler(
                service, Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC),
                new JobRunContext(), "2026-10-04"));
        context.refresh();
        return context;
    }

    @Test
    @Timeout(20)
    void startupAndMidnightOnDifferentNodesShareTheSameDatabaseLock() throws Exception {
        var db = database();
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(db);
        var service = mock(StockSnapshotService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(service).capture(any());
        var executor = Executors.newSingleThreadExecutor();
        try (var blue = node(db, service); var green = node(db, service)) {
            var first = executor.submit(() -> blue.getBean(StockSnapshotScheduler.class).captureOnStartup());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            green.getBean(StockSnapshotScheduler.class).captureAtMidnight();
            verify(service, times(1)).capture(any());
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            // The minimum lease also skips a second node arriving just after completion.
            green.getBean(StockSnapshotScheduler.class).captureOnStartup();
            verify(service, times(1)).capture(any());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void authenticatedSessionSavedByBlueCanBeReadByGreen() {
        var db = database();
        new ResourceDatabasePopulator(new ClassPathResource("org/springframework/session/jdbc/schema-h2.sql")).execute(db);
        var jdbc = new JdbcTemplate(db);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(db));
        var blue = new JdbcIndexedSessionRepository(jdbc, tx);
        var green = new JdbcIndexedSessionRepository(jdbc, tx);
        assertSessionSharing(blue, green);
    }

    private <S extends Session> void assertSessionSharing(SessionRepository<S> blue, SessionRepository<S> green) {
        var session = blue.createSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(42L, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))));
        blue.save(session);
        var restored = green.findById(session.getId());
        assertThat(restored).isNotNull();
        SecurityContextImpl security = restored.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(security.getAuthentication().getPrincipal()).isEqualTo(42L);
        assertThat(security.getAuthentication().isAuthenticated()).isTrue();
        green.deleteById(restored.getId());
        assertThat(blue.findById(session.getId())).isNull();
    }
}
