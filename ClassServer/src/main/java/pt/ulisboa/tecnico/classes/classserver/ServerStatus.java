package pt.ulisboa.tecnico.classes.classserver;

import pt.ulisboa.tecnico.classes.VectorClock;

import java.util.List;

public class ServerStatus {

    private boolean active = true;
    private boolean changed = false;
    private boolean gossipActive = true;
    private boolean deactivateGossip = true;

    private final String host;
    private final int port;
    private final List<String> qualifiers;

    private int serverId;
    private VectorClock vectorClock;

    public ServerStatus(String host, int port, List<String> qualifiers) {

        this.host = host;
        this.port = port;
        this.qualifiers = qualifiers;
        vectorClock = new VectorClock();
    }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public boolean isGossipActive() { return gossipActive; }

    public void setGossipActive(boolean gossipActive) { this.gossipActive = gossipActive; }

    public boolean isChanged() { return changed; }

    public void setChanged(boolean changed) { this.changed = changed; }

    public boolean isDeactivateGossip() {
        return deactivateGossip;
    }

    public void setDeactivateGossip(boolean deactivateGossip) {
        this.deactivateGossip = deactivateGossip;
    }

    public List<String> getQualifiers() { return qualifiers; }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public int getServerId() { return serverId; }

    public void setServerId(int id) { this.serverId = id; }

    public VectorClock getVectorClock() { return vectorClock; }

    public void setVectorClock(VectorClock vectorClock) {
        this.vectorClock = this.vectorClock.copyVectorClock(vectorClock);
    }
}
