package com.convo.backend.signalling.websocket;

import com.convo.backend.auth.entity.User;
import com.convo.backend.auth.repository.UserRepository;
import com.convo.backend.auth.service.JwtService;
import com.convo.backend.signalling.service.MeetingLifecycleService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.convo.backend.signalling.utilities.JSONUtils;

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

    public SignalingHandler(
            JwtService jwtService,
            UserRepository userRepository,
            MeetingLifecycleService meetingLifecycleService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.meetingLifecycleService = meetingLifecycleService;
    }

    private ConcurrentWebSocketSessionDecorator getOutboundSession(WebSocketSession session) {
        return outboundSessions.computeIfAbsent(session,
                s -> new ConcurrentWebSocketSessionDecorator(s, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_BYTES));
    }

    private boolean sendToSession(
            WebSocketSession targetSession,
            String messageType,
            String fromPeerId,
            String toPeerId,
            Map<String, Object> payload) {
        // Check if session is still open before attempting to send
        if (!targetSession.isOpen()) {
            System.out.println(
                    "Session already closed, skipping send " + messageType +
                            " to=" + toPeerId +
                            " sessionId=" + targetSession.getId());
            return false;
        }

        try {
            ConcurrentWebSocketSessionDecorator outboundSession = getOutboundSession(targetSession);
            outboundSession.sendMessage(new TextMessage(JSONUtils.stringify(payload)));
            return true;
        } catch (SessionLimitExceededException e) {
            System.err.println(
                    "Session limit exceeded while sending " + messageType +
                            " from=" + fromPeerId + " to=" + toPeerId +
                            " sessionId=" + targetSession.getId() +
                            " reason=" + e.getMessage());
        } catch (Exception e) {
            System.err.println(
                    "Send failed for " + messageType +
                            " from=" + fromPeerId + " to=" + toPeerId +
                            " sessionId=" + targetSession.getId() +
                            " exception=" + e.getClass().getSimpleName() +
                            " message=" + e.getMessage());
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
            System.out.println("WebSocket authenticated for user " + userOptional.get().getId());
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Token validation failed"));
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> data = JSONUtils.parse(payload);

        String type = (String) data.get("type");
        String roomId = (String) data.get("roomId");
        // Object msgPayload = data.get("payload");

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
                        sendToSession(session, "room-already-exists", "server", session.getId(), errorMsg);
                        System.out.println("Start failed: Room " + roomId + " already exists");
                    } else {
                        Set<WebSocketSession> newRoom = new CopyOnWriteArraySet<>();
                        newRoom.add(session);
                        rooms.put(roomId, newRoom);
                        // Store peerId mapping
                        sessionToRoom.put(session, roomId);

                        Map<String, Object> successMsg = Map.of(
                                "type", "start-success",
                                "peerId", senderId,
                                "userId", currentUserId.toString());
                        sendToSession(session, "start-success", "server", senderId, successMsg);

                        System.out.println("Room " + roomId + " created by peer " + senderId + " (Total peers: 1)");
                    }
                }
            }

            case "join" -> {
                synchronized (rooms) {
                    if (!rooms.containsKey(roomId)) {
                        Map<String, Object> errorMsg = Map.of(
                                "type", "room-not-found");
                        sendToSession(session, "room-not-found", "server", session.getId(), errorMsg);
                        System.out.println("Join failed: Room " + roomId + " not found for peer " + senderId);
                    } else {
                        Set<WebSocketSession> roomSessions = rooms.get(roomId);

                        // Prevent duplicate joins - check if already in room
                        if (roomSessions.contains(session)) {
                            System.out.println(
                                    "Peer " + senderId + " already in room " + roomId + ", skipping duplicate join");
                        } else {
                            roomSessions.add(session);
                            sessionToRoom.put(session, roomId);
                        }

                        Map<String, Object> successMsg = Map.of(
                                "type", "join-success",
                                "peerId", senderId,
                                "userId", currentUserId.toString());
                        sendToSession(session, "join-success", "server", senderId, successMsg);

                        System.out.println("Peer " + senderId + " joined room " + roomId +
                                " (Total peers in room: " + roomSessions.size() + ")");

                        for (WebSocketSession s : roomSessions) {
                            if (!s.equals(session) && s.isOpen()) {
                                Map<String, Object> newPeerMsg = Map.of(
                                        "type", "peer-joined",
                                        "peerId", senderId);
                                sendToSession(s, "peer-joined", senderId, s.getId(), newPeerMsg);
                            }
                        }
                        System.out.println("Peer " + senderId + " ready for peers in room " + roomId +
                                " (Total peers in room: " + roomSessions.size() + ")");
                    }
                }
            }

            case "offer", "answer", "ice" -> {
                String recipientId = (String) data.get("to");
                Set<WebSocketSession> sessions = rooms.get(roomId);

                if (sessions != null) {
                    // Verify sender is actually in the room
                    if (!sessions.contains(session)) {
                        System.out.println("Error: Sender " + senderId + " not in room " + roomId);
                        break;
                    }

                    if (recipientId != null) {
                        // Send to specific peer
                        for (WebSocketSession s : sessions) {
                            String sId = s.getId();
                            if (sId.equals(recipientId) && !s.equals(session)) {
                                Object msgPayload = data.get("payload");
                                Map<String, Object> routedMsg = Map.of(
                                        "type", type,
                                        "from", senderId,
                                        "payload", msgPayload);
                                if (sendToSession(s, type, senderId, recipientId, routedMsg)) {
                                    System.out.println("Routed " + type + " from " + senderId + " to " + recipientId);
                                }
                                break;
                            }
                        }
                    } else {
                        // Broadcast to all other peers
                        for (WebSocketSession s : sessions) {
                            if (!s.equals(session) && s.isOpen()) {
                                Object msgPayload = data.get("payload");
                                Map<String, Object> broadcastMsg = Map.of(
                                        "type", type,
                                        "from", senderId,
                                        "payload", msgPayload);
                                sendToSession(s, type, senderId, s.getId(), broadcastMsg);
                            }
                        }
                    }
                } else {
                    System.out.println("Error: Room " + roomId + " not found for " + type + " from " + senderId);
                }
            }
            case "ready-for-peers" -> {
                // Notify all existing peers about the new peer joining
                Set<WebSocketSession> roomSessions = rooms.get(roomId);
                if (roomSessions == null) {
                    System.out.println("Error: Room " + roomId + " not found for ready-for-peers from " + senderId);
                    break;
                }

                // Verify sender is in the room
                if (!roomSessions.contains(session)) {
                    System.out.println("Error: Sender " + senderId + " not in room " + roomId);
                    break;
                }

            }

            default -> System.out.println("Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Get peerId BEFORE removing from map
        String peerId = session.getId();
        String roomId = sessionToRoom.get(session);
        UUID userId = sessionToUserId.get(session);

        System.out.println("Peer " + peerId + " attempting to disconnect from room " + roomId);

        // Remove session from room and notify others
        if (roomId != null) {
            synchronized (rooms) {
                Set<WebSocketSession> roomSessions = rooms.get(roomId);
                if (roomSessions != null) {
                    // Remove this session
                    boolean wasRemoved = roomSessions.remove(session);

                    if (wasRemoved) {
                        System.out.println("Peer " + peerId + " removed from room " + roomId);

                        // Clean up any other dead sessions in this room
                        roomSessions.removeIf(s -> !s.isOpen());

                        // Notify remaining peers that this peer left
                        Map<String, Object> peerLeftMsg = Map.of(
                                "type", "peer-left",
                                "peerId", peerId);

                        for (WebSocketSession remainingSession : roomSessions) {
                            if (sendToSession(remainingSession, "peer-left", peerId, remainingSession.getId(),
                                    peerLeftMsg)) {
                                System.out.println("Notified peer about " + peerId + " leaving");
                            }
                        }

                        // Clean up empty rooms
                        if (roomSessions.isEmpty()) {
                            rooms.remove(roomId);
                            System.out.println("Room " + roomId + " is now empty and has been removed");
                        } else {
                            System.out.println("Peer " + peerId + " left room " + roomId +
                                    " (Remaining peers: " + roomSessions.size() + ")");
                        }
                    }
                }
            }
        }

        if (roomId != null && userId != null) {
            meetingLifecycleService.handleMeetingLeave(roomId, userId);
        }

        // Clean up mappings
        sessionToRoom.remove(session);
        sessionToUserId.remove(session);
        outboundSessions.remove(session);

        System.out.println("Session " + session.getId() + " fully disconnected");
    }
}
