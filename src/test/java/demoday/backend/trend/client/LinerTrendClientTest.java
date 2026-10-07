package demoday.backend.trend.client;

import com.sun.net.httpserver.HttpServer;
import demoday.backend.trend.code.TrendGenerationFailure;
import demoday.backend.trend.code.TrendCategory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class LinerTrendClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;
    private String url;
    private int status = 200;
    private String response;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
    }
    @AfterEach void tearDown() { server.stop(0); }

    @ParameterizedTest @EnumSource(TrendCategory.class)
    void sendsNonStreamingRequestAndParsesGroundedAnswer(TrendCategory category) {
        String answer = """
                {"items":[{"category":"%s","title":"금리","summary":"요약","terms":[],
                "references":[{"title":"발표","url":"https://example.com/news","publisher":"기관","publishedDate":"2026-10-04"}]}]}
                """.formatted(category.name());
        response = mapper.writeValueAsString(Map.of("answer", answer,
                "references", List.of(Map.of("url", "https://example.com/news"))));
        var content = client().generate(LocalDate.of(2026, 10, 5));
        assertThat(content.items().get(0).title()).isEqualTo("금리");
        assertThat(content.items().get(0).category()).isEqualTo(category);
        assertThat(content.items().get(0).references().get(0).publishedDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(receivedKey.get()).isEqualTo("test-key");
        var request = mapper.readTree(receivedBody.get());
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.path("messages").get(0).path("role").asText()).isEqualTo("user");
    }
    @ParameterizedTest @CsvSource({"401,AUTHENTICATION", "403,ACCOUNT_SUSPENDED", "402,INSUFFICIENT_CREDITS",
            "429,RATE_LIMIT", "503,PROVIDER_ERROR", "400,BAD_REQUEST"})
    void classifiesErrorsWithoutRetainingSensitiveBody(int httpStatus, TrendGenerationFailure failure) {
        status = httpStatus; response = "private-provider-details";
        assertThatThrownBy(() -> client().generate(LocalDate.now())).isInstanceOfSatisfying(TrendGenerationException.class,
                ex -> { assertThat(ex.getFailure()).isEqualTo(failure); assertThat(ex.getMessage()).doesNotContain(response); });
    }
    @Test void rejectsInvalidOrUngroundedAnswer() {
        response = "{\"answer\":\"not json\"}";
        assertInvalid();
        response = mapper.writeValueAsString(Map.of("answer", "{\"items\":[{\"category\":\"OTHER\",\"references\":[{\"url\":\"https://invented.com\"}]}]}",
                "references", List.of(Map.of("url", "https://real.com"))));
        assertInvalid();
    }
    @Test void missingKeyDoesNotSendRequest() {
        assertThatThrownBy(() -> new LinerTrendClient(mapper, "", url, 5).generate(LocalDate.now()))
                .isInstanceOfSatisfying(TrendGenerationException.class,
                        ex -> assertThat(ex.getFailure()).isEqualTo(TrendGenerationFailure.NOT_CONFIGURED));
        assertThat(receivedBody.get()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"null", "\"UNKNOWN\"", "\"PREVIEW\"", "0", "\"macro_economy\""})
    void rejectsInvalidCategoryCodes(String categoryJson) {
        response = mapper.writeValueAsString(Map.of("answer",
                "{\"items\":[{\"category\":" + categoryJson + ",\"title\":\"이슈\",\"references\":[]}]}",
                "references", List.of()));
        assertInvalid();
    }

    @Test void rejectsMissingCategory() {
        response = mapper.writeValueAsString(Map.of("answer", "{\"items\":[{\"title\":\"이슈\",\"references\":[]}]}",
                "references", List.of()));
        assertInvalid();
    }
    @Test void respectsProvidersNonRetryableServerError() {
        status = 502; response = "{\"error\":{\"retryable\":false}}";
        assertThatThrownBy(() -> client().generate(LocalDate.now())).isInstanceOfSatisfying(TrendGenerationException.class,
                ex -> assertThat(ex.getFailure().isRetryable()).isFalse());
    }
    @ParameterizedTest @CsvSource({"200,INVALID_CONTENT", "401,AUTHENTICATION", "429,RATE_LIMIT", "503,PROVIDER_ERROR"})
    void oversizedResponsesKeepStatusClassification(int httpStatus, TrendGenerationFailure failure) {
        status = httpStatus;
        response = "x".repeat(1_000_001);
        assertThatThrownBy(() -> client().generate(LocalDate.now())).isInstanceOfSatisfying(TrendGenerationException.class,
                ex -> assertThat(ex.getFailure()).isEqualTo(failure));
    }

    @Test void rejectsChunkedOversizedBodyWithoutWaitingForEndOfResponse() {
        var release = new CountDownLatch(1);
        server.createContext("/stream", exchange -> {
            exchange.getRequestBody().close();
            exchange.sendResponseHeaders(200, 0); // Content-Length 없는 청크 응답
            try (var output = exchange.getResponseBody()) {
                output.write("x".repeat(1_000_001).getBytes(StandardCharsets.UTF_8));
                output.flush();
                // 클라이언트가 결과를 반환할 때까지 본문 종료를 보류한다.
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { /* 크기 초과 취소로 서버 연결이 닫힐 수 있다. */ }
        });
        String streamUrl = url.replace("/search", "/stream");
        try {
            assertThatThrownBy(() -> new LinerTrendClient(mapper, "test-key", streamUrl, 5).generate(LocalDate.now()))
                    .isInstanceOfSatisfying(TrendGenerationException.class,
                            ex -> assertThat(ex.getFailure()).isEqualTo(TrendGenerationFailure.INVALID_CONTENT));
        } finally { release.countDown(); }
    }
    private LinerTrendClient client() { return new LinerTrendClient(mapper, "test-key", url, 5); }
    private void assertInvalid() {
        assertThatThrownBy(() -> client().generate(LocalDate.now())).isInstanceOfSatisfying(TrendGenerationException.class,
                ex -> assertThat(ex.getFailure()).isEqualTo(TrendGenerationFailure.INVALID_CONTENT));
    }
}
