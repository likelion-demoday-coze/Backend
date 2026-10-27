package demoday.backend.trend.config;

import demoday.backend.trend.client.LinerTrendClient;
import demoday.backend.trend.code.TrendCategory;
import demoday.backend.trend.dto.GeneratedTrendContent;
import demoday.backend.trend.dto.RecentTrendContent;
import demoday.backend.trend.service.RecentTrendContentService;
import demoday.backend.trend.service.TrendContentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalTrendPreviewRunnerTest {
    @TempDir Path temporary;

    @Test void previewRunsAgainOnSameDayAndWritesComparisonWithoutProductionGeneration() throws Exception {
        var date = LocalDate.of(2026, 10, 5);
        var recent = mock(RecentTrendContentService.class);
        var client = mock(LinerTrendClient.class);
        var previous = List.of(new RecentTrendContent(date, "기존 이슈", "기존 요약", List.of("https://example.com/old")));
        var generated = new GeneratedTrendContent(java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(i -> new GeneratedTrendContent.Item(TrendCategory.OTHER, "새 이슈 " + i, "새 요약",
                        List.of(new GeneratedTrendContent.Term("경제", "설명")),
                        List.of(new GeneratedTrendContent.Reference("출처", "https://example.com/" + i, "기관", date))))
                .toList());
        when(recent.getRecent(date, true)).thenReturn(previous);
        when(client.generate(date, previous)).thenReturn(generated);
        var output = temporary.resolve("comparison.json");
        var mapper = JsonMapper.builder().build();
        var runner = new LocalTrendPreviewRunner(recent, client, new TrendContentValidator(),
                Clock.fixed(Instant.parse("2026-10-04T23:00:00Z"), ZoneOffset.UTC), mapper, output.toString());
        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());
        var report = mapper.readTree(Files.readString(output));
        assertThat(report.path("date").asText()).isEqualTo("2026-10-05");
        assertThat(report.path("recentContent").get(0).path("title").asText()).isEqualTo("기존 이슈");
        assertThat(report.path("generatedContent").path("items").size()).isEqualTo(3);
        verify(client, times(2)).generate(date, previous);
        verify(recent, times(2)).getRecent(date, true);
        verifyNoMoreInteractions(client, recent);
    }

    @Test void previewIsDisabledByDefaultAndCannotRunInProductionProfile() {
        var contexts = new ApplicationContextRunner().withUserConfiguration(LocalTrendPreviewRunner.class);
        contexts.run(context -> assertThat(context).doesNotHaveBean(LocalTrendPreviewRunner.class));
        contexts.withPropertyValues("spring.profiles.active=prod", "app.economic-trends.local-preview-on-startup=true")
                .run(context -> assertThat(context).doesNotHaveBean(LocalTrendPreviewRunner.class));
    }
}
