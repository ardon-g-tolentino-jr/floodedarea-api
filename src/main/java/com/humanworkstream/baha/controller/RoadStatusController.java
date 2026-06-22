package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.RoadSegmentResponse;
import com.humanworkstream.baha.service.RoadStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Flooded-road overlay derived from active reports. Public read (SecurityConfig). */
@RestController
@RequestMapping("/road-status")
@RequiredArgsConstructor
public class RoadStatusController {

    private final RoadStatusService service;

    @GetMapping
    public List<RoadSegmentResponse> list() {
        return service.currentSegments();
    }
}
