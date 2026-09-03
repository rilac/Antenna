package ssafy.a507.backend.domain.research.client;

/**
 * 고유번호 파일의 한 줄 — DART 고유번호와 종목코드를 잇는 유일한 다리다.
 *
 * <p>다른 모든 DART API 가 종목코드가 아니라 {@code corp_code} 를 받으므로, 이 매핑이 없으면
 * 나머지 수집이 시작조차 되지 않는다.
 */
public record CorpCodeRow(String corpCode, String corpName, String stockCode) {}
