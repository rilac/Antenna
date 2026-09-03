package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.research.client.DartClient;

/**
 * 키가 없는 상태가 곧 검증 대상이다.
 *
 * <p>DART 자격증명이 없는 팀원의 로컬도 그대로 부팅되고, 수집만 조용히 빠져야 한다 —
 * SSAFY OAuth 자격증명과 CommitAnchor 주소가 비어도 앱이 뜨게 해 둔 것과 같은 판단이다.
 * 키 없이 호출하면 DART 는 200 에 오류 코드를 실어 보내므로, 부르지 않는 것이 유일한 정답이다.
 */
@SpringBootTest
@Transactional
@DisplayName("DART 수집 — 키 없음")
class DartIngestNoKeyTest {

    @Autowired DartIngestService ingestService;

    @MockitoBean DartClient dartClient;

    @Test
    @DisplayName("키가 비면 네 갈래 모두 아무것도 부르지 않는다")
    void 키가_없으면_수집을_열지_않는다() {
        LocalDate today = LocalDate.of(2026, 9, 2);

        assertThat(ingestService.seedCorpCodes()).isZero();
        assertThat(ingestService.ingestProfiles()).isZero();
        assertThat(ingestService.ingestAnnualFinancials(2025)).isZero();
        assertThat(ingestService.ingestDisclosures(today.minusDays(7), today)).isZero();

        verifyNoInteractions(dartClient);
    }
}
