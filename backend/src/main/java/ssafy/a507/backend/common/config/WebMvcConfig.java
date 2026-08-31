package ssafy.a507.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 §1 의 {@code Base /api/v1} 을 한 곳에서 붙인다.
 *
 * <p>컨트롤러는 {@code @RequestMapping("/posts")} 처럼 명세에 적힌 경로만 쓰고,
 * 버전 접두사는 여기서 일괄로 얹는다. 컨트롤러마다 {@code /api/v1} 을 반복해 적으면
 * 한 곳을 빠뜨리는 순간 버전 밖으로 새는 엔드포인트가 생긴다.
 *
 * <p>서블릿 경로({@code spring.mvc.servlet.path})나 컨텍스트 경로가 아니라 MVC 매핑
 * 단계에서 붙이는 이유는, 그래야 MockMvc 테스트에도 같은 규칙이 적용되기 때문이다.
 * 서블릿·컨텍스트 경로는 테스트에서 그대로 재현되지 않아 운영과 테스트의 경로가 갈린다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
