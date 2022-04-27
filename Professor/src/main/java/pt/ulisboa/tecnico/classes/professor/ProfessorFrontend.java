package pt.ulisboa.tecnico.classes.professor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import pt.ulisboa.tecnico.classes.*;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.Address;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.ResponseCode;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc;
import pt.ulisboa.tecnico.classes.contract.naming.NamingServerServiceGrpc.*;
import pt.ulisboa.tecnico.classes.contract.professor.ProfessorServiceGrpc;
import pt.ulisboa.tecnico.classes.contract.professor.ProfessorServiceGrpc.*;
import pt.ulisboa.tecnico.classes.contract.professor.ProfessorClassServer.*;

import java.util.ArrayList;
import java.util.List;


public class ProfessorFrontend {

    private final Debug debug;

    private VectorClock vectorClock;
    private List<Address> addresses = new ArrayList<>();

    private final ManagedChannel namingServerChannel;
    private final NamingServerServiceBlockingStub namingServerStub;

    private ManagedChannel classServerChannel;
    private ProfessorServiceBlockingStub classServerStub;


    public ProfessorFrontend(boolean debugMode) {

        // debug to log messages
        debug = new Debug(ProfessorFrontend.class.getName(), debugMode);
        // initialize client vector clock
        vectorClock = new VectorClock();
        // create channel and stub to naming services at hardcoded address
        namingServerChannel = ManagedChannelBuilder.forAddress("localhost", 5000).usePlaintext().build();
        namingServerStub = NamingServerServiceGrpc.newBlockingStub(namingServerChannel);
    }

    public List<Address> getAddresses() { return addresses; }

    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public void setVectorClock(VectorClock vectorClock) { this.vectorClock = vectorClock; }

    public VectorClock getVectorClock() { return vectorClock; }

    public NamingServerServiceBlockingStub getNamingServerStub() {
        return namingServerStub;
    }

    public ProfessorServiceBlockingStub getClassServerStub() { return classServerStub; }

    public void setClassServerStub(ProfessorServiceBlockingStub classServerStub) { this.classServerStub = classServerStub; }

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
        setClassServerStub(ProfessorServiceGrpc.newBlockingStub(classServerChannel));

        debug.log("Contacting server at " + address.getHost() + ":" + address.getPort());

        // Remove chosen server to avoid re-sending request to the same server
        getAddresses().remove(address);
    }

    /**
     * Open class enrollments with requested capacity
     * @param capacity The class capacity
     */
    public void openEnrollments(Integer capacity){

        if (findServers("turmas", new String[]{"P"})) return;

        OpenEnrollmentsRequest request = OpenEnrollmentsRequest.newBuilder()
                .setCapacity(capacity)
                .setVectorClockState(Convert.toVectorClockState(vectorClock))
                .build();
        OpenEnrollmentsResponse response;
        ResponseCode responseCode;

        do {
            createChannelAndStub();

            try {
                response = getClassServerStub().openEnrollments(request);
            } catch (StatusRuntimeException e){
                System.err.println("Caught exception with description: " + e.getStatus().getDescription());
                return;
            }

            responseCode = response.getCode();
            System.out.println(Stringify.format(responseCode));

            if(responseCode == ResponseCode.INACTIVE_SERVER && !addresses.isEmpty()) {
                System.out.println("Trying another server...");
            }
            getClassServerChannel().shutdown();

        } while(responseCode == ResponseCode.INACTIVE_SERVER && !getAddresses().isEmpty());

        // Update client vector clock to match the server's vector clock
        setVectorClock(Convert.toVectorClock(response.getVectorClockState()));

        debug.log("openEnrollments: " + responseCode + " with argument: " + capacity + " and vector clock: " + getVectorClock());
    }

    /**
     * Close class enrollments
     */
    public void closeEnrollments(){

        if (findServers("turmas", new String[]{"P"})) return;

        CloseEnrollmentsRequest request = CloseEnrollmentsRequest.newBuilder()
                .setVectorClockState(Convert.toVectorClockState(vectorClock))
                .build();
        CloseEnrollmentsResponse response;
        ResponseCode responseCode;

        do {
            createChannelAndStub();

            try {
                response = getClassServerStub().closeEnrollments(request);
            } catch (StatusRuntimeException e){
                System.err.println("Caught exception with description: " + e.getStatus().getDescription());
                return;
            }

            responseCode = response.getCode();
            System.out.println(Stringify.format(responseCode));

            if(responseCode == ResponseCode.INACTIVE_SERVER && !addresses.isEmpty()) {
                System.out.println("Trying another server...");
            }
            getClassServerChannel().shutdown();

        } while(responseCode == ResponseCode.INACTIVE_SERVER && !getAddresses().isEmpty());

        // Update client vector clock to match the server's vector clock
        setVectorClock(Convert.toVectorClock(response.getVectorClockState()));

        debug.log("closeEnrollments: " + responseCode + " and vector clock: " + getVectorClock());
    }

    /**
     * List class state with class capacity, enrollment status and enrolled/discarded students
     */
    public void listClass(){

        if(findServers("turmas", new String[]{"P","S"})) return;

        ListClassRequest request = ListClassRequest.newBuilder()
                .setVectorClockState(Convert.toVectorClockState(vectorClock))
                .build();
        ListClassResponse response;
        ResponseCode responseCode;

        do {
            createChannelAndStub();

            try{
                response = getClassServerStub().listClass(request);
            } catch (StatusRuntimeException e){
                System.err.println("Caught exception with description: " + e.getStatus().getDescription());
                return;
            }

            responseCode = response.getCode();

            if(responseCode == ResponseCode.INACTIVE_SERVER && !addresses.isEmpty()){
                System.out.println(Stringify.format(responseCode));
                System.out.println("Trying another server...");
            }
            getClassServerChannel().shutdown();

        } while (responseCode == ResponseCode.INACTIVE_SERVER && !getAddresses().isEmpty());

        // Update client vector clock to match the server's vector clock
        setVectorClock(Convert.toVectorClock(response.getVectorClockState()));

        if(responseCode == ResponseCode.OK){
            System.out.println(Stringify.format(response.getClassState()));
        }
        else if(responseCode == ResponseCode.SERVER_NOT_UPDATED){
            System.out.println(Stringify.format(response.getClassState()));
            System.out.println(Stringify.format(responseCode));
        }
        else{
            System.out.println(Stringify.format(responseCode));
        }

        debug.log("listClass: " + responseCode + " and vector clock: " + getVectorClock());
    }

    /**
     * Cancel student enrollment
     * @param studentId The student id
     */
    public void cancelEnrollment(String studentId){

        if (findServers("turmas", new String[]{"P","S"})) return;

        CancelEnrollmentRequest request = CancelEnrollmentRequest.newBuilder()
                .setStudentId(studentId)
                .setVectorClockState(Convert.toVectorClockState(vectorClock))
                .build();
        CancelEnrollmentResponse response;
        ResponseCode responseCode;

        do {
            createChannelAndStub();

            try {
                response = getClassServerStub().cancelEnrollment(request);
            } catch (StatusRuntimeException e){
                System.err.println("Caught exception with description: " + e.getStatus().getDescription());
                return;
            }

            responseCode = response.getCode();
            System.out.println(Stringify.format(responseCode));

            if(responseCode == ResponseCode.INACTIVE_SERVER && !addresses.isEmpty()) {
                System.out.println("Trying another server...");
            }
            getClassServerChannel().shutdown();

        } while(responseCode == ResponseCode.INACTIVE_SERVER && !getAddresses().isEmpty());

        // Update client vector clock to match the server's vector clock
        setVectorClock(Convert.toVectorClock(response.getVectorClockState()));

        debug.log("cancelEnrollment: " + responseCode + " with arguments " + studentId + " and vector clock: " + getVectorClock());
    }

    /**
     * Close channel
     */
    public void close(){
        getNamingServerChannel().shutdown();
    }
}
