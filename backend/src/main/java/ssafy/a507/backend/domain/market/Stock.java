package ssafy.a507.backend.domain.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 종목 마스터. 6자리 종목코드를 불변 자연키로 쓴다. */
@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @Column(length = 6)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** KRX 업종분류 CSV로 시드한다. */
    @Column(length = 30)
    private String sector;

    /** 상장 여부. 폐지되면 false로 두고 행은 지우지 않는다. */
    @Column(nullable = false)
    private boolean listed;
}
