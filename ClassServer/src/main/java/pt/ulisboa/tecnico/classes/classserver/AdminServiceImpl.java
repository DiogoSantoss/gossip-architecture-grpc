package pt.ulisboa.tecnico.classes.classserver;

import io.grpc.stub.StreamObserver;

import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.admin.AdminClassServer.*;
import pt.ulisboa.tecnico.classes.contract.admin.AdminServiceGrpc.AdminServiceImplBase;

import pt.ulisboa.tecnico.classes.Debug;

public class AdminServiceImpl extends AdminServiceImplBase  {

    private final Debug debug;
    private final Class studentsClass;
    private final ClassServerFrontend classServerFrontend;


    public AdminServiceImpl(Class studentsClass, ClassServerFrontend classServerFrontend, boolean debugMode) {
        this.studentsClass = studentsClass;
        this.classServerFrontend = classServerFrontend;
        debug = new Debug(AdminServiceImpl.class.getName(), debugMode);
    }

    /**
     * Change server state to ACTIVE (server responds to all requests)
     * @param request           The request from AdminFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void activate(ActivateRequest request, StreamObserver<ActivateResponse> responseObserver) {

        synchronized (studentsClass) {

            studentsClass.activate();
            ResponseCode responseCode = ResponseCode.OK;
            ActivateResponse activateResponse = ActivateResponse.newBuilder()
                    .setCode(responseCode)
                    .build();

            debug.log("activate: " + responseCode);
            debug.log(
                "deactivateGossip:" +
                " Server Active: " +
                this.studentsClass.getServerStatus().isActive() +
                " | Gossip Active: " +
                this.studentsClass.getServerStatus().isGossipActive()
            );

            responseObserver.onNext(activateResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * Change server state to INACTIVE (server responds with DISABLED to all requests)
     * @param request           The request from AdminFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void deactivate(DeactivateRequest request, StreamObserver<DeactivateResponse> responseObserver) {

        synchronized (studentsClass){

            studentsClass.deactivate();
            ResponseCode responseCode = ResponseCode.OK;
            DeactivateResponse deactivateResponse = DeactivateResponse.newBuilder()
                    .setCode(responseCode)
                    .build();

            debug.log("deactivate: " + responseCode);
            debug.log(
                "deactivateGossip:" +
                " Server Active: " +
                this.studentsClass.getServerStatus().isActive() +
                " | Gossip Active: " +
                this.studentsClass.getServerStatus().isGossipActive()
            );

            responseObserver.onNext(deactivateResponse);
            responseObserver.onCompleted();
        }
    }

  /**
   * List class state with class capacity, enrollment status and enrolled/discarded students
   * @param request             The request from AdminFrontend
   * @param responseObserver    The stream where response will be sent
   */
      @Override
      public void dump(DumpRequest request, StreamObserver<DumpResponse> responseObserver) {

        ResponseCode responseCode = ResponseCode.OK;
        DumpResponse response = DumpResponse.newBuilder()
                .setCode(responseCode)
                .setClassState(convertToClassState(studentsClass))
                .build();

        debug.log("dump: " + response.getCode());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
      }


    /**
     * Change server gossip state to ACTIVE_GOSSIP
     * @param request           The request from AdminFrontend
     * @param responseObserver  The stream where response will be sent
     */
      public void activateGossip(ActivateGossipRequest request, StreamObserver<ActivateGossipResponse> responseObserver) {
          synchronized (studentsClass){

              studentsClass.activateGossip();
              ResponseCode responseCode = ResponseCode.OK;
              ActivateGossipResponse activateGossipResponse = ActivateGossipResponse.newBuilder()
                      .setCode(responseCode)
                      .build();

              debug.log("activateGossip: " + responseCode);
              debug.log(
                  "deactivateGossip:" +
                  " Server Active: " +
                  this.studentsClass.getServerStatus().isActive() +
                  " | Gossip Active: " +
                  this.studentsClass.getServerStatus().isGossipActive()
              );

              responseObserver.onNext(activateGossipResponse);
              responseObserver.onCompleted();
          }
      }

    /**
     * Change server gossip state to INACTIVE_GOSSIP
     * @param request           The request from AdminFrontend
     * @param responseObserver  The stream where response will be sent
     */
    public void deactivateGossip(DeactivateGossipRequest request, StreamObserver<DeactivateGossipResponse> responseObserver) {
      synchronized (studentsClass){

          studentsClass.deactivateGossip();
          ResponseCode responseCode = ResponseCode.OK;
          DeactivateGossipResponse deactivateGossipResponse = DeactivateGossipResponse
                  .newBuilder()
                  .setCode(responseCode)
                  .build();

          debug.log("deactivateGossip: " + responseCode);
          debug.log(
              "deactivateGossip:" +
              " Server Active: " +
              this.studentsClass.getServerStatus().isActive() +
              " | Gossip Active: " +
              this.studentsClass.getServerStatus().isGossipActive()
          );

          responseObserver.onNext(deactivateGossipResponse);
          responseObserver.onCompleted();
      }
    }

    /**
     * Force server to gossip
     * @param request           The request from AdminFrontend
     * @param responseObserver  The stream where response will be sent
     */
    public void forceGossip(ForceGossipRequest request, StreamObserver<ForceGossipResponse> responseObserver) {
        synchronized (studentsClass){

            classServerFrontend.gossip(studentsClass, "admin");
            ResponseCode responseCode = ResponseCode.OK;
            ForceGossipResponse forceGossipResponse = ForceGossipResponse.newBuilder()
                    .setCode(responseCode)
                    .build();

            debug.log("forceGossip: " + responseCode);

            responseObserver.onNext(forceGossipResponse);
            responseObserver.onCompleted();
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
