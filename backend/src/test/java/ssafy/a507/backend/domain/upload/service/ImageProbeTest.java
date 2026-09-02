package ssafy.a507.backend.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.support.TestImages;

/**
 * 매직 바이트 판정과 WebP 헤더 파싱 검증.
 *
 * <p>WebP 는 JDK 에 리더가 없어 청크를 직접 읽는다 — 비트 자리를 한 칸만 잘못 짚어도 크기가
 * 엉뚱하게 나오고, 그 값으로 배너 비율을 판정하므로 여기서 못 잡으면 화면에서야 드러난다.
 */
@DisplayName("이미지 형식·크기 판독")
class ImageProbeTest {

    private static final long MAX_PIXELS = 40_000_000L;

    @Test
    @DisplayName("PNG 는 형식과 크기를 그대로 읽는다")
    void png() {
        ImageProbe.Image image = ImageProbe.probe(TestImages.png(400, 100), MAX_PIXELS);

        assertThat(image.mime()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(400);
        assertThat(image.height()).isEqualTo(100);
    }

    @Test
    @DisplayName("WebP VP8X(확장)의 캔버스 크기는 24비트 · 실제값-1 로 들어 있다")
    void webpVp8x() {
        ImageProbe.Image image = ImageProbe.probe(webpVp8x(800, 200), MAX_PIXELS);

        assertThat(image.mime()).isEqualTo("image/webp");
        assertThat(image.width()).isEqualTo(800);
        assertThat(image.height()).isEqualTo(200);
    }

    @Test
    @DisplayName("WebP VP8L(무손실)의 크기는 14비트씩 붙어 있다")
    void webpVp8l() {
        ImageProbe.Image image = ImageProbe.probe(webpVp8l(640, 160), MAX_PIXELS);

        assertThat(image.width()).isEqualTo(640);
        assertThat(image.height()).isEqualTo(160);
    }

    @Test
    @DisplayName("헤더가 선언한 크기가 상한을 넘으면 거부한다 — 파일은 30바이트여도 된다")
    void 픽셀_폭탄_거부() {
        byte[] tiny = webpVp8x(65_532, 16_383);

        assertThat(tiny.length).isLessThan(64);
        assertThatThrownBy(() -> ImageProbe.probe(tiny, MAX_PIXELS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("너무 큽니다");
    }

    @Test
    @DisplayName("이미지가 아니면 400 UNSUPPORTED_IMAGE_TYPE — 확장자·Content-Type 은 보지 않는다")
    void 이미지가_아니면_거부() {
        assertThatThrownBy(() -> ImageProbe.probe("not an image".getBytes(StandardCharsets.UTF_8), MAX_PIXELS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미지");
    }

    @Test
    @DisplayName("앞머리만 PNG 이고 뒤가 깨진 파일도 거부한다")
    void 깨진_파일도_거부() {
        byte[] broken = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};

        assertThatThrownBy(() -> ImageProbe.probe(broken, MAX_PIXELS))
                .isInstanceOf(BusinessException.class);
    }

    /** 플래그 4바이트 뒤에 (가로-1)·(세로-1) 이 24비트 리틀엔디언으로 붙는다. */
    private static byte[] webpVp8x(int width, int height) {
        ByteBuffer buffer = riff("VP8X", 10);
        buffer.putInt(0);
        put24(buffer, width - 1);
        put24(buffer, height - 1);
        return buffer.array();
    }

    /** 시그니처 0x2F 뒤 32비트에 (가로-1) 14비트 + (세로-1) 14비트가 담긴다. */
    private static byte[] webpVp8l(int width, int height) {
        ByteBuffer buffer = riff("VP8L", 5);
        buffer.put((byte) 0x2F);
        buffer.putInt((width - 1) | ((height - 1) << 14));
        return buffer.array();
    }

    private static ByteBuffer riff(String chunk, int chunkSize) {
        ByteBuffer buffer =
                ByteBuffer.allocate(12 + 8 + chunkSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(4 + 8 + chunkSize);
        buffer.put("WEBP".getBytes(StandardCharsets.US_ASCII));
        buffer.put(chunk.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(chunkSize);
        return buffer;
    }

    private static void put24(ByteBuffer buffer, int value) {
        buffer.put((byte) (value & 0xFF));
        buffer.put((byte) ((value >> 8) & 0xFF));
        buffer.put((byte) ((value >> 16) & 0xFF));
    }
}
