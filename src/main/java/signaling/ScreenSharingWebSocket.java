package signaling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import model.UserSessionOne2Many;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 木数难数
 */
@ServerEndpoint(value = "/screen-sharing-websocket")
public class ScreenSharingWebSocket {

    private KurentoClient kurento = KurentoClient.create("ws://192.168.1.180:8888/kurento");
    private final Logger log = LoggerFactory.getLogger(ScreenSharingWebSocket.class);
    private static final Gson GSON = new GsonBuilder().create();
    private final ConcurrentHashMap<String, UserSessionOne2Many> viewers = new ConcurrentHashMap<>();

    private static MediaPipeline screenPipeline;
    private static MediaPipeline webcamPipeline;
    private static UserSessionOne2Many screenPresenterUserSession;
    private static UserSessionOne2Many webcamPresenterUserSession;

    private final String SCREEN = "screen";
    private final String WEBCAM = "webcam";

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        JsonObject jsonMessage = GSON.fromJson(message, JsonObject.class);
        log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);

        switch (jsonMessage.get("id").getAsString()) {
            case "screen-presenter":
                if (screenPresenterUserSession == null) {
                    screenPresenterUserSession = new UserSessionOne2Many(session);
                    presenter(screenPresenterUserSession, session, jsonMessage, SCREEN);
                } else {
                    alreadyActing(session);
                }
                break;
            case "webcam-presenter":
                if (webcamPresenterUserSession == null) {
                    webcamPresenterUserSession = new UserSessionOne2Many(session);
                    presenter(webcamPresenterUserSession, session, jsonMessage, WEBCAM);
                } else {
                    alreadyActing(session);
                }
                break;
            case "viewer":
                if (isEmpty(screenPresenterUserSession) || isEmpty(webcamPresenterUserSession)) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "viewerResponse");
                    response.addProperty("response", "rejected");
                    response.addProperty("message", "At least one sender no active now. Become sender or . Try again later ...");
                    session.getBasicRemote().sendText(response.toString());
                } else {
                    try {
                        viewer(session, jsonMessage, jsonMessage.get("type").getAsString());
                    } catch (Throwable t) {
                        handleErrorResponse(t, session);
                    }
                }
                break;
            case "webcamOnIceCandidate": {
                addCandidate(webcamPresenterUserSession, session, jsonMessage);
                break;
            }
            case "screenOnIceCandidate": {
                addCandidate(screenPresenterUserSession, session, jsonMessage);
                break;
            }
            case "stop":
                stop(session);
                break;
            default:
                break;
        }
    }

    private boolean isEmpty(UserSessionOne2Many userSessionOne2Many) {
        return userSessionOne2Many == null || userSessionOne2Many.getWebRtcEndpoint() == null;
    }

    private void addCandidate(UserSessionOne2Many presenterUserSession, Session session, JsonObject jsonMessage) {
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        UserSessionOne2Many user = null;
        if (presenterUserSession != null) {
            if (presenterUserSession.getSession() == session) {
                user = presenterUserSession;
            } else {
                user = viewers.get(session.getId());
            }
        }
        if (user != null) {
            IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
            user.addCandidate(cand);
        }
    }


    private synchronized void presenter(UserSessionOne2Many presenterUserSession, final Session session, JsonObject jsonMessage, String type)
            throws IOException {
        if (SCREEN.equals(type)) {
            screenPipeline = kurento.createMediaPipeline();
            presenterUserSession.setWebRtcEndpoint(new WebRtcEndpoint.Builder(screenPipeline).build());
        } else if (WEBCAM.equals(type)){
            webcamPipeline = kurento.createMediaPipeline();
            presenterUserSession.setWebRtcEndpoint(new WebRtcEndpoint.Builder(webcamPipeline).build());
        }

        WebRtcEndpoint presenterWebRtc = presenterUserSession.getWebRtcEndpoint();

        presenterWebRtc.addIceCandidateFoundListener(getListener(session, type + "IceCandidate"));

        String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
        String sdpAnswer = presenterWebRtc.processOffer(sdpOffer);

        JsonObject response = new JsonObject();
        response.addProperty("id", type + "PresenterResponse");
        response.addProperty("response", "accepted");
        response.addProperty("sdpAnswer", sdpAnswer);

        synchronized (session) {
            presenterUserSession.sendMessage(response);
        }
        presenterWebRtc.gatherCandidates();
    }

    private void alreadyActing(Session session) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "presenterResponse");
        response.addProperty("response", "rejected");
        response.addProperty("message", "Another user is currently acting as sender. Try again later ...");
        try {
            session.getBasicRemote().sendText(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void viewer(final Session session, JsonObject jsonMessage, String type)
            throws IOException {
        UserSessionOne2Many viewer;
        if (viewers.containsKey(session.getId())) {
            viewer = viewers.get(session.getId());
        } else {
            viewer = new UserSessionOne2Many(session);
            viewers.put(session.getId(), viewer);
        }
        
        WebRtcEndpoint nextWebRtc;
        if (SCREEN.equals(type)) {
            nextWebRtc = new WebRtcEndpoint.Builder(screenPipeline).build();
            nextWebRtc.addIceCandidateFoundListener(getListener(session, type + "IceCandidate"));
            viewer.setWebRtcEndpoint(nextWebRtc);
            screenPresenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
        } else if (WEBCAM.equals(type)) {
            nextWebRtc = new WebRtcEndpoint.Builder(webcamPipeline).build();
            nextWebRtc.addIceCandidateFoundListener(getListener(session, type + "IceCandidate"));
            viewer.setWebRtcEndpoint(nextWebRtc);
            webcamPresenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
        } else {
            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "rejected");
            response.addProperty("message", "Error type send server...");
            session.getBasicRemote().sendText(response.toString());
            return;
        }
        String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
        String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

        JsonObject response = new JsonObject();
        response.addProperty("id", type + "ViewerResponse");
        response.addProperty("response", "accepted");
        response.addProperty("sdpAnswer", sdpAnswer);

        synchronized (session) {
            viewer.sendMessage(response);
        }
        nextWebRtc.gatherCandidates();
    }

    private EventListener<IceCandidateFoundEvent> getListener(final Session session, String iceCandidateType) {
        return event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", iceCandidateType);
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            try {
                synchronized (session) {
                    session.getBasicRemote().sendText(response.toString());
                }
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        };
    }

    private synchronized void stop(Session session) throws IOException {
        String sessionId = session.getId();
        if (screenPresenterUserSession != null && screenPresenterUserSession.getSession().getId().equals(sessionId)) {
            for (UserSessionOne2Many viewer : viewers.values()) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "stopCommunication");
                viewer.sendMessage(response);
            }

            log.info("Releasing media pipeline");
            if (screenPipeline != null) {
                screenPipeline.release();
            }
            screenPipeline = null;
            screenPresenterUserSession = null;
            webcamPresenterUserSession = null;
        } else if (viewers.containsKey(sessionId)) {
            if (viewers.get(sessionId).getWebRtcEndpoint() != null) {
                viewers.get(sessionId).getWebRtcEndpoint().release();
            }
            viewers.remove(sessionId);
        }
    }

    @OnClose
    public void afterConnectionClosed(Session session) throws IOException {
        stop(session);
    }

    private void handleErrorResponse(Throwable throwable, Session session)
            throws IOException {
        stop(session);
        log.error(throwable.getMessage(), throwable);
        JsonObject response = new JsonObject();
        response.addProperty("id", "viewerResponse");
        response.addProperty("response", "rejected");
        response.addProperty("message", throwable.getMessage());
        session.getBasicRemote().sendText(response.toString());
    }
}
