package ssafy.a507.backend.domain.chain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

/**
 * ANT-CHAIN-03 — 배포 산출물(주소·ABI)이 서버 설정으로 들어오는지.
 *
 * <p>테스트 프로파일은 주소를 비워 둔다. 그 상태로 컨텍스트가 뜨는 것 자체가 검증 대상이다 —
 * 토큰을 배포하지 않은 팀원의 로컬이 부팅에 실패하면 안 된다.
 *
 * <p>ABI 표면 검사는 토큰 릴레이어(CHAIN-10)·인덱서(CHAIN-11)가 손으로 인코딩할 시그니처를 못박는다.
 * 컨트랙트가 바뀌면 그 두 이슈의 코드보다 여기서 먼저 깨져야 한다.
 */
@SpringBootTest
@DisplayName("PredictToken 설정 주입")
class PredictTokenConfigTest {

    @Autowired PredictTokenProperties properties;
    @Autowired PredictTokenAbi predictTokenAbi;

    @Test
    @DisplayName("주소가_비어_있어도_컨텍스트가_뜬다")
    void 주소가_비어_있어도_컨텍스트가_뜬다() {
        assertThat(properties).isNotNull();
        assertThat(properties.isDeployed()).isFalse();
        assertThat(properties.normalizedAddress()).isNull();
    }

    @Test
    @DisplayName("잘린_주소는_배포_안_됨으로_본다")
    void 잘린_주소는_배포_안_됨으로_본다() {
        assertThat(new PredictTokenProperties("0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D").isDeployed()).isFalse();
        assertThat(new PredictTokenProperties("0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a").isDeployed()).isTrue();
        assertThat(new PredictTokenProperties("0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a").normalizedAddress())
                .isEqualTo("0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a");
    }

    @Test
    @DisplayName("ABI에_mint_burn_burnSelf_subscribe_operatorTransfer_와_이벤트_3종이_있다")
    void ABI에_함수와_이벤트가_있다() {
        assertThat(memberNames("function"))
                .contains("mint", "burn", "burnSelf", "subscribe", "operatorTransfer", "balanceOf", "decimals", "treasury");
        assertThat(memberNames("event")).contains("Minted", "Burned", "Subscribed", "TreasuryChanged");
        // ERC-20 표면은 지갑 표시용으로 남아 있어야 한다.
        assertThat(memberNames("function")).contains("transfer", "transferFrom", "approve", "allowance", "totalSupply");
    }

    @Test
    @DisplayName("burn은_오퍼레이터용_3인자_하나뿐이다_오버로드_없음")
    void burn은_오버로드가_없다() {
        // ethers 는 같은 이름의 오버로드를 시그니처 문자열로만 부를 수 있다. 보유자 자가 소각을 burnSelf 로 나눈 이유.
        long burns = memberNames("function").stream().filter("burn"::equals).count();
        assertThat(burns).isEqualTo(1);
        JsonNode inputs = member("function", "burn").get("inputs");
        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(0).path("type").asText()).isEqualTo("address");
        assertThat(inputs.get(1).path("type").asText()).isEqualTo("uint256");
        assertThat(inputs.get(2).path("type").asText()).isEqualTo("bytes32");
    }

    @Test
    @DisplayName("subscribe는_subscriber_creator_amount_3인자다")
    void subscribe는_3인자다() {
        JsonNode inputs = member("function", "subscribe").get("inputs");
        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(0).path("name").asText()).isEqualTo("subscriber");
        assertThat(inputs.get(1).path("name").asText()).isEqualTo("creator");
        assertThat(inputs.get(2).path("name").asText()).isEqualTo("amount");
        assertThat(inputs.get(2).path("type").asText()).isEqualTo("uint256");
    }

    @Test
    @DisplayName("Minted_Burned의_reason은_bytes32_indexed_Subscribed는_5필드다")
    void 이벤트_모양() {
        // 인덱서 ② 가 reason 을 topic 으로 거르고(bytes32 indexed), Subscribed 의 두 몫을 사본으로 저장한다.
        for (String ev : List.of("Minted", "Burned")) {
            JsonNode inputs = member("event", ev).get("inputs");
            assertThat(inputs).hasSize(3);
            assertThat(inputs.get(0).path("indexed").asBoolean()).isTrue(); // to / from
            assertThat(inputs.get(1).path("type").asText()).isEqualTo("uint256"); // amount
            assertThat(inputs.get(2).path("name").asText()).isEqualTo("reason");
            assertThat(inputs.get(2).path("type").asText()).isEqualTo("bytes32");
            assertThat(inputs.get(2).path("indexed").asBoolean()).isTrue();
        }
        JsonNode sub = member("event", "Subscribed").get("inputs");
        assertThat(sub).hasSize(5);
        assertThat(sub.get(0).path("name").asText()).isEqualTo("subscriber");
        assertThat(sub.get(1).path("name").asText()).isEqualTo("creator");
        assertThat(sub.get(2).path("name").asText()).isEqualTo("amount");
        assertThat(sub.get(3).path("name").asText()).isEqualTo("creatorShare");
        assertThat(sub.get(4).path("name").asText()).isEqualTo("platformShare");
    }

    @Test
    @DisplayName("decimals는_view이고_uint8을_돌려준다")
    void decimals는_view다() {
        JsonNode fn = member("function", "decimals");
        assertThat(fn.path("stateMutability").asText()).isIn("view", "pure");
        assertThat(fn.get("outputs").get(0).path("type").asText()).isEqualTo("uint8");
    }

    @Test
    @DisplayName("커스텀_에러_5종이_ABI에_들어_있다")
    void 커스텀_에러_5종이_ABI에_들어_있다() {
        // 릴레이어가 revert 사유를 이름으로 읽어야 한다 — TransferDisabled 가 나오면 서버 버그(막힌 함수를 불렀다),
        // ERC20InsufficientBalance 는 INSUFFICIENT_BALANCE 로 사용자에게 돌아간다.
        assertThat(memberNames("error"))
                .contains("TransferDisabled", "SelfSubscribe", "ZeroAmount", "ZeroAddress", "SameAddress",
                        "ERC20InsufficientBalance", "AccessControlUnauthorizedAccount");
    }

    private List<String> memberNames(String type) {
        List<String> names = new ArrayList<>();
        for (JsonNode node : predictTokenAbi.abi()) {
            if (type.equals(node.path("type").asText())) {
                names.add(node.path("name").asText());
            }
        }
        return names;
    }

    private JsonNode member(String type, String name) {
        for (JsonNode node : predictTokenAbi.abi()) {
            if (type.equals(node.path("type").asText()) && name.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }
}
