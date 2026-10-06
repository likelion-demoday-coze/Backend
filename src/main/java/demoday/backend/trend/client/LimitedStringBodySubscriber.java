package demoday.backend.trend.client;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** 전체 바이트를 보관하지 않고 디코딩된 UTF-16 문자 수를 제한한다. */
final class LimitedStringBodySubscriber implements HttpResponse.BodySubscriber<String> {
    private final int limit;
    private final int status;
    private final CharsetDecoder decoder;
    private final ByteBuffer bytes = ByteBuffer.allocate(8192);
    private final CharBuffer chars = CharBuffer.allocate(4096);
    private final StringBuilder text = new StringBuilder();
    private final CompletableFuture<String> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedStringBodySubscriber(int limit, int status, Charset charset) {
        this.limit = limit;
        this.status = status;
        decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    static HttpResponse.BodyHandler<String> handler(int limit) {
        return info -> new LimitedStringBodySubscriber(limit, info.statusCode(), charset(info));
    }

    // ofString()과 같이 Content-Type의 charset을 사용하고 없거나 미지원이면 UTF-8을 쓴다.
    private static Charset charset(HttpResponse.ResponseInfo info) {
        String contentType = info.headers().firstValue("Content-Type").orElse("");
        for (String parameter : contentType.split(";")) {
            String[] pair = parameter.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equalsIgnoreCase("charset")) {
                String name = pair[1].trim();
                if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\""))
                    name = name.substring(1, name.length() - 1);
                try { return Charset.forName(name); }
                catch (IllegalArgumentException ignored) { return StandardCharsets.UTF_8; }
            }
        }
        return StandardCharsets.UTF_8;
    }

    @Override public CompletionStage<String> getBody() { return body; }

    @Override public void onSubscribe(Flow.Subscription incoming) {
        if (subscription != null) { incoming.cancel(); return; }
        subscription = incoming;
        incoming.request(1);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) return;
        try {
            for (ByteBuffer source : buffers) {
                while (source.hasRemaining()) {
                    int count = Math.min(source.remaining(), bytes.remaining());
                    var portion = source.duplicate();
                    portion.limit(source.position() + count);
                    bytes.put(portion);
                    source.position(source.position() + count);
                    bytes.flip();
                    decode(false);
                    bytes.compact(); // 분할된 멀티바이트 문자의 남은 바이트를 다음 수신까지 보존한다.
                }
            }
            subscription.request(1);
        } catch (RuntimeException exception) { fail(exception); }
    }

    @Override public void onComplete() {
        if (body.isDone()) return;
        try {
            bytes.flip();
            decode(true);
            while (true) {
                var result = decoder.flush(chars);
                append();
                if (!result.isOverflow()) break;
            }
            body.complete(text.toString());
        } catch (RuntimeException exception) { fail(exception); }
    }

    @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }

    private void decode(boolean end) {
        while (true) {
            var result = decoder.decode(bytes, chars, end);
            append();
            if (!result.isOverflow()) break;
        }
    }

    private void append() {
        chars.flip();
        if (chars.remaining() > limit - text.length()) throw new BodyTooLargeException(status);
        text.append(chars);
        chars.clear();
    }

    private void fail(RuntimeException exception) {
        subscription.cancel();
        body.completeExceptionally(exception);
    }

    /** 응답 원문 없이 상태 코드만 보존해 크기 초과 시에도 HTTP 오류 분류를 유지한다. */
    static final class BodyTooLargeException extends RuntimeException {
        private final int status;
        BodyTooLargeException(int status) { super("Response body character limit exceeded"); this.status = status; }
        int status() { return status; }
    }
}
