package ssafy.a507.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 모든 REST 엔드포인트가 {@code /api/v1} 로 시작하고 그 접두사가 두 번 붙지 않는지 본다.
 *
 * <p>이 테스트가 있는 이유 — 2026-08-31 에 실제로 깨졌다. 컨트롤러 전체에 접두사를 자동으로
 * 얹는 설정을 넣었는데, 다른 담당자가 만든 AuthController 는 이미 경로에 {@code /api/v1} 을
 * 적어둬서 실제 경로가 {@code /api/v1/api/v1/auth/...} 가 됐다. 로그인이 통째로 404 였고
 * 컨트롤러별 테스트는 자기 경로만 보니 아무도 잡지 못했다.
 */
@SpringBootTest
class ApiPathContractTest {

    private static final String PREFIX = "/api/v1";

    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("엔드포인트 경로에 /api/v1 이 두 번 들어가지 않는다")
    void 접두사가_중복되지_않는다() {
        List<String> doubled = patterns().stream()
                .filter(path -> path.startsWith(PREFIX + PREFIX))
                .toList();

        assertThat(doubled)
                .as("접두사가 두 번 붙은 경로 — 컨트롤러가 직접 적은 /api/v1 과 자동 접두사가 겹쳤다")
                .isEmpty();
    }

    @Test
    @DisplayName("모든 REST 엔드포인트는 /api/v1 로 시작한다 — 명세 §1 Base")
    void 모든_경로가_버전_아래에_있다() {
        List<String> outside = patterns().stream()
                .filter(path -> !path.startsWith(PREFIX))
                // /error 는 서블릿 컨테이너가 포워딩하는 경로라 버전 밖이다.
                .filter(path -> !path.startsWith("/error"))
                .toList();

        assertThat(outside).as("버전 접두사 밖으로 새어나간 경로").isEmpty();
    }

    private List<String> patterns() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::getPathPatternsCondition)
                .filter(condition -> condition != null)
                .flatMap(condition -> condition.getPatternValues().stream())
                .toList();
    }
}
