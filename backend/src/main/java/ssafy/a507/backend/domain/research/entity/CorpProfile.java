package ssafy.a507.backend.domain.research.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기업개황 (ANT-RESEARCH-01). 종목 하나에 한 행이다.
 *
 * <p><b>이 표가 DART 고유번호와 종목코드를 잇는 다리다.</b> 다른 모든 DART API 가 종목코드가
 * 아니라 {@code corp_code} 를 받으므로, 매핑 전용 표를 따로 두는 대신 어차피 1:1 인 여기에
 * 얹었다. 표 하나가 줄고 조인도 준다.
 *
 * <p>PK 를 종목코드로 잡는다 — 우리 서비스는 종목에서 출발하지 회사에서 출발하지 않는다.
 * 비상장사는 애초에 저장하지 않으므로 종목코드가 없는 행이 생기지 않는다.
 */
@Entity
@Table(
        name = "corp_profiles",
        indexes = @Index(name = "idx_corp_profiles_corp_code", columnList = "corp_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorpProfile {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * {@code stocks.code} 와 같은 값이지만 <b>FK 로 걸지 않는다.</b> 수집 범위가 서로 다르기
     * 때문이다 — {@code stocks} 는 KOSPI 시가총액 상위 300 으로 좁혀 놨고 DART 에는 상장사가
     * 4천 곳 있다. FK 를 걸면 수집 범위를 넓히거나 좁힐 때마다 프로필 적재가 통째로 깨진다.
     */
    @Id
    @Column(name = "stock_code", length = 6)
    private String stockCode;

    /** DART 고유번호 8자리. 다른 DART 호출의 입력값이라 인덱스를 둔다. */
    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;

    @Column(name = "corp_name", nullable = false, length = 100)
    private String corpName;

    @Column(name = "corp_name_eng", length = 150)
    private String corpNameEng;

    @Column(name = "ceo_name", length = 100)
    private String ceoName;

    /** DART 업종코드. {@code stocks.sector}(KRX 분류)와 다른 체계라 따로 둔다. */
    @Column(name = "industry_code", length = 10)
    private String industryCode;

    @Column(length = 200)
    private String address;

    @Column(name = "homepage_url", length = 200)
    private String homepageUrl;

    @Column(name = "ir_url", length = 200)
    private String irUrl;

    /** 설립일. DART 가 {@code yyyyMMdd} 문자열로 주는데 형식이 깨진 회사가 있어 실패는 null 로 둔다. */
    @Column(name = "established_on")
    private LocalDate establishedOn;

    /** 결산월(1~12). "12월 결산법인" 표기와 분기 판단에 쓴다. */
    @Column(name = "account_month", length = 2)
    private String accountMonth;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CorpProfile of(String stockCode, String corpCode, String corpName) {
        CorpProfile profile = new CorpProfile();
        profile.stockCode = stockCode;
        profile.corpCode = corpCode;
        profile.corpName = corpName;
        profile.updatedAt = Instant.now();
        return profile;
    }

    /**
     * 기업개황으로 채운다. 고유번호 파일만으로 만들어 둔 행을 나중에 완성하는 경로이기도 해서,
     * 이름은 들어온 값이 있을 때만 덮는다 — {@code company.json} 이 실패해도 매핑은 남는다.
     */
    public void update(
            String corpName,
            String corpNameEng,
            String ceoName,
            String industryCode,
            String address,
            String homepageUrl,
            String irUrl,
            String establishedDate,
            String accountMonth) {
        if (corpName != null && !corpName.isBlank()) {
            this.corpName = corpName;
        }
        this.corpNameEng = corpNameEng;
        this.ceoName = ceoName;
        this.industryCode = industryCode;
        this.address = address;
        this.homepageUrl = homepageUrl;
        this.irUrl = irUrl;
        this.establishedOn = parseDate(establishedDate);
        this.accountMonth = accountMonth;
        this.updatedAt = Instant.now();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
