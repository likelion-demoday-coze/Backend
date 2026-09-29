package demoday.backend.auth.service;

import demoday.backend.auth.code.AuthErrorCode;
import demoday.backend.global.exception.ProjectException;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class FrontendRedirectService {

    private static final String SESSION_ATTRIBUTE =
            FrontendRedirectService.class.getName() + ".REDIRECT_URL";

    private final String defaultUrl;
    private final List<String> allowedUrls;

    public FrontendRedirectService(
            @Value("${app.frontend-base-url}") String defaultUrl,
            @Value("${app.frontend-allowed-origins:${app.frontend-base-url}}") String allowedOrigins
    ) {
        this.defaultUrl = removeTrailingSlash(defaultUrl);
        this.allowedUrls = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .map(FrontendRedirectService::removeTrailingSlash)
                .distinct()
                .toList();

        if (!allowedUrls.contains(this.defaultUrl)) {
            throw new IllegalStateException("기본 프론트 주소는 허용된 프론트 주소 목록에 포함되어야 합니다.");
        }
    }

    public String validate(String requestedUrl) {
        String redirectUrl = requestedUrl == null || requestedUrl.isBlank()
                ? defaultUrl
                : removeTrailingSlash(requestedUrl.trim());

        if (!allowedUrls.contains(redirectUrl)) {
            throw new ProjectException(AuthErrorCode.INVALID_REDIRECT_URL);
        }
        return redirectUrl;
    }

    public void save(HttpSession session, String requestedUrl) {
        session.setAttribute(SESSION_ATTRIBUTE, validate(requestedUrl));
    }

    public String consume(HttpSession session) {
        Object savedUrl = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return savedUrl instanceof String redirectUrl && allowedUrls.contains(redirectUrl)
                ? redirectUrl
                : defaultUrl;
    }

    public List<String> getAllowedUrls() {
        return allowedUrls;
    }

    private static String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("프론트 주소 설정은 비어 있을 수 없습니다.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
