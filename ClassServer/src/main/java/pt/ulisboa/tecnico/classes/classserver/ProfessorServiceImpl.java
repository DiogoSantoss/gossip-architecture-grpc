package pt.ulisboa.tecnico.classes.classserver;

import io.grpc.stub.StreamObserver;

import pt.ulisboa.tecnico.classes.Convert;
import pt.ulisboa.tecnico.classes.VectorClock;
import pt.ulisboa.tecnico.classes.classserver.exception.ClassException;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.professor.ProfessorClassServer.*;
import pt.ulisboa.tecnico.classes.contract.professor.ProfessorServiceGrpc.ProfessorServiceImplBase;

import pt.ulisboa.tecnico.classes.Debug;

import static io.grpc.Status.INVALID_ARGUMENT;

public class ProfessorServiceImpl extends ProfessorServiceImplBase {

    private final Debug debug;
    private final Class studentsClass;
    private final ClassServerFrontend classServerFrontend;


    public ProfessorServiceImpl(Class studentsClass, ClassServerFrontend classServerFrontend, boolean debugMode){
        this.studentsClass = studentsClass;
        this.classServerFrontend = classServerFrontend;
        debug = new Debug(ProfessorServiceImpl.class.getName(), debugMode);
    }

    /**
     * Open class enrollments with requested capacity
     * @param request           The request from ProfessorFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void openEnrollments(OpenEnrollmentsRequest request, StreamObserver<OpenEnrollmentsResponse> responseObserver) {

        synchronized (studentsClass) {

            boolean updatedServer = false;

            ResponseCode responseCode = ResponseCode.OK;

            try{
                VectorClock clientVectorClock = new VectorClock(request.getVectorClockState().getVectorClockMap());

                // Force a gossip to the other server to update the information
                // if it's outdated in relation to the client's clock
                updatedServer = this.updateServer(clientVectorClock);
                if(!updatedServer){
                    responseCode = ResponseCode.SERVER_NOT_UPDATED;
                    debug.log("Server was unable to update. Data may be out of date.");
                }

                studentsClass.openEnrollments(request.getCapacity());

            } catch(ClassException e){

                switch (e.getMessage()){
                    case "Server is inactive" ->
                            responseCode = ResponseCode.INACTIVE_SERVER;
                    case "Writing not supported" ->
                            responseCode = ResponseCode.WRITING_NOT_SUPPORTED;
                    case "Enrollments already open" ->
                            responseCode = ResponseCode.ENROLLMENTS_ALREADY_OPENED;
                    case "Class is full" ->
                            responseCode = ResponseCode.FULL_CLASS;
                }
            } finally {

                OpenEnrollmentsResponse.Builder openEnrollmentsResponse = OpenEnrollmentsResponse.newBuilder()
                        .setCode(responseCode);

                // Only update client clock if server responded with updated information
                if(updatedServer)
                    openEnrollmentsResponse.setVectorClockState(Convert.toVectorClockState(studentsClass.getServerStatus().getVectorClock()));
                else
                    openEnrollmentsResponse.setVectorClockState(request.getVectorClockState());

                debug.log("openEnrollments: " + responseCode + " with argument: " + request.getCapacity());

                responseObserver.onNext(openEnrollmentsResponse.build());
                responseObserver.onCompleted();
            }
        }
    }

    /**
     * Close class enrollments
     * @param request           The request from ProfessorFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void closeEnrollments(CloseEnrollmentsRequest request, StreamObserver<CloseEnrollmentsResponse> responseObserver) {

        synchronized (studentsClass) {

            boolean updatedServer = false;

            ResponseCode responseCode = ResponseCode.OK;

            try{
                VectorClock clientVectorClock = Convert.toVectorClock(request.getVectorClockState());

                // Force a gossip to the other server to update the information
                // if it's outdated in relation to the client's clock
                updatedServer = this.updateServer(clientVectorClock);
                if(!updatedServer){
                    responseCode = ResponseCode.SERVER_NOT_UPDATED;
                    debug.log("Server was unable to update. Data may be out of date.");
                }

                studentsClass.closeEnrollments();

            } catch(ClassException e){

                switch (e.getMessage()){
                    case "Server is inactive" ->
                            responseCode = ResponseCode.INACTIVE_SERVER;
                    case "Writing not supported" ->
                            responseCode = ResponseCode.WRITING_NOT_SUPPORTED;
                    case "Enrollments already closed" ->
                            responseCode = ResponseCode.ENROLLMENTS_ALREADY_CLOSED;
                }
            } finally {

                CloseEnrollmentsResponse.Builder closeEnrollmentsResponse = CloseEnrollmentsResponse.newBuilder()
                        .setCode(responseCode);

                // Only update client clock if server responded with updated information
                if(updatedServer)
                    closeEnrollmentsResponse.setVectorClockState(Convert.toVectorClockState(studentsClass.getServerStatus().getVectorClock()));
                else
                    closeEnrollmentsResponse.setVectorClockState(request.getVectorClockState());

                debug.log("closeEnrollments: " + responseCode);

                responseObserver.onNext(closeEnrollmentsResponse.build());
                responseObserver.onCompleted();
            }
        }
    }

    /**
     * List class state with class capacity, enrollment status and enrolled/discarded students
     * @param request           The request from ProfessorFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void listClass(ListClassRequest request, StreamObserver<ListClassResponse> responseObserver) {

        boolean updatedServer;

        ResponseCode responseCode = ResponseCode.OK;
        ListClassResponse.Builder response = ListClassResponse.newBuilder();

        if(!studentsClass.getServerStatus().isActive()) {
            responseCode = ResponseCode.INACTIVE_SERVER;
            updatedServer = false;
        }
        else{

            VectorClock clientVectorClock = Convert.toVectorClock(request.getVectorClockState());

            // Force a gossip to the other server to update the information
            // if it's outdated in relation to the client's clock
            updatedServer = this.updateServer(clientVectorClock);
            if(!updatedServer){
                responseCode = ResponseCode.SERVER_NOT_UPDATED;
                debug.log("Server was unable to update. Data may be out of date.");
            }

            response.setClassState(convertToClassState(studentsClass));
        }

        response.setCode(responseCode);

        // Only update client clock if server responded with updated information
        if(updatedServer)
            response.setVectorClockState(Convert.toVectorClockState(studentsClass.getServerStatus().getVectorClock()));
        else
            response.setVectorClockState(request.getVectorClockState());

        debug.log("listClass: " + response.getCode());

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    /**
     * Cancel student enrollment
     * @param request           The request from ProfessorFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void cancelEnrollment(CancelEnrollmentRequest request, StreamObserver<CancelEnrollmentResponse> responseObserver) {

        synchronized (studentsClass) {

            boolean updatedServer = false;

            String studentId = request.getStudentId();

            ResponseCode responseCode = ResponseCode.OK;

            try{
                VectorClock clientVectorClock = Convert.toVectorClock(request.getVectorClockState());

                // Force a gossip to the other server to update the information
                // if it's outdated in relation to the client's clock
                updatedServer = this.updateServer(clientVectorClock);
                if(!updatedServer){
                    responseCode = ResponseCode.SERVER_NOT_UPDATED;
                    debug.log("Server was unable to update. Data may be out of date.");
                }

                studentsClass.cancelEnrollment(studentId);

            } catch (ClassException e){

                switch (e.getMessage()){
                    case "Server is inactive" ->
                            responseCode = ResponseCode.INACTIVE_SERVER;
                    case "Writing not supported" ->
                            responseCode = ResponseCode.WRITING_NOT_SUPPORTED;
                    case "Student not enrolled" ->
                            responseCode = ResponseCode.NON_EXISTING_STUDENT;
                    case "Invalid student id" -> {
                        responseCode = null;
                        debug.log("cancelEnrollment: " + "ERROR Invalid student id" + " with argument: " + studentId);
                        responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid student id.").asRuntimeException());
                    }
                }

            } finally {

                if(responseCode != null){

                    CancelEnrollmentResponse.Builder cancelEnrollmentResponse = CancelEnrollmentResponse.newBuilder()
                            .setCode(responseCode);

                    // Only update client clock if server responded with updated information
                    if(updatedServer)
                        cancelEnrollmentResponse.setVectorClockState(Convert.toVectorClockState(studentsClass.getServerStatus().getVectorClock()));
                    else
                        cancelEnrollmentResponse.setVectorClockState(request.getVectorClockState());

                    debug.log("cancelEnrollment: " + responseCode + " with argument: " + studentId);

                    responseObserver.onNext(cancelEnrollmentResponse.build());
                    responseObserver.onCompleted();
                }
            }
        }
    }

    /**
     * Verify if server is ahead of client, if not force a gossip to update
     * @param clientVectorClock The client's clock
     * @return updatedServer    True if server successfully updated, else false
     */
    public boolean updateServer(VectorClock clientVectorClock){

        if(studentsClass.getServerStatus().getVectorClock().happensBefore(clientVectorClock)){
            debug.log("openEnrollments: Client contacted outdated server, forcing server to update...");
            return classServerFrontend.gossip(studentsClass, "update", clientVectorClock);
        } else {
            return true;
        }
    }

    /**
     * Convert Class to ClassState
     * @param studentsClass The class to be converted
     * @return              The class state
     */
    public ClassState convertToClassState(Class studentsClass){

        // Add enrollment status and class capacity
        ClassState.Builder classState = ClassState.newBuilder()
                .setOpenEnrollments(studentsClass.isOpenEnrollments())
                .setCapacity(studentsClass.getCapacity());

        // Add each enrolled student to state
        studentsClass.getEnrolled().forEach((studentId, studentName) -> {

            Student student = Student.newBuilder()
                    .setStudentId(studentId)
                    .setStudentName(studentName)
                    .build();
            classState.addEnrolled(student);
        });

        // Add each discarded student to state
        studentsClass.getDiscarded().forEach((studentId, studentName) -> {

            Student student = Student.newBuilder()
                    .setStudentId(studentId)
                    .setStudentName(studentName)
                    .build();
            classState.addDiscarded(student);
        });

        return classState.build();
    }
}
