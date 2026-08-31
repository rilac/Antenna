package ssafy.a507.backend.domain.community.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.community.dto.AbuseReportCreateRequest;
import ssafy.a507.backend.domain.community.dto.AbuseReportCreateResponse;
import ssafy.a507.backend.domain.community.service.AbuseReportService;

@RestController
@RequestMapping("/api/abuse-reports")
@RequiredArgsConstructor
public class AbuseReportController {

    private final AbuseReportService abuseReportService;

    @PostMapping
    public ResponseEntity<AbuseReportCreateResponse> report(
            @Valid @RequestBody AbuseReportCreateRequest request) {
        Long id = abuseReportService.report(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AbuseReportCreateResponse(id));
    }
}
