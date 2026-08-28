package ssafy.a507.backend.domain.common;

/**
 * 예측 트랙. 실전(REAL)과 리플레이(REPLAY)는 랭킹이 분리되며,
 * 리플레이 실적은 실전 신뢰도에 반영하지 않는다.
 */
public enum Track {
    REAL,
    REPLAY
}
