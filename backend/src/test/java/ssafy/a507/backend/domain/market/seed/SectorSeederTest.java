package ssafy.a507.backend.domain.market.seed;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.StockRepository;

/**
 * KRX 정보데이터시스템 「업종분류 현황」 CSV 로 stocks.sector 를 채우는 시드.
 *
 * <p>파일은 사람이 내려받아 리소스에 둔다 — KRX 는 프로그램 다운로드를 막는다(2026-09-03 확인,
 * HTML 로그인 페이지가 온다). 그래서 형식의 변덕을 여기서 고정한다: KRX 는 CP949 로 주고, 엑셀로
 * 한 번 열었다 저장하면 UTF-8 BOM 이 된다. 열 순서는 믿지 않고 머리글 이름으로 찾는다.
 */
@SpringBootTest
@Transactional
class SectorSeederTest {

    @Autowired SectorSeeder seeder;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("UTF-8 BOM CSV — 머리글 이름으로 종목코드·업종명 열을 찾는다")
    void utf8_bom_csv_를_읽는다() throws IOException {
        Map<String, String> sectors = SectorSeeder.parse(new ClassPathResource("seed/sectors-utf8-bom.csv"));

        assertThat(sectors)
                .containsEntry("005930", "전기전자")
                .containsEntry("000660", "전기전자")
                .containsEntry("035720", "서비스업")
                .hasSize(4);
    }

    @Test
    @DisplayName("KRX 원본 인코딩(CP949)도 그대로 읽는다")
    void cp949_csv_를_읽는다() throws IOException {
        Map<String, String> sectors = SectorSeeder.parse(new ClassPathResource("seed/sectors-cp949.csv"));

        assertThat(sectors).containsEntry("005930", "전기전자").containsEntry("035720", "서비스업");
    }

    @Test
    @DisplayName("있는 종목만 업종을 덮고, 파일에만 있는 종목은 만들지 않는다 — 몇 번 돌려도 같다")
    void 있는_종목만_갱신한다() throws IOException {
        insertStock("005930", "삼성전자", null);
        insertStock("035720", "카카오", "옛업종");
        insertStock("000100", "유한양행", "의약품");
        em.flush();
        em.clear();

        int updated = seeder.apply(SectorSeeder.parse(new ClassPathResource("seed/sectors-utf8-bom.csv")));
        int again = seeder.apply(SectorSeeder.parse(new ClassPathResource("seed/sectors-utf8-bom.csv")));
        em.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(again).as("두 번째도 같은 행을 같은 값으로 덮는다").isEqualTo(2);
        assertThat(sector("005930")).isEqualTo("전기전자");
        assertThat(sector("035720")).as("파일이 원본이다 — 이미 값이 있어도 덮는다").isEqualTo("서비스업");
        assertThat(sector("000100")).as("파일에 없는 종목은 건드리지 않는다").isEqualTo("의약품");
        assertThat(stockRepository.existsById("999999")).as("파일에만 있는 종목은 만들지 않는다").isFalse();
    }

    private String sector(String code) {
        return stockRepository.findById(code).map(Stock::getSector).orElseThrow();
    }

    private void insertStock(String code, String name, String sector) {
        em.createNativeQuery(
                        """
                        INSERT INTO stocks (code, name, sector, market, listed) VALUES (?, ?, ?, 'KOSPI', true)
                        """)
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, sector)
                .executeUpdate();
    }
}
