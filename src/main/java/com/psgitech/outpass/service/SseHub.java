package com.psgitech.outpass.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes live "something changed" events to the open browser tabs of a user (Server-Sent Events).
 * Streams are keyed by the login e-mail, so opening a stream never touches the database.
 */
@Component
public class SseHub {
    private static final int MAX_STREAMS_PER_USER = 5;
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static String key(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    public SseEmitter subscribe(String email) {
        String k = key(email);
        SseEmitter e = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> list = emitters.computeIfAbsent(k, x -> new CopyOnWriteArrayList<>());
        Runnable cleanup = () -> list.remove(e);
        e.onCompletion(cleanup);
        e.onTimeout(() -> { cleanup.run(); e.complete(); });
        e.onError(t -> cleanup.run());
        list.add(e);
        while (list.size() > MAX_STREAMS_PER_USER) {          // stale tabs: drop the oldest
            SseEmitter old = list.remove(0);
            try { old.complete(); } catch (Exception ignored) { }
        }
        try {
            e.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException ex) {
            cleanup.run();
        }
        return e;
    }

    public void push(String email, String event, String data) {
        List<SseEmitter> l = emitters.get(key(email));
        if (l == null) return;
        for (SseEmitter e : l) {
            try {
                e.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ex) {
                l.remove(e);
            }
        }
    }

    /** Heartbeat: detects closed browser tabs quickly so dead streams never pile up. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((k, list) -> {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().comment("hb"));
                } catch (Exception ex) {
                    list.remove(e);
                }
            }
            if (list.isEmpty()) emitters.remove(k, list);
        });
    }
}
