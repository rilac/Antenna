package ssafy.a507.backend.domain.monetize.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.monetize.dto.ChannelReportListResponse;
import ssafy.a507.backend.domain.monetize.service.ReportService;

/**
 * 채널 리포트 목록 — ANT-COMMUNITY-01.
 *
 * <p>{@link ReportController} 와 따로 둔 이유는 경로가 {@code /channels} 아래여서다. 한
 * 컨트롤러에 클래스 수준 {@code @RequestMapping} 은 하나뿐이라 {@code /api/v1/reports} 를
 * 붙인 클래스에서는 이 경로를 만들 수 없다.
 */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelReportController {

    private final ReportService reportService;

    /** 채널별 리포트 목록. 최신순 고정이고 미구독자에게도 제목까지 공개다(구독 유인). */
    @GetMapping("/{userId}/reports")
    public ChannelReportListResponse list(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return reportService.channelReports(userId, cursor, size);
    }
}
