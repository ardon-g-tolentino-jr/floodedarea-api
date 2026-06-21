package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.service.ActivitySseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public live-activity stream. Flood data is public, so the EventSource needs no auth — the UI
 * subscribes and refetches the relevant collection whenever a new report / confirmation / rescue
 * event arrives.
 */
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final ActivitySseService activitySseService;

    @GetMapping(value = "/activity", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter activity() {
        return activitySseService.subscribe();
    }
}
