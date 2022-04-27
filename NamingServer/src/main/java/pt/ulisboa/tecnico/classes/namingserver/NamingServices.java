package pt.ulisboa.tecnico.classes.namingserver;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class NamingServices {

    public static void main(String[] args) {

        // check arguments
        if(!validArguments(args)){
            System.err.println("Closing Server.");
            return;
        }

        boolean debug = Arrays.asList(args).contains("-debug");

        ConcurrentHashMap<String, ServiceEntry> namingServices = new ConcurrentHashMap<>();
        // create service with namingServices
        final BindableService namingServerImpl = new NamingServerServiceImpl(debug, namingServices);

        int port = Integer.parseInt(args[1]);

        try{

            // create new server to listen on port
            Server namingServer = ServerBuilder.forPort(port)
                    .addService(namingServerImpl)
                    .build();

            namingServer.start();

            System.out.println("Naming server started on port " + port);

            // prevent server from exiting the main thread
            namingServer.awaitTermination();

        } catch (IOException e) {
            System.err.println("Caught IOException when starting naming server: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Caught InterruptedException when waiting for naming server termination: " + e.getMessage());
        } finally {
            System.err.println("Closing naming server.");
            System.exit(0);
        }
    }

    /**
     * Check the validity of the command-line arguments
     * @param args  The command-line arguments
     * @return      The validity of the arguments
     */
    public static boolean validArguments(String[] args) {

        if(args.length < 1) {
            System.err.println("Incorrect number of arguments.");
            return false;
        }

        // Verify port
        try{
            Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number.");
            return false;
        }

        return true;
    }
}
