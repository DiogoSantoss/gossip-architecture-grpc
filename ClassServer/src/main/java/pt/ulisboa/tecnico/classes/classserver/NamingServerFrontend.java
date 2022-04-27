package pt.ulisboa.tecnico.classes.classserver;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions;
import pt.ulisboa.tecnico.classes.contract.naming.ClassServerNamingServer.*;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc;

import java.util.List;

public class NamingServerFrontend {

    private final ManagedChannel namingServerChannel;
    private final NamingServerServiceGrpc.NamingServerServiceBlockingStub namingServerStub;

    public NamingServerFrontend() {

        // create channel and stub to naming services at hardcoded address
        namingServerChannel = ManagedChannelBuilder.forAddress("localhost", 5000).usePlaintext().build();
        namingServerStub = NamingServerServiceGrpc.newBlockingStub(namingServerChannel);
    }

    public NamingServerServiceGrpc.NamingServerServiceBlockingStub getNamingServerStub() {
        return namingServerStub;
    }

    public ManagedChannel getNamingServerChannel() { return namingServerChannel; }


    /**
     * Register server in the naming server
     * @param serviceName The service name
     * @param host        The host
     * @param port        The port
     * @param qualifiers  The qualifiers
     */
    public int register(String serviceName, String host, int port, List<String> qualifiers) {

        ClassesDefinitions.Address address = ClassesDefinitions.Address.newBuilder().setHost(host).setPort(port).build();

        RegisterRequest request = RegisterRequest.newBuilder()
                .setServiceName(serviceName)
                .setAddress(address)
                .addAllQualifiers(qualifiers)
                .build();

        try {
            RegisterResponse response = getNamingServerStub().register(request);
            return response.getServerId();
        } catch (StatusRuntimeException e) {
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            // throws exception to prevent server from staying alive since it was not registered
            throw e;
        }
    }

    /**
     * Delete server from the naming server
     * @param serviceName The service name
     * @param host        The host
     * @param port        The port
     */
    public void delete(String serviceName, String host, int port) {

        ClassesDefinitions.Address address = ClassesDefinitions.Address.newBuilder().setHost(host).setPort(port).build();

        DeleteRequest request = DeleteRequest.newBuilder()
                .setServiceName(serviceName)
                .setAddress(address)
                .build();

        try {
            getNamingServerStub().delete(request);
        } catch (StatusRuntimeException e) {
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
        }
    }

    /**
     * Close channel
     */
    public void close(){
        getNamingServerChannel().shutdown();
    }
}
