package com.chatapp.service;

import com.chatapp.dto.PresenceResponse;
import com.chatapp.entity.PresenceStatus;
import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<Long, PresenceInfo> presenceMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> offlineTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void connect(Long userId) {
        ScheduledFuture<?> timer = offlineTimers.remove(userId);
        if (timer != null) {
            timer.cancel(false);
        }

        presenceMap.compute(userId, (id, info) -> {
            if (info == null) {
                info = new PresenceInfo();
            }
            info.activeConnections++;
            info.lastHeartbeatAt = Instant.now();
            info.status = PresenceStatus.ONLINE;
            return info;
        });

        broadcastPresence(userId);
    }

    public void disconnect(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            PresenceStatus oldStatus = info.status;
            info.activeConnections = Math.max(0, info.activeConnections - 1);
            info.afkConnections = Math.min(info.afkConnections, info.activeConnections);
            if (info.activeConnections == 0) {
                scheduleOffline(userId);
            } else if (info.afkConnections >= info.activeConnections && info.status != PresenceStatus.AFK) {
                info.status = PresenceStatus.AFK;
            }
            if (info.status != oldStatus) {
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public void heartbeat(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.lastHeartbeatAt = Instant.now();
            if (info.status == PresenceStatus.AFK && info.afkConnections < info.activeConnections) {
                info.status = PresenceStatus.ONLINE;
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public void setConnectionAFK(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.afkConnections = Math.min(info.afkConnections + 1, info.activeConnections);
            if (info.afkConnections >= info.activeConnections && info.status != PresenceStatus.AFK) {
                info.status = PresenceStatus.AFK;
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public void setConnectionActive(Long userId) {
        presenceMap.computeIfPresent(userId, (id, info) -> {
            info.afkConnections = Math.max(0, info.afkConnections - 1);
            if (info.status == PresenceStatus.AFK) {
                info.status = PresenceStatus.ONLINE;
                broadcastPresence(userId);
            }
            return info;
        });
    }

    public PresenceStatus getPresence(Long userId) {
        PresenceInfo info = presenceMap.get(userId);
        return info != null ? info.status : PresenceStatus.OFFLINE;
    }

    public List<PresenceResponse> getPresenceForUsers(List<Long> userIds) {
        return userIds.stream()
                .map(userId -> {
                    PresenceStatus status = getPresence(userId);
                    User user = userRepository.findById(userId).orElse(null);
                    String username = user != null ? user.getUsername() : "unknown";
                    return new PresenceResponse(userId, username, status.name());
                })
                .toList();
    }

    @Scheduled(fixedRate = 30000)
    public void checkHeartbeatTimeouts() {
        Instant threshold = Instant.now().minusSeconds(60);
        presenceMap.forEach((userId, info) -> {
            if (info.activeConnections > 0
                    && info.status != PresenceStatus.AFK
                    && info.lastHeartbeatAt.isBefore(threshold)) {
                info.status = PresenceStatus.AFK;
                broadcastPresence(userId);
            }
        });
    }

    private void scheduleOffline(Long userId) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            presenceMap.computeIfPresent(userId, (id, info) -> {
                if (info.activeConnections == 0) {
                    info.status = PresenceStatus.OFFLINE;
                    info.afkConnections = 0;
                    broadcastPresence(userId);
                }
                return info;
            });
            offlineTimers.remove(userId);
        }, 30, TimeUnit.SECONDS);
        offlineTimers.put(userId, timer);
    }

    private void broadcastPresence(Long userId) {
        PresenceInfo info = presenceMap.get(userId);
        if (info == null) return;
        User user = userRepository.findById(userId).orElse(null);
        String username = user != null ? user.getUsername() : "unknown";
        PresenceResponse response = new PresenceResponse(userId, username, info.status.name());
        messagingTemplate.convertAndSend("/topic/presence", response);
    }

    private static class PresenceInfo {
        PresenceStatus status = PresenceStatus.OFFLINE;
        int activeConnections = 0;
        int afkConnections = 0;
        Instant lastHeartbeatAt = Instant.now();
    }

}
