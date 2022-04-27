package pt.ulisboa.tecnico.classes.classserver;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.classes.Convert;
import pt.ulisboa.tecnico.classes.Debug;
import pt.ulisboa.tecnico.classes.ServerLookup;
import pt.ulisboa.tecnico.classes.VectorClock;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.classserver.ClassServerClassServer.*;
import pt.ulisboa.tecnico.classes.contract.classserver.ClassServerServiceGrpc;
import pt.ulisboa.tecnico.classes.contract.classserver.ClassServerServiceGrpc.*;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class ClassServerFrontend {

    private final Debug debug;

    private List<Address> addresses = new ArrayList<Address>();

    private final ManagedChannel namingServerChannel;
    private final NamingServerServiceBlockingStub namingServerStub;

    private ManagedChannel classServerChannel;
    private ClassServerServiceBlockingStub classServerStub;

    public ClassServerFrontend(boolean debugMode) {

        // debug to log messages
        debug = new Debug(ClassServerFrontend.class.getName(),debugMode);
        // create channel and stub to naming services at hardcoded address
        namingServerChannel = ManagedChannelBuilder.forAddress("localhost", 5000).usePlaintext().build();
        namingServerStub = NamingServerServiceGrpc.newBlockingStub(namingServerChannel);
    }

    public List<Address>    getAddresses() { return addresses; }

    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public NamingServerServiceBlockingStub getNamingServerStub() {
        return namingServerStub;
    }

    public ClassServerServiceBlockingStub getClassServerStub() {
        return classServerStub;
    }

    public void setClassServerStub(ClassServerServiceBlockingStub classServerStub) { this.classServerStub = classServerStub; }

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
    public Address createChannelAndStub() {

        // Select a random server from the list
        int randomValue = ServerLookup.getRandomValue(getAddresses().size());
        Address address = getAddresses().get(randomValue);

        // Create channel and stub to the selected server
        setClassServerChannel(ManagedChannelBuilder.forAddress(address.getHost(), address.getPort())
                .usePlaintext()
                .build());
        setClassServerStub(ClassServerServiceGrpc.newBlockingStub(classServerChannel));

        // Remove chosen server to avoid re-sending request to the same server
        getAddresses().remove(address);

        return address;
    }

    /**
     * Gossip with other servers
     * @param studentsClass
     * @param event
     * @return
     */
    public boolean gossip(Class studentsClass, String event){
        return this.gossip(studentsClass, event, null);
    }

    /**
     * Gossip with other servers
     * @param studentsClass The class
     */
    public boolean gossip(Class studentsClass, String event, VectorClock clientVectorClock) {

        if (!this.verifyServerStatus(studentsClass, event)) { return false; }

        if (findServers("turmas", new String[]{"P","S"})) { return false; }

        String host = studentsClass.getServerStatus().getHost();
        int port = studentsClass.getServerStatus().getPort();
        Address address = Address.newBuilder().setHost(host).setPort(port).build();
        // Remove himself from the list
        getAddresses().remove(address);

        // If there are no servers to gossip with, return
        if(getAddresses().isEmpty()){
            debug.log("No server available to gossip with.");
            return false;
        }

        int serverId = studentsClass.getServerStatus().getServerId();
        ClassState classState = convertToClassState(studentsClass);
        VectorClockState vectorClockState = Convert.toVectorClockState(studentsClass.getServerStatus().getVectorClock());

        GossipRequest request = GossipRequest.newBuilder()
                .setClassState(classState)
                .setServerQualifier(studentsClass.getServerStatus().getQualifiers().get(0))
                .setVectorClockState(vectorClockState)
                .setServerId(serverId)
                .build();

        List<GossipResponse> responses = new ArrayList<>();

        do {
            address = createChannelAndStub();

            try {
                debug.log("Gossip started with " + address.getHost() + ":" + address.getPort());
                responses.add(getClassServerStub().gossip(request));
            } catch (StatusRuntimeException e) {
                System.err.println("Caught exception with description: " + e.getStatus().getDescription());
                return false;
            }
            getClassServerChannel().shutdown();

        } while (!getAddresses().isEmpty());

        if(responses.stream().allMatch((response) -> response.getCode() == ResponseCode.OK)){
            studentsClass.getServerStatus().setChanged(false);
        }

        // By deafault chose first response
        GossipResponse gossipResponse = responses.get(0);

        // If there are multiple responses, choose the one that is up-to-date with the client
        if(clientVectorClock != null){

            for(GossipResponse response : responses){
                if(
                    clientVectorClock.happensBefore(Convert.toVectorClock(response.getVectorClockState())) ||
                    clientVectorClock.equals(Convert.toVectorClock(response.getVectorClockState()))
                ){
                    gossipResponse = response;
                    break;
                }
            }
        // If there are multiple responses, choose the one that has a response code OK
        } else {
            do {
                gossipResponse = responses.get(0);
                responses.remove(0);
            } while(gossipResponse.getCode() != ResponseCode.OK && !responses.isEmpty());
        }

        // If the response was OK then it's safe to update our server
        if(gossipResponse.getCode() == ResponseCode.OK){

            debug.log(
                "Gossip finished successfully," +
                " previous clock: " +
                studentsClass.getServerStatus().getVectorClock() +
                " updated clock: " +
                Convert.toVectorClock(gossipResponse.getVectorClockState())
            );

            this.updateClass(studentsClass, gossipResponse);
        }

        return true;
    }

    /**
     * Close channel
     */
    public void close(){
        getNamingServerChannel().shutdown();
    }

    /**
     * Verify server status conditions (depending on the type of request)
     * @param studentsClass The student's class
     * @param event         The event
     * @return result       True if conditions met, else false
     */
    public boolean verifyServerStatus(Class studentsClass, String event){

        boolean result = true;

        switch (event) {

            case "update" -> {
                if(!studentsClass.getServerStatus().isActive() || !studentsClass.getServerStatus().isGossipActive()){
                    result = false;
                }
            }

            case "admin" -> {
                if(!studentsClass.getServerStatus().isActive()){
                    result = false;
                }
            }

            case "timer" -> {
                if(!studentsClass.getServerStatus().isChanged() || !studentsClass.getServerStatus().isActive()
                        || !studentsClass.getServerStatus().isGossipActive()) {
                    result = false;
                }
            }

            default -> result = false;
        }

        return result;
    }


    /**
     * Update studentsClass with gossip response
     * @param currentStudentClass   The student's class
     * @param response              The gossip response
     */
    public void updateClass(Class currentStudentClass, GossipResponse response){

        Class updatedStudentClass = convertToClass(response.getClassState());
        VectorClock updatedVectorClock = Convert.toVectorClock(response.getVectorClockState());

        currentStudentClass.setCapacity(updatedStudentClass.getCapacity());
        currentStudentClass.setOpenEnrollments(updatedStudentClass.isOpenEnrollments());
        currentStudentClass.setEnrolled(updatedStudentClass.getEnrolled());
        currentStudentClass.setDiscarded(updatedStudentClass.getDiscarded());
        currentStudentClass.setTimestamps(updatedStudentClass.getTimestamps());
        currentStudentClass.getServerStatus().setVectorClock(updatedVectorClock);
    }

    /**
     * Convert Class to ClassState
     * @param studentsClass The student class
     * @return              The ClassState
     */
    public static ClassState convertToClassState(Class studentsClass){

        ClassState.Builder classState = ClassState.newBuilder()
                .setCapacity(studentsClass.getCapacity())
                .setOpenEnrollments(studentsClass.isOpenEnrollments());

        // Add each enrolled student to state
        studentsClass.getEnrolled().forEach((studentId, studentName) -> {
            Student student = Student.newBuilder()
                    .setStudentId(studentId)
                    .setStudentName(studentName)
                    .setTimestamp(Convert.toGoogleTimestamp(studentsClass.getStudentTimestamp(studentId)))
                    .build();
            classState.addEnrolled(student);
        });

        // Add each discarded student to state
        studentsClass.getDiscarded().forEach((studentId, studentName) -> {
            Student student = Student.newBuilder()
                    .setStudentId(studentId)
                    .setStudentName(studentName)
                    .setTimestamp(Convert.toGoogleTimestamp(studentsClass.getStudentTimestamp(studentId)))
                    .build();
            classState.addDiscarded(student);
        });

        return classState.build();
    }

    /**
     * Convert ClassState to Class
     * @param classState ClassState to convert
     * @return Class
     */
    public static Class convertToClass(ClassState classState){

        Class studentsClass = new Class(null);

        studentsClass.setCapacity(classState.getCapacity());
        studentsClass.setOpenEnrollments(classState.getOpenEnrollments());

        ConcurrentHashMap<String, Instant> timestamps = new ConcurrentHashMap<>();

        ConcurrentHashMap<String, String> enrolledStudents = new ConcurrentHashMap<>();
        for (Student s: classState.getEnrolledList()){
            enrolledStudents.put(s.getStudentId(), s.getStudentName());
            timestamps.put(s.getStudentId(), Convert.toInstant(s.getTimestamp()));
        }
        ConcurrentHashMap<String, String> discardedStudents = new ConcurrentHashMap<>();
        for (Student s: classState.getDiscardedList()){
            discardedStudents.put(s.getStudentId(), s.getStudentName());
            timestamps.put(s.getStudentId(), Convert.toInstant(s.getTimestamp()));
        }

        studentsClass.setEnrolled(enrolledStudents);
        studentsClass.setDiscarded(discardedStudents);
        studentsClass.setTimestamps(timestamps);

        return studentsClass;
    }
}