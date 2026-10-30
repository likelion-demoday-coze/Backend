package demoday.backend.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class OpenTelemetryLoggingCompatibilityTest {

    @Test
    void appenderEmitsNormalAndExceptionLogsWithRealSdk() {
        // A noop SDK (OTEL disabled) cannot detect runtime incubator API mismatches.
        var context = new LoggerContext();
        try (var provider = SdkLoggerProvider.builder().build()) {
            var sdk = OpenTelemetrySdk.builder().setLoggerProvider(provider).build();
            var appender = new OpenTelemetryAppender();
            appender.setContext(context);
            appender.setCaptureExperimentalAttributes(true);
            appender.start();
            appender.setOpenTelemetry(sdk);
            Logger logger = context.getLogger("otel-compatibility-test");
            logger.addAppender(appender);
            try {
                assertThatCode(() -> {
                    logger.info("Application starting");
                    logger.error("Startup failure", new IllegalStateException("test"));
                }).doesNotThrowAnyException();
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
        } finally {
            context.stop();
        }
    }
}
