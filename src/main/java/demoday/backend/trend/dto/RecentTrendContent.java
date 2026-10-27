package demoday.backend.trend.dto;

import java.time.LocalDate;
import java.util.List;

/** 이전 이슈를 반복하지 않도록 생성기에 전달하는 제한된 참고 데이터. */
public record RecentTrendContent(LocalDate date, String title, String summary, List<String> urls) {}
