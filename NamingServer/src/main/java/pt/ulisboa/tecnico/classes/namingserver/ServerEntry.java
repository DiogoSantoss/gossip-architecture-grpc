package pt.ulisboa.tecnico.classes.namingserver;

import java.util.List;

public class ServerEntry {

    private int port;
    private String host;
    private final int id;
    private List<String> qualifiers;

    public ServerEntry(String host, int port, int id, List<String> qualifiers) {
        this.port = port;
        this.host = host;
        this.id = id;
        this.qualifiers = qualifiers;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getId() { return id; }

    public List<String> getQualifiers() {
        return qualifiers;
    }

    public void setQualifiers(List<String> qualifiers) {
        this.qualifiers = qualifiers;
    }
}
