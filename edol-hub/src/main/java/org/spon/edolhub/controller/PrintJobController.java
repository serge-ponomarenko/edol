package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.PageResponse;
import org.spon.edolhub.model.dto.PrintJobDto;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.PrintJobService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/print")
@RequiredArgsConstructor
public class PrintJobController {

    private final PrintJobService printJobService;
    private final PrintJobRepository printJobRepository;

    @GetMapping("/image/{jobId}")
    public ResponseEntity<byte[]> image(@PathVariable Long jobId) {

        PrintJob job = printJobRepository.findById(jobId).orElseThrow();

        if (job.getPlateImage() != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, job.getPlateImageType())
                    .body(job.getPlateImage());
        }

        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="160" height="120" viewBox="0 0 160 120">
                    <rect width="160" height="120" rx="10" fill="#f3f4f6"/>
                    <text x="80" y="65"
                          font-family="Arial, sans-serif"
                          font-size="14"
                          text-anchor="middle"
                          fill="#9ca3af">
                          No Preview
                    </text>
                </svg>
                """;

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/print-jobs")
    @ResponseBody
    public PageResponse<PrintJobDto> getJobs(
            @RequestParam int page,
            @RequestParam int size
    ) {
        Page<PrintJobDto> result = printJobService.getJobs(page, size)
                .map(PrintJobDto::toDto);

        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.isLast()
        );
    }

}
