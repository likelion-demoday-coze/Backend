package demoday.backend.trend.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LimitedStringBodySubscriberTest {
    @ParameterizedTest @CsvSource({"UTF-16LE,UTF-16LE", "ISO-8859-1,ISO-8859-1", "unsupported-charset,UTF-8"})
    void usesDeclaredCharsetOrUtf8Fallback(String declared, String encoding) {
        var info = mock(HttpResponse.ResponseInfo.class);
        when(info.statusCode()).thenReturn(200);
        when(info.headers()).thenReturn(HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json; charset=\"" + declared + "\"")), (key, value) -> true));
        var subscriber = LimitedStringBodySubscriber.handler(1).apply(info);
        subscriber.onSubscribe(mock(Flow.Subscription.class));
        byte[] encoded = "é".getBytes(java.nio.charset.Charset.forName(encoding));
        for (byte part : encoded) subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{part})));
        subscriber.onComplete();
        assertThat(subscriber.getBody().toCompletableFuture().join()).isEqualTo("é");
    }

    @Test void acceptsExactlyOneMillionUtf16UnitsAcrossSplitUtf8Characters() {
        String value = "한😀".repeat(333333) + "한";
        assertThat(value.length()).isEqualTo(1_000_000);
        var subscriber = new LimitedStringBodySubscriber(1_000_000, 200, StandardCharsets.UTF_8);
        var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset < encoded.length; offset += 777) {
            subscriber.onNext(List.of(ByteBuffer.wrap(encoded, offset, Math.min(777, encoded.length - offset))));
        }
        subscriber.onComplete();
        assertThat(subscriber.getBody().toCompletableFuture().join()).isEqualTo(value);
        verify(subscription, never()).cancel();
    }

    @Test void cancelsBeforeCompletionAndStopsConsumingOversizedChunk() {
        var subscriber = new LimitedStringBodySubscriber(1_000_000, 200, StandardCharsets.UTF_8);
        var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        var oversized = ByteBuffer.wrap("x".repeat(2_000_000).getBytes(StandardCharsets.UTF_8));
        subscriber.onNext(List.of(oversized));
        verify(subscription).cancel();
        assertThat(oversized.hasRemaining()).isTrue();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .hasCauseInstanceOf(LimitedStringBodySubscriber.BodyTooLargeException.class);
    }

    @Test void handlesTruncatedUtf8LikeStringDecodingWithReplacement() {
        var subscriber = new LimitedStringBodySubscriber(1, 200, StandardCharsets.UTF_8);
        subscriber.onSubscribe(mock(Flow.Subscription.class));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{(byte) 0xE3, (byte) 0x81})));
        subscriber.onComplete();
        assertThat(subscriber.getBody().toCompletableFuture().join()).isEqualTo("\uFFFD");
    }
}
