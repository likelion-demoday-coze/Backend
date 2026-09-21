package demoday.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.Operation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Demoday API")
                        .description("Demoday backend API documentation")
                        .version("v1"))
                .path("/api/v1/auth/oauth2/authorization/kakao", new PathItem()
                        .get(new Operation()
                                .tags(java.util.List.of("Auth"))
                                .summary("카카오 로그인 시작")
                                .description("브라우저를 이 주소로 이동시키면 카카오 인증 페이지로 리다이렉트합니다.")
                                .responses(new ApiResponses()
                                        .addApiResponse("302", new ApiResponse()
                                                .description("카카오 인증 페이지로 이동")))))
                .path("/api/v1/auth/logout", new PathItem()
                        .post(new Operation()
                                .tags(java.util.List.of("Auth"))
                                .summary("로그아웃")
                                .description("세션을 종료합니다. 먼저 GET /api/v1/auth/csrf로 토큰을 받은 뒤 요청 헤더에 넣어야 합니다.")
                                .addParametersItem(new Parameter()
                                        .in("header")
                                        .name("X-CSRF-TOKEN")
                                        .required(true)
                                        .description("GET /api/v1/auth/csrf의 result.token"))
                                .responses(new ApiResponses()
                                        .addApiResponse("204", new ApiResponse()
                                                .description("로그아웃 성공, 응답 본문 없음")))));
    }
}
