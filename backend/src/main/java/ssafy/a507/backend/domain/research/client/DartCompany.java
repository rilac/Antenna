package ssafy.a507.backend.domain.research.client;

/**
 * {@code company.json} 응답 중 우리가 쓰는 것만.
 *
 * <p>법인등록번호·사업자등록번호·전화·팩스는 받지 않는다. 화면에 쓰지 않는 데다, 굳이 우리
 * DB 로 옮겨 둘 이유가 없는 식별 정보다.
 */
public record DartCompany(
        String corpCode,
        String corpName,
        String corpNameEng,
        String stockCode,
        String ceoName,
        String industryCode,
        String address,
        String homepageUrl,
        String irUrl,
        String establishedDate,
        String accountMonth) {}
