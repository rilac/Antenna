package ssafy.a507.backend.global.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 앱 안의 배치 스케줄을 켠다. 첫 사용처는 일봉 수집(ANT-DATA-02)이다.
 *
 * <p>테스트에서 배치가 실제로 돌지 않게 하는 방법은 이 설정을 끄는 것이 아니라, 각
 * {@code @Scheduled} 의 cron 을 {@code "-"}(Spring 의 비활성 표시)로 두는 것이다 —
 * 스케줄 등록 자체는 그대로 검증되고 실행만 빠진다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
