package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.VisitStatsResponse;
import com.humanworkstream.baha.service.SiteStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public site-visit counter. {@code GET /stats/visits} reads the all-time total; {@code POST
 * /stats/visit} records one visit and returns the new total. Both are public (see SecurityConfig).
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final SiteStatsService service;

    @GetMapping("/visits")
    public VisitStatsResponse visits() {
        return new VisitStatsResponse(service.total());
    }

    @PostMapping("/visit")
    public ResponseEntity<VisitStatsResponse> record() {
        return ResponseEntity.status(HttpStatus.CREATED).body(new VisitStatsResponse(service.recordVisit()));
    }
}
