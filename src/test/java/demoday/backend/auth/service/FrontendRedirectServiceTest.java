package demoday.backend.auth.service;

import demoday.backend.auth.code.AuthErrorCode;
import demoday.backend.global.exception.ProjectException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendRedirectServiceTest {

    private final FrontendRedirectService service = new FrontendRedirectService(
            "https://www.coze.kids/",
            "https://www.coze.kids,http://localhost:3000/"
    );

    @Test
    void savesAndConsumesAllowedRedirectUrl() {
        MockHttpSession session = new MockHttpSession();

        service.save(session, "http://localhost:3000/");

        assertThat(service.consume(session)).isEqualTo("http://localhost:3000");
        assertThat(service.consume(session)).isEqualTo("https://www.coze.kids");
    }

    @Test
    void usesDefaultUrlWhenRedirectUrlIsMissing() {
        MockHttpSession session = new MockHttpSession();

        service.save(session, null);

        assertThat(service.consume(session)).isEqualTo("https://www.coze.kids");
    }

    @Test
    void rejectsUrlOutsideAllowList() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> service.save(session, "https://attacker.example"))
                .isInstanceOfSatisfying(ProjectException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.INVALID_REDIRECT_URL)
                );
    }
}
