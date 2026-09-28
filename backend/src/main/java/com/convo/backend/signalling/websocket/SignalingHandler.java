package com.convo.backend.signalling.websocket;

import com.convo.backend.auth.entity.User;
import com.convo.backend.auth.repository.UserRepository;
import com.convo.backend.auth.service.JwtService;
import com.convo.backend.signalling.dto.SignalingMessageRequest;
import com.convo.backend.signalling.service.MeetingLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_SIZE_BYTES = 1024 * 1024;

    // Map roomId -> set of sessions in that room
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    // Map session -> roomId for tracking which room each session belongs to
    private final Map<WebSocketSession, String> sessionToRoom = new ConcurrentHashMap<>();
    // Map session -> authenticated userId
    private final Map<WebSocketSession, UUID> sessionToUserId = new ConcurrentHashMap<>();
    // Map raw session -> decorated session for thread-safe buffered outbound sends
    private final Map<WebSocketSession, ConcurrentWebSocketSessionDecorator> outboundSessions = new ConcurrentHashMap<>();

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final MeetingLifecycleService meetingLifecycleService;
    private final ObjectMapper objectMapper;

    public SignalingHandler(
            JwtService jwtService,
            UserRepository userRepository,
            MeetingLifecycleService meetingLifecycleService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.meetingLifecycleService = meetingLifecycleService;
        this.objectMapper = objectMapper;
    }

    private ConcurrentWebSocketSessionDecorator getOutboundSession(WebSocketSession session) {
        return outboundSessions.computeIfAbsent(session,
                s -> new ConcurrentWebSocketSessionDecorator(s, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_BYTES));
    }

    private boolean sendToSession(
            WebSocketSession targetSession,
            Object payload) {
        // Check if session is still open before attempting to send
        if (!targetSession.isOpen()) {
            log.debug("Session already closed, skipping send sessionId={}", targetSession.getId());
            return false;
        }

        try {
            ConcurrentWebSocketSessionDecorator outboundSession = getOutboundSession(targetSession);
            outboundSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (SessionLimitExceededException e) {
            log.warn("Session limit exceeded while sending sessionId={} reason={}", targetSession.getId(), e.getMessage());
        } catch (Exception e) {
            log.warn("Send failed for sessionId={} exception={} message={}", targetSession.getId(), e.getClass().getSimpleName(), e.getMessage());
        }

        return false;
    }

    private String extractTokenFromQuery(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return null;
        }

        String query = session.getUri().getQuery();
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractTokenFromQuery(session);
        if (token == null || token.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing token"));
            return;
        }

        try {
            String email = jwtService.extractEmail(token);
            Optional<User> userOptional = userRepository.findByEmailIgnoreCase(email);

            if (userOptional.isEmpty() || !jwtService.isTokenValid(token, userOptional.get())) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
                return;
            }

            sessionToUserId.put(session, userOptional.get().getId());
            log.info("WebSocket authenticated for user {}", userOptional.get().getId());
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Token validation failed"));
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        SignalingMessageRequest data = objectMapper.readValue(payload, SignalingMessageRequest.class);

        String type = data.type();
        String roomId = data.roomId();

        UUID currentUserId = sessionToUserId.get(session);
        if (currentUserId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthenticated websocket session"));
            return;
        }

        String senderId = session.getId();

        switch (type) {
            case "start" -> {
                synchronized (rooms) {
                    if (rooms.containsKey(roomId)) {
                        Map<String, Object> errorMsg = Map.of(
                                "type", "room-already-exists");
                        sendToSession(session, errorMsg);
                        log.info("Start failed: room {} already exists", roomId);
                    } else {
                        Set<WebSocketSession> newRoom = new CopyOnWriteArraySet<>();
                        newRoom.add(session);
                        rooms.put(roomId, newRoom);
                        // Store peerId mapping
                        sessionToRoom.put(session, roomId);

                        // Tell the creator its own peer id so it can run the
                        // deterministic-initiator rule (smaller id offers) when
                        // reconciling connections later on.
                        Map<String, Object> selfMsg = Map.of(
                                "type", "room-created",
                                "selfId", senderId);
                        sendToSession(session, selfMsg);

                        log.info("Room {} created by peer {} (total peers: 1)", roomId, senderId);
                    }
                }
            }

            case "join" -> {
                synchronized (rooms) {
                    Set<WebSocketSession> roomSessions =
                            rooms.computeIfAbsent(roomId, id -> new CopyOnWriteArraySet<>());

                    if (roomSessions.contains(session)) {
                        log.debug("Peer {} already in room {}, skipping duplicate join", senderId, roomId);
                    } else {
                        roomSessions.add(session);
                        sessionToRoom.put(session, roomId);
                    }

                    log.info("Peer {} joined room {} (total peers in room: {})", senderId, roomId, roomSessions.size());
                    
                    java.util.List<Map<String, Object>> existingPeers = new java.util.ArrayList<>();
                    for (WebSocketSession s : roomSessions) {
                        if (!s.equals(session) && s.isOpen()) {
                            Map<String, Object> newPeerMsg = Map.of(
                                    "type", "peer-joined",
                                    "peerId", senderId,
                                    "userId", currentUserId.toString());
                            sendToSession(s, newPeerMsg);

                            UUID peerUserId = sessionToUserId.get(s);
                            existingPeers.add(Map.of(
                                    "peerId", s.getId(),
                                    "userId", peerUserId != null ? peerUserId.toString() : ""));
                        }
                    }
                    
                    Map<String, Object> rosterMsg = Map.of(
                            "type", "existing-peers",
                            "selfId", senderId,
                            "peers", existingPeers);
                    sendToSession(session, rosterMsg);

                    log.info("Peer {} ready for peers in room {} (total peers in room: {})", senderId, roomId, roomSessions.size());
                }
            }

            case "offer", "answer", "ice", "video-state", "audio-state", "request-offer" -> {
                String recipientId = data.to();
                Set<WebSocketSession> sessions = rooms.get(roomId);

                if (sessions != null) {
                    // Verify sender is actually in the room
                    if (!sessions.contains(session)) {
                        log.warn("Sender {} not in room {}", senderId, roomId);
                        break;
                    }

                    if (recipientId != null) {
                        // Send to specific peer
                        for (WebSocketSession s : sessions) {
                            String sId = s.getId();
                            if (sId.equals(recipientId) && !s.equals(session)) {
                                // request-offer carries no payload; Map.of rejects
                                // null values, so fall back to an empty map.
                                Object msgPayload = data.payload() != null ? data.payload() : Map.of();
                                Map<String, Object> routedMsg = Map.of(
                                        "type", type,
                                        "from", senderId,
                                        "payload", msgPayload);
                                if (sendToSession(s, routedMsg)) {
                                    log.debug("Routed {} from {} to {}", type, senderId, recipientId);
                                }
                                break;
                            }
                        }
                    } else {
                        // Broadcast to all other peers
                        for (WebSocketSession s : sessions) {
                            if (!s.equals(session) && s.isOpen()) {
                                Object msgPayload = data.payload() != null ? data.payload() : Map.of();
                                Map<String, Object> broadcastMsg = Map.of(
                                        "type", type,
                                        "from", senderId,
                                        "payload", msgPayload);
                                sendToSession(s, broadcastMsg);
                            }
                        }
                    }
                } else {
                    log.warn("Room {} not found for {} from {}", roomId, type, senderId);
                }
            }
            
            case "chat" -> {
                Set<WebSocketSession> sessions = rooms.get(roomId);
                if (sessions != null) {
                    String to = data.to();
                    boolean isDm = to != null && !to.equals("__everyone__");

                    for (WebSocketSession s : sessions) {
                        if (s.equals(session))
                            continue;
                        if (!s.isOpen())
                            continue;

                        if (isDm && !s.getId().equals(to))
                            continue;

                        Map<String, Object> chatMsg = Map.of(
                                "type", "chat",
                                "from", senderId,
                                "fromName", data.fromName() != null ? data.fromName() : "",
                                // ↓ recipient sees "__dm__" so frontend knows it's a DM to them
                                "to", isDm ? "__dm__" : "__everyone__",
                                "text", data.text() != null ? data.text() : "",
                                "time", data.time() != null ? data.time() : System.currentTimeMillis());
                        sendToSession(s, chatMsg);
                    }
                }
            }

            case "leave" -> {
                removePeerFromRoom(session, roomId);
            }

            default -> log.warn("Unknown message type: {}", type);
        }
    }

    // Removes a session from its room and broadcasts peer-left to whoever
    // remains. Called both from the explicit "leave" message (fast path,
    // fired the instant the user clicks Leave, while the socket is still
    // open) and from afterConnectionClosed (fallback for ungraceful
    // disconnects — tab close, network loss, etc). Idempotent: roomSessions
    // .remove() returns false on the second call for the same session, so a
    // "leave" message followed by the eventual afterConnectionClosed never
    // double-broadcasts.
    private void removePeerFromRoom(WebSocketSession session, String roomId) {
        if (roomId == null) {
            return;
        }

        String peerId = session.getId();

        synchronized (rooms) {
            Set<WebSocketSession> roomSessions = rooms.get(roomId);
            if (roomSessions == null) {
                return;
            }

            boolean wasRemoved = roomSessions.remove(session);
            if (!wasRemoved) {
                return;
            }

            log.info("Peer {} removed from room {}", peerId, roomId);

            // Clean up any other dead sessions in this room
            roomSessions.removeIf(s -> !s.isOpen());

            // Notify remaining peers that this peer left
            Map<String, Object> peerLeftMsg = Map.of(
                    "type", "peer-left",
                    "peerId", peerId);

            for (WebSocketSession remainingSession : roomSessions) {
                sendToSession(remainingSession, peerLeftMsg);
            }

            // Clean up empty rooms
            if (roomSessions.isEmpty()) {
                rooms.remove(roomId);
                log.info("Room {} is now empty and has been removed", roomId);
            } else {
                log.info("Peer {} left room {} (remaining peers: {})", peerId, roomId, roomSessions.size());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Get peerId BEFORE removing from map
        String roomId = sessionToRoom.get(session);
        UUID userId = sessionToUserId.get(session);

        removePeerFromRoom(session, roomId);

        if (roomId != null && userId != null) {
            meetingLifecycleService.handleMeetingLeave(roomId, userId);
        }

        // Clean up mappings
        sessionToRoom.remove(session);
        sessionToUserId.remove(session);
        outboundSessions.remove(session);

        log.info("Session {} fully disconnected", session.getId());
    }
}
