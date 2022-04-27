package pt.ulisboa.tecnico.classes;

import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.naming.ClassServerNamingServer.*;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerLookup {

    // Random number generator for server selection
    private static final Random RANDOM = new Random();

    /**
     * Generate random value
     * @param maxValue  The max random value
     * @return          The random value
     */
    public static int getRandomValue(int maxValue){
        return RANDOM.nextInt(maxValue);
    }

    /**
     * Generic function used by clients to get a server address from the naming server
     * @param serviceName   The service name
     * @param qualifiers    The server qualifiers
     * @param stub          The stub to the naming server
     * @return              The server address
     */
    private static List<Address> getServersAddress(String serviceName, String[] qualifiers, NamingServerServiceBlockingStub stub) {

        LookupRequest request = LookupRequest.newBuilder()
                .setServiceName(serviceName)
                .addAllQualifiers(List.of(qualifiers))
                .build();

        LookupResponse response = stub.lookup(request);

        // Create a copy of the original list (because it is unmodifiable)
        return new ArrayList<>(response.getAddressList());
    }

    /**
     * Get servers from naming server
     * @param service       The server's service name
     * @param qualifiers    The server's qualifiers
     * @return              The list of available servers
     */
    public static List<Address> getAvailableServers(String service, String[] qualifiers, NamingServerServiceBlockingStub stub) {

        List<Address> addresses = new ArrayList<>();

        try{
            addresses = getServersAddress(service, qualifiers, stub);
        } catch (StatusRuntimeException e){
            System.err.println("Caught exception with description: " + e.getStatus().getDescription());
            return addresses;
        }

        if(addresses.isEmpty()){
            System.out.println("No server found.");
        }
        return addresses;
    }
}
