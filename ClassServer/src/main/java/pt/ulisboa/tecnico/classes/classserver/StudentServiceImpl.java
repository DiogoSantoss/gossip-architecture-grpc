package pt.ulisboa.tecnico.classes.classserver;

import io.grpc.stub.StreamObserver;

import pt.ulisboa.tecnico.classes.Convert;
import pt.ulisboa.tecnico.classes.VectorClock;
import pt.ulisboa.tecnico.classes.classserver.exception.ClassException;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.student.StudentClassServer.*;
import pt.ulisboa.tecnico.classes.contract.student.StudentServiceGrpc.StudentServiceImplBase;

import pt.ulisboa.tecnico.classes.Debug;

import static io.grpc.Status.INVALID_ARGUMENT;

public class StudentServiceImpl extends StudentServiceImplBase {

    private final Debug debug;
    private final Class studentsClass;
    private final ClassServerFrontend classServerFrontend;


    public StudentServiceImpl(Class studentsClass, ClassServerFrontend classServerFrontend, boolean debugMode) {
        this.studentsClass = studentsClass;
        this.classServerFrontend = classServerFrontend;
        debug = new Debug(StudentServiceImpl.class.getName(), debugMode);
    }

    /**
     * List class state with class capacity, enrollment status and enrolled/discarded students
     * @param request           The request from StudentFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void listClass(ListClassRequest request, StreamObserver<ListClassResponse> responseObserver) {

        boolean updatedServer;

        ResponseCode responseCode = ResponseCode.OK;
        ListClassResponse.Builder response = ListClassResponse.newBuilder();

        if(!studentsClass.getServerStatus().isActive()){
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
     * Enroll student in class
     * @param request           The request from ProfessorFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void enroll(EnrollRequest request, StreamObserver<EnrollResponse> responseObserver) {

        synchronized (studentsClass) {

            boolean updatedServer = false;

            Student student = request.getStudent();

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

                studentsClass.enroll(student.getStudentId(), student.getStudentName());

            } catch (ClassException e){

                switch (e.getMessage()){
                    case "Server is inactive" ->
                        responseCode = ResponseCode.INACTIVE_SERVER;
                    case "Writing not supported" ->
                        responseCode = ResponseCode.WRITING_NOT_SUPPORTED;
                    case "Enrollments are closed" ->
                        responseCode = ResponseCode.ENROLLMENTS_ALREADY_CLOSED;
                    case "Student already enrolled" ->
                        responseCode = ResponseCode.STUDENT_ALREADY_ENROLLED;
                    case "Class is full" ->
                        responseCode = ResponseCode.FULL_CLASS;
                    case "Invalid student id" -> {
                        responseCode = null;
                        debug.log("enroll: " + "ERROR Invalid student id" + " with argument " + student.getStudentId());
                        responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid student id.").asRuntimeException());
                    }
                    case "Invalid student name" -> {
                        responseCode = null;
                        debug.log("enroll: " + "ERROR Invalid student name" + " with arguments " + student.getStudentName());
                        responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid student name.").asRuntimeException());
                    }
                }
            } finally {

                if(responseCode != null){
                    EnrollResponse.Builder enrollResponse = EnrollResponse.newBuilder()
                            .setCode(responseCode);

                    if(updatedServer)
                        enrollResponse.setVectorClockState(Convert.toVectorClockState(studentsClass.getServerStatus().getVectorClock()));
                    else
                        enrollResponse.setVectorClockState(request.getVectorClockState());


                    debug.log("enroll: " + responseCode + " with arguments " + student.getStudentId() + " " + student.getStudentName());

                    responseObserver.onNext(enrollResponse.build());
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
