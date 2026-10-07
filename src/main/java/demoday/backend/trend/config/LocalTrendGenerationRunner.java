package demoday.backend.trend.config;

import demoday.backend.trend.service.TrendGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.economic-trends.local-generate-on-startup", havingValue = "true")
public class LocalTrendGenerationRunner implements ApplicationRunner {
    private final TrendGenerationService generations;
    @Override public void run(ApplicationArguments arguments) {
        var result = generations.runLatestDueSlot();
        log.info("로컬 경제 트렌드 생성 결과: {}. 기존 조회 API로 콘텐츠를 확인하세요.", result);
    }
}
