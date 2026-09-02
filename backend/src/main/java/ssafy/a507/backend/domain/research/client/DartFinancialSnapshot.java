package ssafy.a507.backend.domain.research.client;

import java.math.BigInteger;

/**
 * 한 회사·한 회계연도의 주요 재무 계정.
 *
 * <p>DART 는 계정 하나를 한 행으로 주고 각 행이 <b>3개 회계연도</b>(당기·전기·전전기)를 함께
 * 담는다. 즉 응답 30행이 실제로는 "계정 14개 × 재무제표 2종"이고 연도는 그 안에 접혀 있다.
 * 클라이언트가 그걸 펴서 연도별로 이 스냅샷 하나씩을 만든다.
 *
 * @param fsDiv {@code CFS}(연결) 또는 {@code OFS}(별도) · 어느 쪽 숫자인지 화면에 밝혀야 한다
 * @param receiptNo 이 숫자가 실린 보고서의 접수번호 · 출처 추적용
 */
public record DartFinancialSnapshot(
        int year,
        String fsDiv,
        String currency,
        String receiptNo,
        BigInteger revenue,
        BigInteger operatingProfit,
        BigInteger netIncome,
        BigInteger totalAssets,
        BigInteger totalLiabilities,
        BigInteger totalEquity) {

    /** 계정이 하나도 안 잡힌 연도는 저장하지 않는다 — 빈 행이 "실적 0"으로 읽힌다. */
    public boolean isEmpty() {
        return revenue == null
                && operatingProfit == null
                && netIncome == null
                && totalAssets == null
                && totalLiabilities == null
                && totalEquity == null;
    }
}
