package demoday.backend.trend.config;

import demoday.backend.trend.client.LinerTrendClient;
import demoday.backend.trend.client.TrendGenerationException;
import demoday.backend.trend.service.RecentTrendContentService;
import demoday.backend.trend.service.TrendContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** 로컬에서만 실제 API를 한 번 호출하고, DB를 변경하지 않고 비교 결과를 파일로 저장한다. */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.economic-trends.local-preview-on-startup", havingValue = "true")
public class LocalTrendPreviewRunner implements ApplicationRunner {
    private final RecentTrendContentService recentContent;
    private final LinerTrendClient client;
    private final TrendContentValidator validator;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final Path output;

    public LocalTrendPreviewRunner(RecentTrendContentService recentContent, LinerTrendClient client,
            TrendContentValidator validator, Clock clock, ObjectMapper mapper,
            @Value("${app.economic-trends.local-preview-output:.local-docs/trend-preview.json}") String outputPath) {
        this.recentContent = recentContent; this.client = client; this.validator = validator;
        this.clock = clock; this.mapper = mapper; this.output = Path.of(outputPath).toAbsolutePath();
    }

    @Override public void run(ApplicationArguments arguments) throws Exception {
        LocalDate date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        var previous = recentContent.getRecent(date, true);
        try {
            var generated = client.generate(date, previous);
            validator.validate(generated, date);
            Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    Map.of("date", date, "recentContent", previous, "generatedContent", generated)), StandardCharsets.UTF_8);
            log.info("로컬 트렌드 비교 완료. recentCount={}, output={}. DB에는 저장하지 않았습니다.", previous.size(), output);
        } catch (TrendGenerationException exception) {
            log.warn("로컬 트렌드 비교 실패. errorType={}. 기존 결과 파일은 이번 실행 결과가 아닙니다.", exception.getFailure());
        }
    }
}
