package ssafy.a507.backend.domain.research.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;
import ssafy.a507.backend.domain.research.repository.CorpProfileRepository;
import ssafy.a507.backend.domain.research.service.DartIngestService;

/**
 * 첫 배포·키 등록 직후의 빈 표를 매일 도는 공시 회차가 채우는지 본다.
 *
 * <p>개황은 매월 1일, 재무는 분기 1일에만 돈다. 그 회차가 키 없이 지나가면 다음 회차까지
 * 한 달·석 달을 빈손으로 보낸다 — 실제로 2026-09-01 회차가 그렇게 지나갔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DART 스케줄러 — 빈 표 부트스트랩")
class DartIngestSchedulerTest {

    @Mock DartIngestService ingestService;
    @Mock CorpProfileRepository corpProfileRepository;
    @Mock CorpFinancialRepository corpFinancialRepository;

    @InjectMocks DartIngestScheduler scheduler;

    @Test
    @DisplayName("프로필·재무가 비어 있으면 공시 회차가 시드·개황·재무를 먼저 채운다")
    void 둘_다_비면_시드_개황_재무_순으로_먼저_채운다() {
        given(corpProfileRepository.count()).willReturn(0L);
        given(corpFinancialRepository.count()).willReturn(0L);

        scheduler.ingestDisclosures();

        // 순서가 곧 정답이다 — 고유번호 없이는 개황을, 개황 없이는 재무를 부를 수 없다.
        InOrder order = inOrder(ingestService);
        order.verify(ingestService).seedCorpCodes();
        order.verify(ingestService).ingestProfiles();
        order.verify(ingestService).ingestAnnualFinancials(anyInt());
        order.verify(ingestService).ingestDisclosures(any(), any());
    }

    @Test
    @DisplayName("프로필은 있고 재무만 비면 재무만 채운다")
    void 재무만_비면_재무만_채운다() {
        given(corpProfileRepository.count()).willReturn(300L);
        given(corpFinancialRepository.count()).willReturn(0L);

        scheduler.ingestDisclosures();

        verify(ingestService, never()).seedCorpCodes();
        verify(ingestService, never()).ingestProfiles();
        verify(ingestService).ingestAnnualFinancials(anyInt());
        verify(ingestService).ingestDisclosures(any(), any());
    }

    @Test
    @DisplayName("둘 다 차 있으면 공시 목록만 돈다")
    void 차_있으면_공시만_돈다() {
        given(corpProfileRepository.count()).willReturn(300L);
        given(corpFinancialRepository.count()).willReturn(900L);

        scheduler.ingestDisclosures();

        verify(ingestService, never()).seedCorpCodes();
        verify(ingestService, never()).ingestProfiles();
        verify(ingestService, never()).ingestAnnualFinancials(anyInt());
        verify(ingestService).ingestDisclosures(any(), any());
    }
}
