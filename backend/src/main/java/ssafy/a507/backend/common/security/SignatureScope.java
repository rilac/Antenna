package ssafy.a507.backend.common.security;

/**
 * 지갑 서명이 붙는 요청의 종류.
 *
 * <p>한 값이 두 곳에 동시에 쓰인다 — payload 첫 줄의 용도 태그(<code>antenna:&lt;tag&gt;:v1</code>)와
 * nonce Redis 키의 마지막 칸(<code>sig:nonce:{userId}:{tag}</code>). 둘을 따로 두면 어긋나는 순간
 * 원인 모를 401이 나므로 여기 하나로 묶는다.
 */
public enum SignatureScope {
    WALLET_LINK("wallet-link"),
    PREDICTION_BURN("prediction-burn"),
    SUBSCRIBE("subscribe"),
    AD("ad"),
    SEASON_JOIN("season-join");

    private final String tag;

    SignatureScope(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
