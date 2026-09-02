package ssafy.a507.backend.domain.chain.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CommitAnchor 의 ABI (ANT-CHAIN-01).
 *
 * <p>ABI 는 {@code contracts/scripts/emit-artifacts.js} 가 배포할 때마다 이 리소스로 복사하고,
 * 저장소에 커밋한다. 런타임에 어디서 받아오지 않는 이유가 둘이다 — 컨트랙트 소스와 ABI 의 버전이
 * 같이 고정돼야 하고, 받아오는 구조는 부팅에 실패면을 하나 더 만든다.
 *
 * <p><b>주소와 달리 ABI 는 없으면 부팅을 막는다.</b> 주소는 "아직 배포 안 함" 이라는 정상 상태가
 * 있지만, ABI 는 빌드에 포함된 파일이라 없거나 깨졌다면 그건 빌드가 잘못된 것이다. 릴레이어가
 * 첫 tx 를 보낼 때 터지는 것보다 부팅에서 터지는 편이 낫다.
 *
 * <p>web3j 코드젠(래퍼 클래스 생성)을 쓰지 않고 ABI JSON 을 그대로 두는 건 의도한 선택이다.
 * 함수가 {@code anchor} · {@code rootOf} 둘뿐이라 타입 안전으로 얻는 게 적은 반면, 코드젠은
 * {@code build.gradle}(팀 전체가 물려 쓰는 파일)을 고치고 빌드를 컨트랙트 컴파일에 묶는다.
 */
@Component
@EnableConfigurationProperties(CommitAnchorProperties.class)
public class CommitAnchorAbi {

    private static final String RESOURCE_PATH = "abi/CommitAnchor.json";

    /** 이 이름들이 없으면 배포된 컨트랙트와 서버가 보는 인터페이스가 어긋난 것이다. */
    private static final List<String> REQUIRED_FUNCTIONS = List.of("anchor", "rootOf");

    private static final String REQUIRED_EVENT = "Anchored";

    private final JsonNode abi;

    public CommitAnchorAbi() {
        this.abi = load();
        verifyShape();
    }

    /** ABI 배열 원본. 릴레이어(CHAIN-05)·인덱서(CHAIN-04) 가 web3j 에 넘겨 쓴다. */
    public JsonNode abi() {
        return abi;
    }

    /**
     * 매퍼를 주입받지 않고 여기서 만든다.
     *
     * <p>Boot 4 는 Jackson 3(tools.jackson)을 쓰고, 웹 계층이 없는 테스트 컨텍스트에는
     * 매퍼 빈이 아예 없을 수 있다. 이 클래스가 하는 일은 부팅 때 한 번 파일을 읽는 것뿐이라
     * 애플리케이션의 직렬화 설정(날짜 포맷·네이밍 전략)을 물려받을 이유가 없다.
     * 빈에 기대면 컨텍스트 구성마다 뜨고 안 뜨고가 갈린다.
     */
    private static JsonNode load() {
        JsonMapper mapper = JsonMapper.builder().build();
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            JsonNode root = mapper.readTree(in);
            JsonNode node = root.get("abi");
            if (node == null || !node.isArray()) {
                throw new IllegalStateException(
                        RESOURCE_PATH + " 에 abi 배열이 없다. 배포 스크립트가 만든 파일이 맞는지 확인해라.");
            }
            return node;
        } catch (IOException e) {
            // 커밋된 리소스라 정상적인 빌드에서는 날 수 없는 예외다.
            throw new IllegalStateException(
                    RESOURCE_PATH + " 를 읽지 못했다. contracts 에서 배포 스크립트를 돌렸는지 확인해라.", e);
        }
    }

    /**
     * 컨트랙트 표면이 우리가 기대하는 모양인지 본다.
     *
     * <p>ABI 파일이 있기만 하면 통과시키면, 다른 컨트랙트의 ABI 가 실수로 덮어써졌을 때
     * 릴레이어가 첫 tx 를 보내는 순간까지 아무도 모른다.
     */
    private void verifyShape() {
        for (String name : REQUIRED_FUNCTIONS) {
            if (!has("function", name)) {
                throw new IllegalStateException(
                        RESOURCE_PATH + " 에 function " + name + " 이 없다. ABI 가 CommitAnchor 의 것이 맞나?");
            }
        }
        if (!has("event", REQUIRED_EVENT)) {
            throw new IllegalStateException(
                    RESOURCE_PATH + " 에 event " + REQUIRED_EVENT + " 이 없다. 인덱서가 구독할 이벤트다.");
        }
    }

    private boolean has(String type, String name) {
        for (JsonNode member : abi) {
            if (type.equals(member.path("type").asText()) && name.equals(member.path("name").asText())) {
                return true;
            }
        }
        return false;
    }
}
