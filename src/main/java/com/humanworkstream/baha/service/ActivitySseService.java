package com.humanworkstream.baha.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcasts live-activity events (new flood reports, confirmations, rescue requests) to every
 * connected client. The flood map is public, so the stream is public too — no per-user routing.
 * The UI's EventSource refetches the affected collection when an event arrives.
 */
@Slf4j
@Service
public class ActivitySseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.add(emitter);
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> {
            log.debug("[SSE] activity emitter error: {}", ex.getMessage());
            cleanup.run();
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** Push a named event (e.g. "report-created", "report-confirmed", "rescue-created") to all clients. */
    public void broadcast(String event, Object data) {
        sendToAll(SseEmitter.event().name(event).data(String.valueOf(data)));
    }

    /**
     * Keep-alive comment so idle proxies don't silently kill the stream, and so clients that
     * vanished (closed tab, dropped mobile network) are detected and pruned promptly.
     */
    @Scheduled(fixedDelayString = "PT25S")
    public void heartbeat() {
        sendToAll(SseEmitter.event().comment("hb"));
    }

    private void sendToAll(SseEmitter.SseEventBuilder event) {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
