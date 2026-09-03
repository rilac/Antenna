package ssafy.a507.backend.domain.market.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털(1160100 금융위원회) 응답의 변덕을 한곳에서 흡수한다.
 *
 * <p>주식시세와 지수시세는 같은 봉투 모양을 쓴다 — {@code response.header.resultCode} 와
 * {@code response.body.items.item}. 그리고 같은 변덕을 부린다: 데이터가 없는 날 items 가 빈
 * 문자열이고, 한 건뿐이면 배열 대신 객체이며, 인증키가 틀리면 200 에 XML 오류 문서가 온다.
 * 클라이언트마다 이 지식을 따로 들고 있으면 한쪽만 고쳐지는 날이 온다.
 */
final class PublicDataJson {

    private static final String RESULT_OK = "00";
    private static final int ERROR_BODY_PREVIEW = 200;

    /**
     * 응답을 읽기만 하는 용도라 앱의 직렬화 설정을 물려받을 이유가 없다. 우리 API 응답 규약이
     * 바뀐다고 포털 응답 해석이 함께 흔들리면 안 된다.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PublicDataJson() {}

    /**
     * 본문을 해석해 {@code response.body} 를 돌려준다.
     *
     * @param context 오류 메시지에 붙일 요청 식별자(예: {@code basDt=2026-08-28 pageNo=1})
     * @throws PublicDataException 빈 응답 · JSON 이 아닌 응답 · resultCode 가 00 이 아닐 때
     */
    static JsonNode body(String raw, String context) {
        JsonNode root = parse(raw, context);

        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText("");
        if (!RESULT_OK.equals(resultCode)) {
            throw new PublicDataException(
                    "포털이 오류를 돌려줬다 — %s resultCode=%s resultMsg=%s"
                            .formatted(context, resultCode, header.path("resultMsg").asText("")));
        }
        return root.path("response").path("body");
    }

    /**
     * 본문을 JSON 트리로 읽는다. 봉투 모양을 가정하지 않아 포털 밖의 응답(수출입은행)에도 쓴다.
     *
     * @throws PublicDataException 빈 응답이거나 JSON 이 아닐 때 — 본문 앞부분을 메시지에 남긴다
     */
    static JsonNode parse(String raw, String context) {
        if (raw == null || raw.isBlank()) {
            throw new PublicDataException("빈 응답이 왔다 — " + context);
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            // 포털은 인증키가 틀리면 200 에 XML 오류 문서를 준다. 본문 앞부분을 같이 남기지 않으면
            // "JSON 파싱 실패" 로만 보여 원인을 못 찾는다. Decoding 키인지부터 의심할 것.
            throw new PublicDataException(
                    "JSON 이 아닌 응답이 왔다(인증키 확인) — %s · %s".formatted(context, preview(raw)), e);
        }
    }

    /** 데이터가 없는 날의 items 는 빈 문자열이거나 아예 없고, 한 건뿐이면 배열이 아닌 객체다. */
    static List<JsonNode> items(JsonNode body) {
        JsonNode item = body.path("items").path("item");
        if (item.isArray()) {
            List<JsonNode> nodes = new ArrayList<>(item.size());
            item.forEach(nodes::add);
            return nodes;
        }
        return item.isObject() ? List.of(item) : List.of();
    }

    /** 값이 없는 칸을 하이픈으로 채워 보내는 경우가 있고, 천 단위 쉼표도 섞여 온다. 못 읽으면 null. */
    static BigDecimal decimal(JsonNode item, String field) {
        String value = text(item, field);
        if (value == null) {
            return null;
        }
        String normalized = value.replace(",", "");
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 앞뒤 공백을 떼고, 비어 있으면 null. */
    static String text(JsonNode item, String field) {
        JsonNode node = item.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String preview(String raw) {
        String flat = raw.replaceAll("\s+", " ").trim();
        return flat.length() <= ERROR_BODY_PREVIEW
                ? flat
                : flat.substring(0, ERROR_BODY_PREVIEW) + "...";
    }
}
