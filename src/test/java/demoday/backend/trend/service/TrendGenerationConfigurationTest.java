package demoday.backend.trend.service;

import demoday.backend.trend.config.LocalTrendGenerationRunner;
import demoday.backend.trend.scheduler.TrendGenerationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrendGenerationConfigurationTest {
    private final TrendGenerationService generations = mock(TrendGenerationService.class);
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(TrendGenerationService.class, () -> generations)
            .withUserConfiguration(LocalTrendGenerationRunner.class, TrendGenerationScheduler.class);

    @Test void neitherAutomaticNorLocalGenerationIsEnabledByDefault() {
        contexts.run(context -> {
            assertThat(context).doesNotHaveBean(LocalTrendGenerationRunner.class);
            assertThat(context).doesNotHaveBean(TrendGenerationScheduler.class);
        });
        verifyNoInteractions(generations);
    }
    @Test void localStartupFlagCannotCreateRunnerInProductionProfile() {
        contexts.withPropertyValues("spring.profiles.active=prod", "app.economic-trends.local-generate-on-startup=true")
                .run(context -> assertThat(context).doesNotHaveBean(LocalTrendGenerationRunner.class));
        verifyNoInteractions(generations);
    }
    @Test void localProfileAndExplicitFlagEnableOneStartupGeneration() {
        when(generations.runLatestDueSlot()).thenReturn(TrendGenerationService.Result.SUCCESS);
        contexts.withPropertyValues("spring.profiles.active=local", "app.economic-trends.local-generate-on-startup=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalTrendGenerationRunner.class);
                    context.getBean(LocalTrendGenerationRunner.class).run(new DefaultApplicationArguments());
                });
        verify(generations, times(1)).runLatestDueSlot();
    }
}
