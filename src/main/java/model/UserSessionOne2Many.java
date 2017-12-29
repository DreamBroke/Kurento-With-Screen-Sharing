package model;

import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;

/**
 * @author 木数难数
 */
public class UserSessionOne2Many {
    private static final Logger log = LoggerFactory.getLogger(UserSessionOne2Many.class);

    private final Session session;
    private WebRtcEndpoint webRtcEndpoint;

    public UserSessionOne2Many(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public void sendMessage(JsonObject message) throws IOException {
        log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
        session.getBasicRemote().sendText(message.toString());
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }

    public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
        this.webRtcEndpoint = webRtcEndpoint;
    }

    public void addCandidate(IceCandidate candidate) {
        webRtcEndpoint.addIceCandidate(candidate);
    }
}
