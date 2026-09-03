package ssafy.a507.backend.domain.research.client;

/**
 * {@code list.json} 공시 한 건.
 *
 * <p><b>원문 URL 이 응답에 없다.</b> 접수번호로 조립해야 한다 — {@link #originUrl()}.
 *
 * @param receiptNo 접수번호 · 원천이 발급한 고유 ID 라 재수집 멱등 키로 쓴다
 * @param reportName 보고서명 · 화면 목록에 그대로 나간다
 * @param filerName 제출인 · 대량보유보고처럼 회사가 아니라 사람이 내는 공시가 있다
 * @param receiptDate 접수일자 {@code yyyyMMdd}
 */
public record DartDisclosure(
        String corpCode,
        String stockCode,
        String receiptNo,
        String reportName,
        String filerName,
        String receiptDate) {

    private static final String VIEWER = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

    /** 뷰어 주소. 본문을 저장하지 않는 대신 사용자를 원문으로 보낸다. */
    public String originUrl() {
        return VIEWER + receiptNo;
    }
}
