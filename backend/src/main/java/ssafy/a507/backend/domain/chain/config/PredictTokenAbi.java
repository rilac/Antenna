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
 * PredictToken(ANT) 의 ABI (ANT-CHAIN-03).
 *
 * <p>{@link CommitAnchorAbi} 와 같은 구조·같은 이유다 — 배포 스크립트({@code contracts/scripts/emit-artifacts.js})가
 * 이 리소스를 복사하고 저장소에 커밋한다. 주소와 달리 ABI 는 없으면 부팅을 막는다: 빌드에 포함된 파일이라
 * 없거나 깨졌다면 빌드가 잘못된 것이고, 릴레이어가 첫 mint 를 보낼 때 터지는 것보다 부팅에서 터지는 편이 낫다.
 *
 * <p>여기서 이름을 고정하는 함수·이벤트는 토큰 릴레이어(ANT-CHAIN-10)와 토큰 인덱서(ANT-CHAIN-11)가
 * 손으로 인코딩·디코딩할 표면이다. 컨트랙트 쪽 이름이 바뀌면 그 두 이슈의 코드보다 여기서 먼저 깨져야 한다.
 */
@Component
@EnableConfigurationProperties(PredictTokenProperties.class)
public class PredictTokenAbi {

    private static final String RESOURCE_PATH = "abi/PredictToken.json";

    /**
     * 서버가 부르거나(mint · burn · subscribe · balanceOf) 검증에 쓰는(decimals · operatorTransfer · burnSelf) 함수.
     * {@code burn} 은 오퍼레이터용 3인자 하나뿐이다 — 보유자 자가 소각은 {@code burnSelf} 로 이름을 나눠 오버로드 모호성이 없다.
     */
    private static final List<String> REQUIRED_FUNCTIONS =
            List.of("mint", "burn", "burnSelf", "subscribe", "operatorTransfer", "balanceOf", "decimals");

    /** 인덱서 ② 가 구독하는 이벤트 셋. ERC-20 표준 {@code Transfer} 는 지갑용이고 인덱서는 안 읽는다. */
    private static final List<String> REQUIRED_EVENTS = List.of("Minted", "Burned", "Subscribed");

    private final JsonNode abi;

    public PredictTokenAbi() {
        this.abi = load();
        verifyShape();
    }

    /** ABI 배열 원본. */
    public JsonNode abi() {
        return abi;
    }

    /** 매퍼를 주입받지 않는 이유는 {@link CommitAnchorAbi#load()} 와 같다 — Boot 4 컨텍스트마다 매퍼 빈 유무가 갈린다. */
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
            throw new IllegalStateException(
                    RESOURCE_PATH + " 를 읽지 못했다. contracts 에서 배포 스크립트를 돌렸는지 확인해라.", e);
        }
    }

    private void verifyShape() {
        for (String name : REQUIRED_FUNCTIONS) {
            if (!has("function", name)) {
                throw new IllegalStateException(
                        RESOURCE_PATH + " 에 function " + name + " 이 없다. ABI 가 PredictToken 의 것이 맞나?");
            }
        }
        for (String name : REQUIRED_EVENTS) {
            if (!has("event", name)) {
                throw new IllegalStateException(
                        RESOURCE_PATH + " 에 event " + name + " 이 없다. 토큰 인덱서가 구독할 이벤트다.");
            }
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
