package demoday.backend.trend.service;

import demoday.backend.trend.client.TrendGenerationException;
import demoday.backend.trend.code.TrendGenerationFailure;
import demoday.backend.trend.dto.GeneratedTrendContent;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashSet;

@Component
public class TrendContentValidator {
    public void validate(GeneratedTrendContent content, LocalDate date) {
        require(content != null && content.items() != null && content.items().size() == 3);
        var titles = new HashSet<String>();
        for (var item : content.items()) {
            require(item != null);
            text(item.title(), 200); text(item.summary(), 10_000);
            require(titles.add(item.title().strip()));
            require(item.terms() != null && !item.terms().isEmpty() && item.terms().size() <= 20);
            require(item.references() != null && !item.references().isEmpty() && item.references().size() <= 20);
            var names = new HashSet<String>();
            for (var term : item.terms()) {
                require(term != null); text(term.name(), 100); text(term.description(), 5_000);
                require(names.add(term.name().strip()));
            }
            var urls = new HashSet<String>();
            for (var reference : item.references()) {
                require(reference != null);
                text(reference.title(), 500); text(reference.url(), 2048); text(reference.publisher(), 200);
                require(reference.publishedDate() != null && !reference.publishedDate().isAfter(date));
                try {
                    URI uri = URI.create(reference.url());
                    require(("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                            && uri.getHost() != null && uri.getUserInfo() == null);
                } catch (IllegalArgumentException ex) { throw invalid(); }
                require(urls.add(reference.url()));
            }
        }
    }
    private void text(String value, int max) { require(value != null && !value.isBlank() && value.length() <= max); }
    private void require(boolean valid) { if (!valid) throw invalid(); }
    private TrendGenerationException invalid() { return new TrendGenerationException(TrendGenerationFailure.INVALID_CONTENT); }
}
