package ssafy.a507.backend.domain.season.repository;

/** 시즌별 종목 수. 목록 응답의 {@code tickerCount} 를 한 번에 채우려고 둔 집계 결과다. */
public record SeasonTickerCount(Long seasonId, long count) {}
