package pt.ulisboa.tecnico.classes.admin;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import pt.ulisboa.tecnico.classes.Debug;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.ResponseCode;
import pt.ulisboa.tecnico.classes.contract.admin.AdminClassServer.*;
import pt.ulisboa.tecnico.classes.contract.admin.AdminServiceGrpc;
import pt.ulisboa.tecnico.classes.contract.admin.AdminServiceGrpc.*;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc.*;

import pt.ulisboa.tecnico.classes.Stringify;
import pt.ulisboa.tecnico.classes.ServerLookup;

import java.util.ArrayList;
import java.util.List;


public class AdminFrontend {

    private final Debug debug;

    private List<Address> addresses = new ArrayList<>();

    private final ManagedChannel namingServerChannel;
    private final NamingServerServiceBlockingStub namingServerStub;

    private ManagedChannel classServerChannel;
    private AdminServiceBlockingStub classServerStub;


    public AdminFrontend(boolean debugMode) {

        // debug to log messages
        debug = new Debug(AdminFrontend.class.getName(),debugMode);
        // create channel and stub to naming services at hardcoded address
        namingServerChannel = ManagedChannelBuilder.forAddress("localhost", 5000).usePlaintext().build();
        namingServerStub = NamingServerServiceGrpc.newBlockingStub(namingServerChannel);
    }

    public List<Address> getAddresses() { return addresses; }

    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public NamingServerServiceBlockingStub getNamingServerStub() { return namingServerStub; }

    public AdminServiceBlockingStub getClassServerStub() { return classServerStub; }

    public void setClassServerStub(AdminServiceBlockingStub classServerStub) { this.classServerStub = classServerStub; }

    public ManagedChannel getClassServerChannel() { return classServerChannel; }

    public void setClassServerChannel(ManagedChannel classServerChannel) { this.classServerChannel = classServerChannel; }

    public ManagedChannel getNamingServerChannel() { return namingServerChannel; }

    /**
     * Get servers from naming server
     * @param service       The server's service name
     * @param qualifiers    The server's qualifiers
     * @return              True if there are available servers, False otherwise
     */
    private boolean findServers(String service, String[] qualifiers) {

        setAddresses(ServerLookup.getAvailableServers(service, qualifiers, getNamingServerStub()));
        return getAddresses().isEmpty();
    }

    /**
     * Create channel and stub to a server
     */
    private void createChannelAndStub() {

        // Select a random server from the list
        int randomValue = ServerLookup.getRandomValue(getAddresses().size());
        Address address = getAddresses().get(randomValue);

        // Create channel and stub to the selected server
        setClassServerChannel(ManagedChannelBuilder.forAddress(address.getHost(), address.getPort())
                .usePlaintext()
                .build());
        setClassServerStub(AdminServiceGrpc.newBlockingStub(classServerChannel));

        debug.log("Connecting to server at " + address.getHost() + ":" + address.getPort());

        // Remove chosen server to avoid re-sending request to the same server
        getAddresses().remove(address);
    }

    /**
     * Change server state to ACTIVE
     * Only changes the status of one server
     * If there are multiple primary (or secondary) servers, it will choose one randomly
     * @param qualifier The server's qualifier
     */
    public void activate(String qualifier) {

        if(findServers("turmas", new String[]{qualifier})) return;

        createChannelAndStub();

        ActivateRequest request = ActivateRequest.getDefaultInstance();
        ActivateResponse response;

        try {
            response = getClassServerStub().activate(request);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return;
        }

        ResponseCode responseCode = response.getCode();
        System.out.println(Stringify.format(responseCode));

        debug.log("activate: " + responseCode + " with argument " + qualifier);

        getClassServerChannel().shutdown();
    }

    /**
     * Change server state to INACTIVE
     * Only changes the status of one server
     * If there are multiple primary (or secondary) servers, it will choose one randomly
     * @param qualifier The server's qualifier
     */
    public void deactivate(String qualifier) {

        if(findServers("turmas", new String[]{qualifier})) return;

        createChannelAndStub();

        DeactivateRequest request = DeactivateRequest.getDefaultInstance();
        DeactivateResponse response;

        try {
            response = getClassServerStub().deactivate(request);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return;
        }

        ResponseCode responseCode = response.getCode();
        System.out.println(Stringify.format(responseCode));

        debug.log("deactivate: " + responseCode + " with argument " + qualifier);

        getClassServerChannel().shutdown();
    }

    /**
     * Report server state
     * Will report the state of a single server
     * If there are multiple, it will choose one randomly
     * @param qualifier The server's qualifier
     */
    public void dump(String qualifier) {

        if(findServers("turmas", new String[]{qualifier})) return;

        createChannelAndStub();

        DumpRequest request = DumpRequest.getDefaultInstance();
        DumpResponse response;

        try{
            response = getClassServerStub().dump(request);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return;
        }

        ResponseCode responseCode = response.getCode();
        if(responseCode == ResponseCode.OK){
            System.out.println(Stringify.format(response.getClassState()));
        }
        else{
            System.out.println(Stringify.format(responseCode));
        }

        debug.log("dump: " + responseCode + " with argument " + qualifier);

        getClassServerChannel().shutdown();
    }

    /**
     * Change server gossip state to ACTIVE_GOSSIP
     * Only changes the status of one server
     * If there are multiple primary (or secondary) servers, it will choose one randomly
     * @param qualifier The server's qualifier
     */
    public void activateGossip(String qualifier) {

        if(findServers("turmas", new String[]{qualifier})) return;

        createChannelAndStub();

        ActivateGossipRequest request = ActivateGossipRequest.getDefaultInstance();
        ActivateGossipResponse response;

        try {
            response = getClassServerStub().activateGossip(request);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return;
        }

        ResponseCode responseCode = response.getCode();
        System.out.println(Stringify.format(responseCode));

        debug.log("activateGossip: " + responseCode + " with argument " + qualifier);

        getClassServerChannel().shutdown();
    }

    /**
     * Change server gossip state to INACTIVE_GOSSIP
     * Only changes the status of one server
     * If there are multiple primary(or secondary) servers, it will choose one randomly
     * @param qualifier The server's qualifier
     */
    public void deactivateGossip(String qualifier) {

        if(findServers("turmas", new String[]{qualifier})) return;

        createChannelAndStub();

        DeactivateGossipRequest request = DeactivateGossipRequest.getDefaultInstance();
        DeactivateGossipResponse response;

        try {
            response = getClassServerStub().deactivateGossip(request);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return;
        }

        ResponseCode responseCode = response.getCode();
        System.out.println(Stringify.format(responseCode));

        debug.log("deactivateGossip: " + responseCode + " with argument " + qualifier);

        getClassServerChannel().shutdown();
    }

    /**
     * Force gossip from a server
     * Only forces one server to gossip
     * If there are multiple primary (or secondary) servers, it will choose one randomly
     * @param qualifier The server's qualifier
     */
    public void forceGossip(String qualifier){

        if(findServers("turmas", new String[]{qualifier})) return;

        createChannelAndStub();

        ForceGossipRequest request = ForceGossipRequest.getDefaultInstance();
        ForceGossipResponse response;

        try {
            response = getClassServerStub().forceGossip(request);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return;
        }

        ResponseCode responseCode = response.getCode();
        System.out.println(Stringify.format(responseCode));

        debug.log("forceGossip: " + responseCode + " with argument " + qualifier);

        getClassServerChannel().shutdown();
    }

    /**
     * Close channel
     */
    public void close(){
        getNamingServerChannel().shutdown();
    }
}
