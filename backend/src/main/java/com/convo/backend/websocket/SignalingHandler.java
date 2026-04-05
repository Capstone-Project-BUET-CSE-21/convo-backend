package com.convo.backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.convo.backend.utilities.JSONUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Map;
import java.util.Set;

@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_SIZE_BYTES = 1024 * 1024;

    // Map roomId -> set of sessions in that room
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    // Map session -> roomId for tracking which room each session belongs to
    private final Map<WebSocketSession, String> sessionToRoom = new ConcurrentHashMap<>();
    // Map raw session -> decorated session for thread-safe buffered outbound sends
    private final Map<WebSocketSession, ConcurrentWebSocketSessionDecorator> outboundSessions = new ConcurrentHashMap<>();

    private ConcurrentWebSocketSessionDecorator getOutboundSession(WebSocketSession session) {
        return outboundSessions.computeIfAbsent(
                session,
                s -> new ConcurrentWebSocketSessionDecorator(s, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_BYTES));
    }

    private boolean sendToSession(
            WebSocketSession targetSession,
            String messageType,
            String fromPeerId,
            String toPeerId,
            Map<String, Object> payload) {
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

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> data = JSONUtils.parse(payload);

        String type = (String) data.get("type");
        String roomId = (String) data.get("roomId");
        // Object msgPayload = data.get("payload");

        String senderId = session.getId();

        switch (type) {
            case "start" -> {
                if (rooms.containsKey(roomId)) {
                    Map<String, Object> errorMsg = Map.of(
                            "type", "room-already-exists");
                    sendToSession(session, "room-already-exists", "server", session.getId(), errorMsg);
                } else {
                    rooms.put(roomId, new CopyOnWriteArraySet<>());
                    rooms.get(roomId).add(session);
                    // Store peerId mapping
                    sessionToRoom.put(session, roomId);
                    Map<String, Object> successMsg = Map.of(
                            "type", "start-success",
                            "peerId", senderId);
                    sendToSession(session, "start-success", "server", senderId, successMsg);
                    System.out.println("Room " + roomId + " created by peer " + senderId);
                }
            }

            case "join" -> {
                if (!rooms.containsKey(roomId)) {
                    Map<String, Object> errorMsg = Map.of(
                            "type", "room-not-found");
                    sendToSession(session, "room-not-found", "server", session.getId(), errorMsg);
                } else {
                    Set<WebSocketSession> roomSessions = rooms.get(roomId);
                    roomSessions.add(session);
                    // Store peerId mapping
                    sessionToRoom.put(session, roomId);

                    Map<String, Object> joinMsg = Map.of(
                            "type", "join-success",
                            "peerId", senderId);
                    sendToSession(session, "join-success", "server", senderId, joinMsg);
                    System.out.println("Sent join success message to " + senderId);
                }
            }

            case "offer", "answer", "ice" -> {
                String recipientId = (String) data.get("to");
                Set<WebSocketSession> sessions = rooms.get(roomId);

                if (sessions != null) {
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
                            if (!s.equals(session)) {
                                Object msgPayload = data.get("payload");
                                Map<String, Object> broadcastMsg = Map.of(
                                        "type", type,
                                        "from", senderId,
                                        "payload", msgPayload);
                                sendToSession(s, type, senderId, s.getId(), broadcastMsg);
                            }
                        }
                    }
                }
            }
            case "ready-for-peers" -> {
                // Notify all existing peers about the new peer joining
                Set<WebSocketSession> roomSessions = rooms.get(roomId);
                if (roomSessions == null) {
                    break;
                }
                for (WebSocketSession s : roomSessions) {
                    if (!s.equals(session)) {
                        Map<String, Object> newPeerMsg = Map.of(
                                "type", "peer-joined",
                                "peerId", senderId);
                        sendToSession(s, "peer-joined", senderId, s.getId(), newPeerMsg);
                    }
                }
                System.out.println("Peer " + senderId + " joined room " + roomId +
                        " (Total peers in room: " + roomSessions.size() + ")");
            }

            default -> System.out.println("Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Get peerId BEFORE removing from map
        String peerId = session.getId();
        String roomId = sessionToRoom.get(session);

        System.out.println("Peer " + peerId + " attempting to disconnect from room " + roomId);

        // Remove session from room and notify others
        if (roomId != null) {
            Set<WebSocketSession> roomSessions = rooms.get(roomId);
            if (roomSessions != null && roomSessions.contains(session)) {
                roomSessions.remove(session);

                // Notify remaining peers that this peer left
                Map<String, Object> peerLeftMsg = Map.of(
                        "type", "peer-left",
                        "peerId", peerId);

                for (WebSocketSession remainingSession : roomSessions) {
                    if (sendToSession(remainingSession, "peer-left", peerId, remainingSession.getId(), peerLeftMsg)) {
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

        // Clean up mappings
        sessionToRoom.remove(session);
        outboundSessions.remove(session);

        System.out.println("Session " + session.getId() + " fully disconnected");
    }
}
