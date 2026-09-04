package ssafy.a507.backend.domain.chain.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

/**
 * ANT-CHAIN-01 수용 기준 4번 — 배포 산출물(주소·ABI)이 서버 설정으로 들어오는지.
 *
 * <p>테스트 프로파일은 주소를 비워 둔다. 그 상태로 컨텍스트가 뜨는 것 자체가 검증 대상이다 —
 * 컨트랙트를 배포하지 않은 팀원의 로컬이 부팅에 실패하면 안 된다.
 */
@SpringBootTest
@DisplayName("CommitAnchor 설정 주입")
class CommitAnchorConfigTest {

    @Autowired CommitAnchorProperties properties;
    @Autowired CommitAnchorAbi commitAnchorAbi;

    @Test
    @DisplayName("주소가_비어_있어도_컨텍스트가_뜬다")
    void 주소가_비어_있어도_컨텍스트가_뜬다() {
        assertThat(properties).isNotNull();
        assertThat(properties.isDeployed()).isFalse();
        assertThat(properties.normalizedAddress()).isNull();
    }

    @Test
    @DisplayName("ABI_리소스가_클래스패스에서_읽힌다")
    void ABI_리소스가_클래스패스에서_읽힌다() {
        JsonNode abi = commitAnchorAbi.abi();
        assertThat(abi).isNotNull();
        assertThat(abi.isArray()).isTrue();
        assertThat(abi).isNotEmpty();
    }

    @Test
    @DisplayName("ABI에_anchor_rootOf_isIncluded_Anchored가_모두_있다")
    void ABI에_anchor_rootOf_isIncluded_Anchored가_모두_있다() {
        // 컨트랙트 표면이 바뀌면 여기서 먼저 깨져야 한다.
        // 릴레이어가 첫 tx 를 보내는 순간에 알게 되면 늦다.
        assertThat(memberNames("function")).contains("anchor", "rootOf", "isIncluded");
        assertThat(memberNames("event")).contains("Anchored");
    }

    @Test
    @DisplayName("anchor는_batchId_merkleRoot_commitHashes배열_3인자다")
    void anchor는_batchId_merkleRoot_commitHashes배열_3인자다() {
        // 배포된 컨트랙트가 API 명세 §4.3 v2 정정본과 같은 시그니처인지 못박는다.
        // v1 은 3번째가 uint256 commitCount 였다. v2(ANT-CHAIN-08)는 bytes32[] commitHashes 다 —
        // 리프 전량이 tx 에 실려야 체인만으로 복구가 된다. 여기서 uint256 이 나오면 v1 ABI 가 남은 것이다.
        JsonNode anchor = member("function", "anchor");
        assertThat(anchor).isNotNull();

        JsonNode inputs = anchor.get("inputs");
        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(0).path("type").asText()).isEqualTo("uint256");
        assertThat(inputs.get(0).path("name").asText()).isEqualTo("batchId");
        assertThat(inputs.get(1).path("type").asText()).isEqualTo("bytes32");
        assertThat(inputs.get(2).path("type").asText()).isEqualTo("bytes32[]");
        assertThat(inputs.get(2).path("name").asText()).isEqualTo("commitHashes");
    }

    @Test
    @DisplayName("Anchored_이벤트는_batchId가_indexed이고_commitHashes배열을_싣는다")
    void Anchored_이벤트는_batchId가_indexed이고_commitHashes배열을_싣는다() {
        // 인덱서(ANT-CHAIN-04)가 batchId 로 로그를 필터링하고, commitHashes 로 트리를 재구축한다.
        // indexed 가 빠지면 전체 로그를 받아 직접 걸러야 하고, 배열이 빠지면 복구 경로가 사라진다.
        JsonNode inputs = member("event", "Anchored").get("inputs");
        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(0).path("name").asText()).isEqualTo("batchId");
        assertThat(inputs.get(0).path("indexed").asBoolean()).isTrue();
        assertThat(inputs.get(2).path("name").asText()).isEqualTo("commitHashes");
        assertThat(inputs.get(2).path("type").asText()).isEqualTo("bytes32[]");
        assertThat(inputs.get(2).path("indexed").asBoolean()).isFalse(); // indexed 면 해시만 남아 값이 사라진다
    }

    @Test
    @DisplayName("isIncluded는_batchId_commitHash_proof배열을_받아_bool을_돌려준다")
    void isIncluded는_batchId_commitHash_proof배열을_받아_bool을_돌려준다() {
        // 검증 API(ANT-CHAIN-06)와 FE 가 eth_call 로 쓰는 표면. view 라 상태를 바꾸지 않아야 한다.
        JsonNode fn = member("function", "isIncluded");
        assertThat(fn).isNotNull();
        assertThat(fn.path("stateMutability").asText()).isEqualTo("view");
        JsonNode inputs = fn.get("inputs");
        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(1).path("type").asText()).isEqualTo("bytes32");
        assertThat(inputs.get(2).path("type").asText()).isEqualTo("bytes32[]");
        assertThat(fn.get("outputs").get(0).path("type").asText()).isEqualTo("bool");
    }

    @Test
    @DisplayName("커스텀_에러_5종이_ABI에_들어_있다")
    void 커스텀_에러_5종이_ABI에_들어_있다() {
        // 릴레이어가 revert 사유를 디코딩하려면 ABI 에 에러 정의가 있어야 한다.
        // "리버트했다"가 아니라 "어떤 에러로 리버트했다"를 알아야 재시도 여부가 갈린다 —
        // BatchAlreadyAnchored 는 재시도하면 안 되고(이미 성공), RootMismatch 는 배치 로직 버그다.
        assertThat(memberNames("error"))
                .contains(
                        "BatchAlreadyAnchored",
                        "InvalidBatchId",
                        "EmptyRoot",
                        "EmptyCommitCount",
                        "RootMismatch");
    }

    private java.util.List<String> memberNames(String type) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonNode node : commitAnchorAbi.abi()) {
            if (type.equals(node.path("type").asText())) {
                names.add(node.path("name").asText());
            }
        }
        return names;
    }

    private JsonNode member(String type, String name) {
        for (JsonNode node : commitAnchorAbi.abi()) {
            if (type.equals(node.path("type").asText()) && name.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }

    /** 주소가 설정된 환경(배포 후)의 바인딩. 프로퍼티만 다르므로 컨텍스트를 분리한다. */
    @SpringBootTest(
            properties =
                    "app.chain.commit-anchor.address=0x5FbDB2315678afecb367f032d93F642f64180aa3")
    @DisplayName("CommitAnchor 설정 주입 — 주소가 있을 때")
    static class 주소가_있을_때 {

        @Autowired CommitAnchorProperties properties;

        @Test
        @DisplayName("주소가_바인딩되고_소문자로_정규화된다")
        void 주소가_바인딩되고_소문자로_정규화된다() {
            assertThat(properties.isDeployed()).isTrue();
            // 지갑 주소 저장 형식(users.wallet_address, 소문자)과 비교 형식을 맞춘다.
            assertThat(properties.normalizedAddress())
                    .isEqualTo("0x5fbdb2315678afecb367f032d93f642f64180aa3");
        }

        @Test
        @DisplayName("잘린_주소는_isDeployed가_거짓이다")
        void 잘린_주소는_isDeployed가_거짓이다() {
            // .env 복사 실수(앞뒤 잘림)를 부팅 단계에서 걸러 내는 게 목적이다.
            CommitAnchorProperties truncated = new CommitAnchorProperties("0x5FbDB23156");
            assertThat(truncated.isDeployed()).isFalse();
            assertThat(new CommitAnchorProperties(null).isDeployed()).isFalse();
            assertThat(new CommitAnchorProperties("").isDeployed()).isFalse();
        }
    }
}
