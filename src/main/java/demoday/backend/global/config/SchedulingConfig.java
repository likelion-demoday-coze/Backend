package demoday.backend.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
public class SchedulingConfig {
    // Keep task execution separate from the lease renewal executor. Otherwise
    // Spring may select that executor for @Scheduled and block lock renewals.
    @Bean
    public GracefulJobExecutor jobExecutor(LockProvider lockProvider) {
        return new GracefulJobExecutor();
    }

    @Bean
    public SchedulingConfigurer jobSchedulingConfigurer(GracefulJobExecutor jobExecutor) {
        return registrar -> registrar.setScheduler(jobExecutor);
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService shedLockHeartbeat() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "shedlock-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource, ScheduledExecutorService shedLockHeartbeat) {
        var jdbc = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
        return new KeepAliveLockProvider(jdbc, shedLockHeartbeat);
    }
}
