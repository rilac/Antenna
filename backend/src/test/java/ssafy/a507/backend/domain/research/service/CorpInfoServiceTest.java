package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.research.dto.CorpFinancialListResponse;
import ssafy.a507.backend.domain.research.dto.CorpProfileResponse;
import ssafy.a507.backend.domain.research.dto.PeerListResponse;
import ssafy.a507.backend.domain.research.dto.ValuationResponse;

/** 기업개요·재무·밸류에이션·경쟁사 조회 — 빈 값 규칙·fsDiv·비율식·활성 필터·404. */
@SpringBootTest
@Transactional
@DisplayName("기업 정보 조회")
class CorpInfoServiceTest {

    /** 라이브 E2E 테스트가 커밋해 두는 코드(005930 · 035720 · 340440)와 겹치지 않게 고른다 —
     * H2 가 테스트 전체에서 공유되고 그쪽은 정리를 하지 않아, 같은 코드를 쓰면 PK 충돌로 여기가 먼저 깨진다. */
    private static final String SAMSUNG = "005935";
    private static final String HYNIX = "000660";
    private static final String OLD = "000001";
    private static final String HALTED = "000002";
    private static final String OTHER_SECTOR = "000003";
    private static final String SECTOR = "전기전자";
    private static final LocalDate BASE = LocalDate.of(2026, 9, 2);

    @Autowired CorpInfoService corpInfoService;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        seedStock(SAMSUNG, "삼성전자", SECTOR);
        seedStock(HYNIX, "SK하이닉스", SECTOR);
        seedStock(OLD, "옛멤버", SECTOR);
        seedStock(HALTED, "거래정지", SECTOR);
        seedStock(OTHER_SECTOR, "은행", "금융");
        // 기준일 시세: 삼성·하이닉스·은행. 거래정지는 기준일 -1 만, 옛멤버는 창 밖(-30일)만.
        seedQuote(SAMSUNG, BASE.minusDays(1), 69000);
        seedQuote(SAMSUNG, BASE, 70000);
        seedQuote(HYNIX, BASE, 250000);
        seedQuote(OTHER_SECTOR, BASE, 50000);
        seedQuote(HALTED, BASE.minusDays(1), 1000);
        seedQuote(OLD, BASE.minusDays(30), 500);
        em.flush();
    }

    @Nested
    @DisplayName("profile")
    class Profile {

        @Test
        @DisplayName("기업개황을 내려주고 industry 는 KRX 섹터다")
        void 정상() {
            em.createNativeQuery(
                            "INSERT INTO corp_profiles (stock_code, corp_code, corp_name, ceo_name, industry_code,"
                                    + " address, homepage_url, established_on, updated_at)"
                                    + " VALUES (?, '00126380', '삼성전자', '전영현', '264', '경기도 수원시',"
                                    + " 'https://www.samsung.com', ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, LocalDate.of(1969, 1, 13))
                    .setParameter(3, Instant.now())
                    .executeUpdate();
            em.flush();

            CorpProfileResponse response = corpInfoService.profile(SAMSUNG);

            assertThat(response.corpName()).isEqualTo("삼성전자");
            assertThat(response.ceo()).isEqualTo("전영현");
            assertThat(response.establishedAt()).isEqualTo(LocalDate.of(1969, 1, 13));
            assertThat(response.homepage()).isEqualTo("https://www.samsung.com");
            assertThat(response.address()).isEqualTo("경기도 수원시");
            assertThat(response.industry()).as("DART 업종코드(264)가 아니라 stocks.sector").isEqualTo(SECTOR);
            assertThat(response.listedAt()).isNull();
        }

        @Test
        @DisplayName("기업개황 미수집이면 200 + 종목명·섹터만")
        void 미수집() {
            CorpProfileResponse response = corpInfoService.profile(HYNIX);

            assertThat(response.corpName()).isEqualTo("SK하이닉스");
            assertThat(response.industry()).isEqualTo(SECTOR);
            assertThat(response.ceo()).isNull();
        }
    }

    @Nested
    @DisplayName("financials")
    class Financials {

        @BeforeEach
        void seed() {
            seedFinancial(SAMSUNG, 2022, "OFS", 300, 40, 30, 90, 300);
            seedFinancial(SAMSUNG, 2023, "CFS", 258, 6, 15, 92, 363);
            seedFinancial(SAMSUNG, 2024, "CFS", 300, 32, 34, 112, 402);
            seedFinancial(SAMSUNG, 2025, "CFS", 320, 40, 36, 120, 420);
        }

        @Test
        @DisplayName("기본 3개년 · 오래된 연도가 먼저 · 행마다 fsDiv")
        void 기본() {
            CorpFinancialListResponse response = corpInfoService.financials(SAMSUNG, null);

            assertThat(response.items()).extracting(CorpFinancialListResponse.Item::year)
                    .containsExactly(2023, 2024, 2025);
            assertThat(response.items()).extracting(CorpFinancialListResponse.Item::fsDiv)
                    .containsOnly("CFS");
            assertThat(response.items().get(0)).satisfies(f -> {
                assertThat(f.quarter()).isEqualTo(4);
                assertThat(f.revenue()).isEqualTo(java.math.BigInteger.valueOf(258));
                assertThat(f.equity()).isEqualTo(java.math.BigInteger.valueOf(363));
            });
        }

        @Test
        @DisplayName("years 를 주면 그만큼 — 연도마다 fsDiv 가 다를 수 있다")
        void 연수_지정() {
            CorpFinancialListResponse response = corpInfoService.financials(SAMSUNG, 4);

            assertThat(response.items()).extracting(CorpFinancialListResponse.Item::year)
                    .containsExactly(2022, 2023, 2024, 2025);
            assertThat(response.items().get(0).fsDiv()).isEqualTo("OFS");
        }

        @Test
        @DisplayName("0 이하는 기본값, 상한을 넘으면 MAX_YEARS 로 자른다")
        void 연수_경계() {
            for (int year = 2011; year <= 2018; year++) {
                seedFinancial(SAMSUNG, year, "CFS", 100, 10, 10, 50, 200);
            }

            assertThat(corpInfoService.financials(SAMSUNG, 0).items()).hasSize(3);
            assertThat(corpInfoService.financials(SAMSUNG, 99).items())
                    .as("12개년이 있어도 상한까지만")
                    .hasSize(CorpInfoService.MAX_YEARS);
        }

        @Test
        @DisplayName("재무 미수집 종목은 200 + 빈 목록")
        void 미수집() {
            assertThat(corpInfoService.financials(HYNIX, null).items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("valuation")
    class Valuation {

        @Test
        @DisplayName("ROE·부채비율은 최신 연간 재무 · 소수 1자리 · PER·PBR 은 null + 사유")
        void 정상() {
            seedFinancial(SAMSUNG, 2024, "CFS", 300, 32, 34, 112, 402);
            seedFinancial(SAMSUNG, 2025, "CFS", 320, 40, 36, 120, 400);

            ValuationResponse response = corpInfoService.valuation(SAMSUNG);

            assertThat(response.roe()).isEqualByComparingTo("9.0");
            assertThat(response.debtRatio()).isEqualByComparingTo("30.0");
            assertThat(response.per()).isNull();
            assertThat(response.pbr()).isNull();
            assertThat(response.basedOn().priceDate()).isEqualTo(BASE);
            assertThat(response.basedOn().prevClose()).isEqualByComparingTo("70000");
            assertThat(response.basedOn().fiscal()).isEqualTo(2025);
            assertThat(response.basedOn().fsDiv()).isEqualTo("CFS");
            assertThat(response.basedOn().note()).isEqualTo(CorpInfoService.PER_PBR_NOTE);
        }

        @Test
        @DisplayName("자본총계가 0 이하면 비율은 null — 뒤집힌 숫자를 내지 않는다")
        void 자본잠식() {
            seedFinancial(SAMSUNG, 2025, "CFS", 320, 40, 36, 120, -5);

            ValuationResponse response = corpInfoService.valuation(SAMSUNG);

            assertThat(response.roe()).isNull();
            assertThat(response.debtRatio()).isNull();
            assertThat(response.basedOn().fiscal()).isEqualTo(2025);
        }

        @Test
        @DisplayName("계정이 비어 있으면 그 비율만 null — 나머지는 그대로 값이 있다")
        void 계정_결측() {
            em.createNativeQuery(
                            "INSERT INTO corp_financials (stock_code, fiscal_year, quarter, fs_div,"
                                    + " total_liabilities, total_equity, updated_at)"
                                    + " VALUES (?, 2025, 4, 'CFS', 120, 400, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, Instant.now())
                    .executeUpdate();
            em.flush();

            ValuationResponse response = corpInfoService.valuation(SAMSUNG);

            assertThat(response.roe()).as("순이익 계정이 없다").isNull();
            assertThat(response.debtRatio()).isEqualByComparingTo("30.0");
        }

        @Test
        @DisplayName("재무 미수집이면 비율 null, 종가 기준일은 그대로 · 기준일 거래정지면 종가만 null")
        void 미수집() {
            ValuationResponse response = corpInfoService.valuation(HALTED);

            assertThat(response.roe()).isNull();
            assertThat(response.basedOn().priceDate()).isEqualTo(BASE);
            assertThat(response.basedOn().prevClose()).isNull();
            assertThat(response.basedOn().fiscal()).isNull();
        }
    }

    @Nested
    @DisplayName("peers")
    class Peers {

        @Test
        @DisplayName("같은 섹터 · 자기 제외 · 창 안 시세 있는 종목만 · 거래정지는 종가 null 로 남는다")
        void 정상() {
            PeerListResponse response = corpInfoService.peers(SAMSUNG);

            assertThat(response.priceDate()).isEqualTo(BASE);
            assertThat(response.items()).extracting(PeerListResponse.Item::code)
                    .as("옛멤버(창 밖)·은행(다른 섹터)·삼성(자기) 제외, 코드순")
                    .containsExactly(HALTED, HYNIX);
            assertThat(response.items().get(0).prevClose()).isNull();
            assertThat(response.items().get(1).prevClose()).isEqualByComparingTo(new BigDecimal("250000"));
            assertThat(response.items().get(1).marketCap()).isNull();
            assertThat(response.items().get(1).per()).isNull();
        }

        @Test
        @DisplayName("섹터가 없는 종목은 빈 목록")
        void 섹터_없음() {
            seedStock("000009", "섹터없음", null);
            em.flush();

            assertThat(corpInfoService.peers("000009").items()).isEmpty();
        }
    }

    @Test
    @DisplayName("없는 종목은 네 API 모두 STOCK_NOT_FOUND")
    void 없는_종목() {
        assertThatThrownBy(() -> corpInfoService.profile("999999")).satisfies(this::stockNotFound);
        assertThatThrownBy(() -> corpInfoService.financials("999999", null)).satisfies(this::stockNotFound);
        assertThatThrownBy(() -> corpInfoService.valuation("999999")).satisfies(this::stockNotFound);
        assertThatThrownBy(() -> corpInfoService.peers("999999")).satisfies(this::stockNotFound);
    }

    private void stockNotFound(Throwable e) {
        assertThat(e).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    private void seedStock(String code, String name, String sector) {
        em.createNativeQuery("INSERT INTO stocks (code, name, sector, listed) VALUES (?, ?, ?, TRUE)")
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, sector)
                .executeUpdate();
    }

    private void seedQuote(String code, LocalDate date, long close) {
        em.createNativeQuery(
                        "INSERT INTO daily_quotes (stock_code, trade_date, close, collected_at) VALUES (?, ?, ?, ?)")
                .setParameter(1, code)
                .setParameter(2, date)
                .setParameter(3, BigDecimal.valueOf(close))
                .setParameter(4, Instant.now())
                .executeUpdate();
    }

    private void seedFinancial(
            String code, int year, String fsDiv, long revenue, long op, long net, long liabilities, long equity) {
        em.createNativeQuery(
                        "INSERT INTO corp_financials (stock_code, fiscal_year, quarter, fs_div, revenue,"
                                + " operating_profit, net_income, total_liabilities, total_equity, updated_at)"
                                + " VALUES (?, ?, 4, ?, ?, ?, ?, ?, ?, ?)")
                .setParameter(1, code)
                .setParameter(2, year)
                .setParameter(3, fsDiv)
                .setParameter(4, revenue)
                .setParameter(5, op)
                .setParameter(6, net)
                .setParameter(7, liabilities)
                .setParameter(8, equity)
                .setParameter(9, Instant.now())
                .executeUpdate();
        em.flush();
    }
}
