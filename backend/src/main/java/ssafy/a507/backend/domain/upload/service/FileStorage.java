package ssafy.a507.backend.domain.upload.service;

/**
 * 업로드된 바이트가 실제로 놓이는 곳.
 *
 * <p>인터페이스가 하나뿐인 구현을 감싸고 있는 것은 의도적이다. 저장 위치는 인프라 결정이
 * 끝나지 않았고(로컬 볼륨 / S3 호환 오브젝트 스토리지), 바뀔 때 갈아 끼우는 파일을 하나로
 * 묶어 두려는 것이다. 그 결정이 로컬로 굳으면 이 인터페이스를 지우고 구현을 직접 주입한다.
 */
public interface FileStorage {

    /** 저장하고 조회 URL 을 돌려준다. */
    String store(String fileId, byte[] content);

    /** 조회 엔드포인트가 내려보낼 바이트. */
    byte[] read(String fileId);
}
