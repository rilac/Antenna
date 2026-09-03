package ssafy.a507.backend.domain.prediction.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * ANT-DB-02 — 커밋 엔티티가 실수로 응답에 실려도 salt 는 나가지 않는다.
 * 리빌 전 salt 유출은 커밋 해시 역산으로 숨은 예측 내용을 드러낸다.
 */
class PredictionCommitJsonTest {

    @Test
    @DisplayName("salt 는 기본 직렬화에서 빠진다")
    void salt_는_직렬화되지_않는다() {
        PredictionCommit commit = new PredictionCommit();
        ReflectionTestUtils.setField(commit, "commitHash", "0xc0ffee");
        ReflectionTestUtils.setField(commit, "salt", "5ecre7");

        String json = JsonMapper.builder().build().writeValueAsString(commit);

        assertThat(json).contains("0xc0ffee").doesNotContain("5ecre7").doesNotContain("salt");
    }
}
