package ssafy.a507.backend.domain.market.seed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * KRX 정보데이터시스템 「업종분류 현황」 CSV 로 {@code stocks.sector} 를 채운다.
 *
 * <p><b>왜 파일 시드인가.</b> 시세 API 는 업종을 주지 않고, KRX 는 프로그램 다운로드를 막는다
 * (2026-09-03 확인 — HTML 로그인 페이지가 온다). 그래서 사람이 내려받은 CSV 를
 * {@code resources/seed/krx-sectors.csv} 에 두고 기동 때마다 덮어쓴다. 업종 개편은 드물고,
 * 바뀌면 파일만 갈아 끼운다. 파일이 없으면 조용히 건너뛴다 — 시드 없는 팀원의 로컬도 뜬다.
 *
 * <p><b>형식의 변덕.</b> KRX 원본은 CP949, 엑셀로 한 번 열었다 저장하면 UTF-8 BOM 이다. 둘 다
 * 읽는다. 열 순서는 믿지 않고 머리글 이름(종목코드·업종명)으로 찾는다. stocks 에 없는 종목은
 * 만들지 않는다 — 수집 범위(KOSPI 300) 밖 종목이 파일에 700개 더 있다.
 */
@Slf4j
@Component
@Order(SectorSeeder.ORDER)
@RequiredArgsConstructor
public class SectorSeeder implements ApplicationRunner {

    /**
     * 시더 중 가장 먼저 돈다. {@code stocks.sector} 를 읽는 시더(모의투자 시즌)가 있고,
     * 순서를 정해 두지 않으면 업종이 비어 있는 채로 그 시더가 도는 기동이 생긴다.
     */
    public static final int ORDER = 10;

    static final String SEED_PATH = "seed/krx-sectors.csv";

    private static final String CODE_HEADER = "종목코드";
    private static final String SECTOR_HEADER = "업종명";
    private static final String UPDATE_SECTOR = "UPDATE stocks SET sector = ? WHERE code = ?";
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_PATH);
        if (!resource.exists()) {
            log.info("업종 시드 파일이 없어 건너뛴다 — {}", SEED_PATH);
            return;
        }
        Map<String, String> sectors = parse(resource);
        int updated = apply(sectors);
        log.info("업종 시드 적용 — 파일 {}종목 중 stocks 에 있는 {}종목 갱신", sectors.size(), updated);
    }

    /** 종목코드 → 업종명. 머리글로 열을 찾고, 코드나 업종이 빈 줄은 버린다. */
    static Map<String, String> parse(Resource resource) throws IOException {
        String text = decode(resource.getContentAsByteArray());
        Map<String, String> sectors = new LinkedHashMap<>();
        int codeAt = -1;
        int sectorAt = -1;

        for (String line : text.split("\r?\n")) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCsv(line);
            if (codeAt < 0) {
                codeAt = cells.indexOf(CODE_HEADER);
                sectorAt = cells.indexOf(SECTOR_HEADER);
                if (codeAt < 0 || sectorAt < 0) {
                    throw new IOException("업종 CSV 머리글에 종목코드·업종명 열이 없다 — " + cells);
                }
                continue;
            }
            if (cells.size() <= Math.max(codeAt, sectorAt)) {
                continue;
            }
            String code = cells.get(codeAt).trim();
            String sector = cells.get(sectorAt).trim();
            if (!code.isEmpty() && !sector.isEmpty()) {
                sectors.put(code, sector);
            }
        }
        return sectors;
    }

    /** stocks 에 있는 종목만 덮는다. 갱신된 행 수를 돌려준다 — 파일에만 있는 종목은 0 으로 센다. */
    @Transactional
    public int apply(Map<String, String> sectors) {
        List<Map.Entry<String, String>> rows = new ArrayList<>(sectors.entrySet());
        if (rows.isEmpty()) {
            return 0;
        }
        int[] counts = jdbcTemplate.batchUpdate(
                UPDATE_SECTOR,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, rows.get(i).getValue());
                        ps.setString(2, rows.get(i).getKey());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
        return Arrays.stream(counts).map(c -> Math.max(c, 0)).sum();
    }

    /** UTF-8(BOM 유무 무관)로 읽어 보고, 깨지면 KRX 원본 인코딩(CP949)이다. */
    private static String decode(byte[] bytes) {
        if (bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            return new String(bytes, UTF8_BOM.length, bytes.length - UTF8_BOM.length, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("MS949"));
        }
    }

    /** 따옴표 안의 쉼표를 지키는 최소 CSV 분리. KRX 파일은 모든 칸을 따옴표로 감싼다. */
    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
