package com.convo.backend;

import com.convo.backend.auth.dto.AuthResponse;
import com.convo.backend.auth.dto.SignupRequest;
import com.convo.backend.auth.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Real server on a random port, real WebSocket clients — exercises the
// /ws handshake auth and the signalling protocol exactly as browsers use it
// (see convo-frontend's useMeetingRoomSession.js).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalingWebSocketTest {

    private static final long TIMEOUT_S = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private AuthService authService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<WebSocketSession> opened = new ArrayList<>();

    @AfterEach
    void closeAll() throws Exception {
        for (WebSocketSession s : opened) {
            if (s.isOpen()) {
                s.close();
            }
        }
    }

    /** Records every inbound message and how the connection closed. */
    private static final class Client extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }
    }

    private AuthResponse newUser() {
        String email = "ws-" + UUID.randomUUID() + "@example.com";
        return authService.signup(new SignupRequest("Ws", "User", email, "secret123", "secret123"));
    }

    private Client connect(String query) throws Exception {
        Client client = new Client();
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + "/ws" + query)
                .get(TIMEOUT_S, TimeUnit.SECONDS);
        opened.add(client.session);
        return client;
    }

    private Client connectAs(AuthResponse user) throws Exception {
        return connect("?token=" + user.token());
    }

    private void send(Client client, String json) throws Exception {
        client.session.sendMessage(new TextMessage(json));
    }

    /** Next message of the given type, skipping any others. */
    private JsonNode next(Client client, String type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S);
        while (System.nanoTime() < deadline) {
            String raw = client.messages.poll(100, TimeUnit.MILLISECONDS);
            if (raw == null) {
                continue;
            }
            JsonNode node = mapper.readTree(raw);
            if (type.equals(node.path("type").asString())) {
                return node;
            }
        }
        fail("no '" + type + "' message within " + TIMEOUT_S + "s");
        return null;
    }

    private static String room() {
        return "room-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- handshake auth ------------------------------------------------------

    @Test
    void noToken_ConnectionClosedWithPolicyViolation() throws Exception {
        Client c = connect("");
        CloseStatus status = c.closed.get(TIMEOUT_S, TimeUnit.SECONDS);
        assertEquals(CloseStatus.POLICY_VIOLATION.getCode(), status.getCode());
    }

    @Test
    void invalidToken_ConnectionClosedWithPolicyViolation() throws Exception {
        Client c = connect("?token=not.a.jwt");
        CloseStatus status = c.closed.get(TIMEOUT_S, TimeUnit.SECONDS);
        assertEquals(CloseStatus.POLICY_VIOLATION.getCode(), status.getCode());
    }

    // ---- start / join / relay / leave ---------------------------------------

    @Test
    void start_RepliesRoomCreatedWithOwnPeerId() throws Exception {
        Client a = connectAs(newUser());
        send(a, "{\"type\":\"start\",\"roomId\":\"" + room() + "\"}");

        JsonNode created = next(a, "room-created");
        assertFalse(created.path("selfId").asString().isEmpty());
    }

    @Test
    void start_SameRoomTwice_SecondGetsRoomAlreadyExists() throws Exception {
        String room = room();
        Client a = connectAs(newUser());
        Client b = connectAs(newUser());

        send(a, "{\"type\":\"start\",\"roomId\":\"" + room + "\"}");
        next(a, "room-created");
        send(b, "{\"type\":\"start\",\"roomId\":\"" + room + "\"}");

        next(b, "room-already-exists");
    }

    @Test
    void join_ExchangesRosterBothWays_ThenRelaysDirectedOfferAndPeerLeft() throws Exception {
        String room = room();
        AuthResponse alice = newUser();
        AuthResponse bob = newUser();
        Client a = connectAs(alice);
        Client b = connectAs(bob);

        send(a, "{\"type\":\"start\",\"roomId\":\"" + room + "\"}");
        String alicePeerId = next(a, "room-created").path("selfId").asString();

        send(b, "{\"type\":\"join\",\"roomId\":\"" + room + "\"}");

        // Joiner gets a roster of who's already there, with their user ids.
        JsonNode roster = next(b, "existing-peers");
        String bobPeerId = roster.path("selfId").asString();
        assertEquals(1, roster.path("peers").size());
        assertEquals(alicePeerId, roster.path("peers").get(0).path("peerId").asString());
        assertEquals(alice.user().id().toString(), roster.path("peers").get(0).path("userId").asString());

        // Existing member is told who joined — the authenticated user id,
        // not anything the client claimed.
        JsonNode joined = next(a, "peer-joined");
        assertEquals(bobPeerId, joined.path("peerId").asString());
        assertEquals(bob.user().id().toString(), joined.path("userId").asString());

        // Directed relay: only the addressed peer receives it, stamped with
        // the real sender id.
        send(a, "{\"type\":\"offer\",\"roomId\":\"" + room + "\",\"to\":\"" + bobPeerId + "\",\"payload\":{\"sdp\":\"x\"}}");
        JsonNode offer = next(b, "offer");
        assertEquals(alicePeerId, offer.path("from").asString());
        assertEquals("x", offer.path("payload").path("sdp").asString());

        // Leaving notifies the rest of the room.
        send(b, "{\"type\":\"leave\",\"roomId\":\"" + room + "\"}");
        JsonNode left = next(a, "peer-left");
        assertEquals(bobPeerId, left.path("peerId").asString());
    }

    @Test
    void relay_FromSomeoneNotInTheRoom_IsDropped() throws Exception {
        String room = room();
        Client a = connectAs(newUser());
        Client outsider = connectAs(newUser());

        send(a, "{\"type\":\"start\",\"roomId\":\"" + room + "\"}");
        next(a, "room-created");

        send(outsider, "{\"type\":\"offer\",\"roomId\":\"" + room + "\",\"payload\":{\"sdp\":\"evil\"}}");

        assertNull(a.messages.poll(1, TimeUnit.SECONDS), "outsider's broadcast must not reach room members");
    }

    @Test
    void chat_DirectMessage_OnlyReachesAddressedPeer() throws Exception {
        String room = room();
        Client a = connectAs(newUser());
        Client b = connectAs(newUser());
        Client c = connectAs(newUser());

        send(a, "{\"type\":\"start\",\"roomId\":\"" + room + "\"}");
        next(a, "room-created");
        send(b, "{\"type\":\"join\",\"roomId\":\"" + room + "\"}");
        String bobPeerId = next(b, "existing-peers").path("selfId").asString();
        send(c, "{\"type\":\"join\",\"roomId\":\"" + room + "\"}");
        next(c, "existing-peers");

        send(a, "{\"type\":\"chat\",\"roomId\":\"" + room + "\",\"to\":\"" + bobPeerId + "\",\"text\":\"psst\"}");

        JsonNode dm = next(b, "chat");
        assertEquals("psst", dm.path("text").asString());
        assertEquals("__dm__", dm.path("to").asString());

        String leaked;
        while ((leaked = c.messages.poll(500, TimeUnit.MILLISECONDS)) != null) {
            assertFalse(leaked.contains("psst"), "DM leaked to a third participant: " + leaked);
        }
    }
}
