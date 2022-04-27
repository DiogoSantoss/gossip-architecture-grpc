package pt.ulisboa.tecnico.classes.namingserver;

import java.util.ArrayList;
import java.util.List;

public class ServiceEntry {

    private String serviceName;
    private List<ServerEntry> serverEntryList;

    private int secondaryCount;

    public ServiceEntry(String serviceName) {
        this.serviceName = serviceName;
        this.serverEntryList = new ArrayList<>();
        this.secondaryCount = 1;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public List<ServerEntry> getServerEntryList() { return serverEntryList; }

    public void incrementSecondaryCount() {
        this.secondaryCount++;
    }

    public int getSecondaryCount() {
        return this.secondaryCount;
    }

    public void setServerEntryList(List<ServerEntry> serverEntryList) {
        this.serverEntryList = serverEntryList;
    }

    public void deleteServerEntry(String host, int port) {
        serverEntryList.removeIf(serverEntry -> serverEntry.getHost().equals(host) && serverEntry.getPort() == port);
    }
}
