package ssafy.a507.backend.domain.upload.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * 올라온 바이트에서 형식과 크기를 직접 읽는다 (ANT-COMMUNITY-06).
 *
 * <p><b>클라이언트가 보낸 Content-Type 을 믿지 않는다.</b> multipart 의 파트 헤더는 요청을
 * 만드는 쪽이 자유롭게 쓰는 값이라, {@code image/png} 라고 적고 실행 파일을 올릴 수 있다.
 * 화이트리스트 판정은 파일 앞머리의 매직 바이트로만 한다.
 *
 * <p>크기는 헤더만 읽는다. {@code ImageIO.read} 는 픽셀까지 전부 디코딩해 5MB JPEG 하나가
 * 수십 MB 힙을 잡는다 — 여기서 필요한 건 두 정수뿐이다.
 */
public final class ImageProbe {

    public record Image(String mime, int width, int height) {}

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** RIFF 헤더(12바이트) 다음에 청크가 온다 — WebP 크기는 그 청크 안에 있다. */
    private static final int RIFF_HEADER_BYTES = 12;

    private ImageProbe() {}

    public static Image probe(byte[] data) {
        String mime = sniff(data);
        int[] size = "image/webp".equals(mime) ? webpSize(data) : imageIoSize(data);
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE, "file");
        }
        return new Image(mime, size[0], size[1]);
    }

    private static String sniff(byte[] data) {
        if (startsWith(data, PNG, 0)) {
            return "image/png";
        }
        if (startsWith(data, JPEG, 0)) {
            return "image/jpeg";
        }
        // RIFF 컨테이너는 "RIFF"(0) + 크기(4) + 포맷(8) 이라 두 마디를 함께 봐야 WebP 로 확정된다.
        if (startsWith(data, "RIFF".getBytes(), 0) && startsWith(data, "WEBP".getBytes(), 8)) {
            return "image/webp";
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE, "file");
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** PNG · JPEG. 등록된 리더에게 헤더만 읽혀 크기를 얻는다. */
    private static int[] imageIoSize(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // 매직 바이트는 맞았지만 뒤가 깨진 파일이다. 형식 오류로 묶어 400 으로 돌려준다.
            return null;
        }
    }

    /**
     * WebP. JDK 에 리더가 없어 RIFF 청크를 직접 읽는다 — 화이트리스트에서 빼면 배너 용량이
     * 두 배가 되므로 30 줄을 감수한다.
     *
     * <p>세 가지 청크가 있고 크기를 담는 자리가 각각 다르다. VP8(손실) · VP8L(무손실) ·
     * VP8X(확장, 애니메이션·알파). 값은 모두 리틀엔디언이고 VP8L·VP8X 는 "실제값 - 1" 이다.
     */
    private static int[] webpSize(byte[] data) {
        if (data.length < RIFF_HEADER_BYTES + 8) {
            return null;
        }
        String chunk = new String(data, RIFF_HEADER_BYTES, 4);
        int payload = RIFF_HEADER_BYTES + 8;

        return switch (chunk) {
            // 프레임 태그 3바이트 + 시작 코드 3바이트(9d 01 2a) 다음이 14비트씩의 크기다.
            case "VP8 " -> data.length < payload + 10
                    ? null
                    : new int[] {
                        le16(data, payload + 6) & 0x3FFF, le16(data, payload + 8) & 0x3FFF
                    };
            // 시그니처 1바이트 다음 4바이트에 (width-1) 14비트 + (height-1) 14비트가 붙어 있다.
            case "VP8L" -> {
                if (data.length < payload + 5) {
                    yield null;
                }
                int bits = le32(data, payload + 1);
                yield new int[] {(bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1};
            }
            // 플래그 4바이트 다음 캔버스 크기가 24비트씩 들어 있다.
            case "VP8X" -> data.length < payload + 10
                    ? null
                    : new int[] {le24(data, payload + 4) + 1, le24(data, payload + 7) + 1};
            default -> null;
        };
    }

    private static int le16(byte[] d, int at) {
        return (d[at] & 0xFF) | ((d[at + 1] & 0xFF) << 8);
    }

    private static int le24(byte[] d, int at) {
        return le16(d, at) | ((d[at + 2] & 0xFF) << 16);
    }

    private static int le32(byte[] d, int at) {
        return le24(d, at) | ((d[at + 3] & 0xFF) << 24);
    }
}
