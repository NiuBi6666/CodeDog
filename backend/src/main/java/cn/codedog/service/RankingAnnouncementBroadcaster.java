package cn.codedog.service;

import cn.codedog.model.RankingRewardPayload;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RankingAnnouncementBroadcaster {
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

  public SseEmitter subscribe(String owner, RankingRewardPayload.Announcement current) {
    SseEmitter emitter = new SseEmitter(0L);
    CopyOnWriteArrayList<SseEmitter> values = subscribers.computeIfAbsent(owner, ignored -> new CopyOnWriteArrayList<>());
    values.add(emitter);
    Runnable remove = () -> remove(owner, emitter);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(ignored -> remove.run());
    if (!send(emitter, current)) remove.run();
    return emitter;
  }

  public void publish(String owner, RankingRewardPayload.Announcement announcement) {
    CopyOnWriteArrayList<SseEmitter> values = subscribers.get(owner);
    if (values == null) return;
    for (SseEmitter emitter : values) {
      if (!send(emitter, announcement)) remove(owner, emitter);
    }
  }

  private boolean send(SseEmitter emitter, RankingRewardPayload.Announcement announcement) {
    try {
      emitter.send(SseEmitter.event().name("announcement").data(announcement));
      return true;
    } catch (IOException | IllegalStateException ignored) {
      return false;
    }
  }

  private void remove(String owner, SseEmitter emitter) {
    subscribers.computeIfPresent(owner, (key, values) -> {
      values.remove(emitter);
      return values.isEmpty() ? null : values;
    });
  }
}
