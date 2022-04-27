package pt.ulisboa.tecnico.classes.classserver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.Timer;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class ClassServer {

  private static final int TIMER_PERIOD = 1000;

  public static void main(String[] args) {

    // check arguments
    if(!validArguments(args)){
      System.err.println("Closing Server.");
      return;
    }

    boolean debug = Arrays.asList(args).contains("-debug");

    String host = args[1];
    int port = Integer.parseInt(args[2]);
    List<String> qualifiers = parseQualifiers(args);

    final NamingServerFrontend namingServerFrontend = new NamingServerFrontend();
    final ClassServerFrontend classServerFrontend = new ClassServerFrontend(debug);

    ServerStatus serverStatus = new ServerStatus(host,port,qualifiers);

    Class studentsClass = new Class(serverStatus);
    // create services all with the same studentsClass
    final BindableService adminImpl = new AdminServiceImpl(studentsClass, classServerFrontend, debug);
    final BindableService studentImpl = new StudentServiceImpl(studentsClass, classServerFrontend, debug);
    final BindableService professorImpl = new ProfessorServiceImpl(studentsClass, classServerFrontend, debug);
    final BindableService classServerImpl = new ClassServerServiceImpl(studentsClass, debug);

    try {

      // create new server to listen on port
      Server server = ServerBuilder.forPort(port)
        .addService(classServerImpl)
        .addService(professorImpl)
        .addService(studentImpl)
        .addService(adminImpl)
        .build();

      // register server in the naming service and get server id
      String serviceName = args[0];
      int id = namingServerFrontend.register(serviceName, host, port, qualifiers);
      serverStatus.setServerId(id);
      serverStatus.getVectorClock().addServerId(id);

      // start server after register
      server.start();

      // set timer task to gossip with other servers
      Timer timer = new Timer();
      timer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              if (serverStatus.isChanged()) classServerFrontend.gossip(studentsClass, "timer");
            }
          },
          0,
          TIMER_PERIOD);

      // Shutdownhook to unregister server from naming service
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        namingServerFrontend.delete(serviceName, host, port);
        namingServerFrontend.close();
        classServerFrontend.close();
        server.shutdown();
        timer.cancel();
        System.out.println("Received SIGINT signal, closing server.");
      }));

      System.out.println("Server started on port " + port + " with id " + id);

      // prevent server from exiting the main thread
      server.awaitTermination();

    } catch (IOException e) {
      System.err.println("Caught IOException when starting server: " + e.getMessage());
    } catch (InterruptedException e) {
      System.err.println("Caught InterruptedException when waiting for server termination: " + e.getMessage());
    } finally {
      System.err.println("Closing server.");
      System.exit(0);
    }
  }

  /**
   * Check the validity of the command-line arguments
   * @param args  The command-line arguments
   * @return      The validity of the arguments
   */
  public static boolean validArguments(String[] args) {

    if(args.length < 4) {
      System.err.println("Incorrect number of arguments.");
      return false;
    }

    // Verify port
    try{
      Integer.parseInt(args[2]);
    } catch (NumberFormatException e) {
      System.err.println("Invalid port number.");
      return false;
    }

    // Verify qualifier
    String qualifier = args[3];
    if(!qualifier.equals("P") && !qualifier.equals("S")){
      System.err.println("Invalid qualifier.");
      return false;
    }

    return true;
  }

  /**
   * Parse the qualifiers from the command-line arguments
   * @param args  The command-line arguments
   * @return      The qualifiers list
   */
  public static List<String> parseQualifiers(String[] args) {
    return Arrays.stream(args).filter(arg -> arg.equals("P") || arg.equals("S")).collect(Collectors.toList());
  }
}
