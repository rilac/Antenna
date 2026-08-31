package ssafy.a507.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ssafy.a507.backend.common.security.SignatureNonceStore;

/** Redis 빈을 인메모리 구현으로 덮는다. Lettuce는 지연 연결이라 컨텍스트는 뜨지만 연산에서 터진다. */
@TestConfiguration
public class TestNonceStoreConfig {

    @Bean
    @Primary
    public SignatureNonceStore inMemorySignatureNonceStore() {
        return new InMemorySignatureNonceStore();
    }
}
