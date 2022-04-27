package pt.ulisboa.tecnico.classes.namingserver;

import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.classes.Debug;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.naming.ClassServerNamingServer.*;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class NamingServerServiceImpl extends NamingServerServiceImplBase {

    private final int MAX_SERVER_ID = 1000;

    private final Debug debug;
    Random RANDOM = new Random();
    private final ConcurrentHashMap<String, ServiceEntry> namingServices;
    List<Integer> serverIds = new ArrayList<>();

    public NamingServerServiceImpl(boolean debugMode, ConcurrentHashMap<String, ServiceEntry> namingServices) {
        debug = new Debug(NamingServerServiceImpl.class.getName(), debugMode);
        this.namingServices = namingServices;
    }

    /**
     * Register a new server entry for a service
     * @param request           The request from NamingFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {

        synchronized (namingServices) {

            String serviceName = request.getServiceName();
            String host = request.getAddress().getHost();
            int port = request.getAddress().getPort();
            List<String> qualifiers = request.getQualifiersList();
            List<String> qualifiersCopy = new ArrayList<>(qualifiers);

            // generate id for server
            int id = RANDOM.nextInt(MAX_SERVER_ID);
            while(serverIds.contains(id)) {
                // number between [1,1000]
                id = RANDOM.nextInt(MAX_SERVER_ID);
            }
            serverIds.add(id);


            // If service is not registered, create a new serviceEntry for it
            if (!namingServices.containsKey(serviceName)) {
                ServiceEntry serviceEntry = new ServiceEntry(serviceName);
                namingServices.put(serviceName, serviceEntry);
            }

            ServiceEntry serviceEntry = namingServices.get(serviceName);

            // If server is a secondary
            if (qualifiersCopy.contains("S")) {

                // transform S in SX where X is the secondary number, e.g. S1,S2,etc
                String secondary = "S".concat(String.valueOf(serviceEntry.getSecondaryCount()));
                // places SX where S was originally
                qualifiersCopy.set(qualifiersCopy.indexOf("S"), secondary);

                serviceEntry.incrementSecondaryCount();
            }

            ServerEntry serverEntry = new ServerEntry(host, port, id, qualifiersCopy);
            serviceEntry.getServerEntryList().add(serverEntry);

            debug.log("register: " + serviceName + " " + host + ":" + port + " " + id + " " + qualifiersCopy);

            responseObserver.onNext(RegisterResponse.newBuilder().setServerId(id).build());
            responseObserver.onCompleted();
        }
    }

    /**
     * Delete a server entry for a service
     * @param request           The request from NamingFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {

        synchronized (namingServices) {

            String serviceName = request.getServiceName();
            String host = request.getAddress().getHost();
            int port = request.getAddress().getPort();

            // If service is registered, remove server entry if exists
            if (namingServices.containsKey(serviceName)) {
                namingServices.get(serviceName).deleteServerEntry(host, port);
            }

            debug.log("unregister: " + serviceName + " " + host + " " + port);

            responseObserver.onNext(DeleteResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    /**
     * Get a list of server entries for a service
     * @param request           The request from NamingFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void lookup(LookupRequest request, StreamObserver<LookupResponse> responseObserver) {

        synchronized (namingServices) {

            LookupResponse.Builder response = LookupResponse.newBuilder();

            String serviceName = request.getServiceName();
            List<String> qualifiers = request.getQualifiersList();

            // If service is registered
            if (namingServices.containsKey(serviceName)) {

                ServiceEntry serviceEntry = namingServices.get(serviceName);
                List<ServerEntry> serverEntryList = serviceEntry.getServerEntryList();

                // Add all server entries that match the qualifiers
                serverEntryList.forEach(serverEntry -> {
                    if(serverEntry.getQualifiers().stream().anyMatch(qualifiers::contains)) {

                        Address address = Address.newBuilder()
                                .setHost(serverEntry.getHost())
                                .setPort(serverEntry.getPort())
                                .build();

                        response.addAddress(address);
                    }
                    if (qualifiers.contains("S")) {
                        if (serverEntry.getQualifiers().stream().anyMatch(qualifier ->
                                qualifier.matches("^S\\d*$"))) {

                            Address address = Address.newBuilder()
                                    .setHost(serverEntry.getHost())
                                    .setPort(serverEntry.getPort())
                                    .build();

                            response.addAddress(address);
                        }
                    }
                });
            }

            debug.log("lookup: " + serviceName + " " + qualifiers);

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }
    }
}
