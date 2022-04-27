package pt.ulisboa.tecnico.classes.classserver;

import io.grpc.stub.StreamObserver;

import pt.ulisboa.tecnico.classes.Convert;
import pt.ulisboa.tecnico.classes.VectorClock;
import pt.ulisboa.tecnico.classes.classserver.exception.ClassException;
import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;
import pt.ulisboa.tecnico.classes.contract.classserver.ClassServerClassServer.*;
import pt.ulisboa.tecnico.classes.contract.classserver.ClassServerServiceGrpc.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.classes.Debug;

public class ClassServerServiceImpl extends ClassServerServiceImplBase {

    private final Debug debug;
    private final Class studentsClass;


    public ClassServerServiceImpl(Class studentsClass, boolean debugMode) {
        this.studentsClass = studentsClass;
        debug = new Debug(ClassServerServiceImpl.class.getName(), debugMode);
    }

    /**
     * Propagate state to another server
     * @param request           The request from ClassServerFrontend
     * @param responseObserver  The stream where response will be sent
     */
    @Override
    public void gossip(GossipRequest request, StreamObserver<GossipResponse> responseObserver){

        synchronized (studentsClass) {

            ResponseCode responseCode = ResponseCode.OK;

            try{
                // Don't process gossip if server is inactive
                this.studentsClass.checkActiveServer();

                // Information about gossip sender
                String remoteQualifier = request.getServerQualifier();
                Class remoteStudentsClass = convertToClass(request.getClassState());
                VectorClock remoteVectorClock = new VectorClock(request.getVectorClockState().getVectorClockMap());

                // Detect concurrent events
                if(this.studentsClass.getServerStatus().getVectorClock().concurrent(remoteVectorClock)){

                    debug.log("gossip: Concurrent events detected, resolving conflicts between local and remote.");

                    // To merge OpenEnrollments and Capacity, always prioritize the primary server or the
                    // server that more up-to-date information about the primary
                    if(this.studentsClass.isOpenEnrollments() != remoteStudentsClass.isOpenEnrollments()
                            || this.studentsClass.getCapacity() != remoteStudentsClass.getCapacity()) {

                        // If remote is primary, update local
                        if (remoteQualifier.equals("P")) {

                            this.studentsClass.setOpenEnrollments(remoteStudentsClass.isOpenEnrollments());
                            this.studentsClass.setCapacity(remoteStudentsClass.getCapacity());

                        // If local is primary, don't do anything
                        } else if (this.studentsClass.getServerStatus().getQualifiers().get(0).equals("P")) {

                        // If neither is primary,
                        // check if remote has more up-to-date information regarding primary server
                        } else if (isRemoteMoreUpToDate(remoteVectorClock, request.getServerId())){

                            this.studentsClass.setOpenEnrollments(remoteStudentsClass.isOpenEnrollments());
                            this.studentsClass.setCapacity(remoteStudentsClass.getCapacity());
                        }
                    }

                    // Merge students lists

                    // Create a list of students containing all students from both remote and local
                    // If student is present in both then add which ever has the more recent timestamp
                    ConcurrentHashMap<String, Instant> allStudents = new ConcurrentHashMap<>(this.studentsClass.getTimestamps());
                    remoteStudentsClass.getTimestamps().forEach((studentId, timestamp) -> {
                        if(!allStudents.containsKey(studentId) || allStudents.get(studentId).compareTo(timestamp) < 0){
                            allStudents.put(studentId,timestamp);
                        }
                    });

                    // Sort all students by timestamp
                    HashMap<String, Instant> allEnrolledSorted = sortByValue(allStudents);

                    ConcurrentHashMap<String, String> newEnrolled = new ConcurrentHashMap<>();
                    ConcurrentHashMap<String, String> newDiscarded = new ConcurrentHashMap<>();

                    // Iterate over sorted students and add students to new enrolled and discarded lists
                    int capacity = this.studentsClass.getCapacity();
                    for(Map.Entry<String, Instant> entry : allEnrolledSorted.entrySet()){

                        String studentId = entry.getKey();
                        String studentName;

                        // Find student name
                        if(this.studentsClass.getTimestamps().containsKey(studentId)){
                            studentName = this.studentsClass.getStudentName(studentId);
                        }
                        else{
                            studentName = remoteStudentsClass.getStudentName(studentId);
                        }


                        // If class is not full
                        if(newEnrolled.size() < capacity){


                            Instant localTimestamp = this.studentsClass.getStudentTimestamp(studentId);
                            Instant remoteTimestamp = remoteStudentsClass.getStudentTimestamp(studentId);

                            // If student is discarded in remote, verify which is more up to date
                            if(remoteStudentsClass.getDiscarded().containsKey(studentId)){

                                remoteStudentsClass.getDiscarded().remove(studentId);

                                if(localTimestamp.compareTo(remoteTimestamp) > 0){
                                    newEnrolled.put(studentId, studentName);
                                } else {
                                    newDiscarded.put(studentId, studentName);
                                }

                            // If student is discarded in local, verify which is more up to date
                            } else if(this.studentsClass.getDiscarded().containsKey(studentId)){

                                this.studentsClass.getDiscarded().remove(studentId);

                                if(localTimestamp.compareTo(remoteTimestamp) > 0){
                                    newDiscarded.put(studentId, studentName);
                                } else {
                                    newEnrolled.put(studentId, studentName);
                                }
                            // If student is not discarded anywhere, add to enrolled
                            } else {
                                newEnrolled.put(studentId, studentName);
                            }

                        // If class is full
                        } else {
                            newDiscarded.put(studentId, studentName);
                        }
                    }

                    // Add remaining students from original discarded to new discarded
                    // Note that local and remote discarded lists were updated during the
                    // iteration above, removing any students that were in conflict
                    // (i.e. present in both enroll and discard lists)
                    // Therefore the remaining students are the ones that are certain to
                    // not be enrolled
                    newDiscarded.putAll(this.studentsClass.getDiscarded());
                    newDiscarded.putAll(remoteStudentsClass.getDiscarded());

                    // Update new enrolled and corresponding timestamps
                    this.studentsClass.setEnrolled(newEnrolled);
                    newEnrolled.forEach((studentId, studentName) -> {
                        this.studentsClass.getTimestamps().put(studentId, allStudents.get(studentId));
                    });
                    // Update new discarded and corresponding timestamps
                    this.studentsClass.setDiscarded(newDiscarded);
                    newDiscarded.forEach((studentId, studentName) -> {
                        this.studentsClass.getTimestamps().put(studentId, allStudents.get(studentId));
                    });

                    // Update clock
                    this.studentsClass.getServerStatus().getVectorClock().merge(remoteVectorClock);
                    int localServerId = this.studentsClass.getServerStatus().getServerId();
                    this.studentsClass.getServerStatus().getVectorClock().increment(localServerId);

                    debug.log("gossip: Conflict resolved, local clock updated to: " + this.studentsClass.getServerStatus().getVectorClock());

                // Detect sequential events
                // If local behind remote then simply update local class state to remote class state
                } else if(this.studentsClass.getServerStatus().getVectorClock().happensBefore(remoteVectorClock)){

                    debug.log("gossip: Remote sender is ahead of local, updating local data.");

                    studentsClass.update(
                        remoteStudentsClass.getCapacity(),
                        remoteStudentsClass.isOpenEnrollments(),
                        remoteStudentsClass.getEnrolled(),
                        remoteStudentsClass.getDiscarded(),
                        remoteStudentsClass.getTimestamps()
                    );

                    // Update local vector clock to be the same as remote clock
                    this.studentsClass.getServerStatus().setVectorClock(remoteVectorClock);

                    debug.log("gossip: Update finished, local clock updated to: " + this.studentsClass.getServerStatus().getVectorClock());

                } else{
                    debug.log("gossip: Local is ahead of remote sender, no update necessary.");
                }

            } catch (ClassException e){

                switch (e.getMessage()){
                    case "Server is inactive" ->
                            responseCode = ResponseCode.INACTIVE_SERVER;
                }

            } finally {

                GossipResponse.Builder gossipResponse = GossipResponse.newBuilder()
                        .setCode(responseCode)
                        .setClassState(convertToClassState(this.studentsClass));

                if(responseCode == ResponseCode.OK){
                    gossipResponse.setVectorClockState(Convert.toVectorClockState(this.studentsClass.getServerStatus().getVectorClock()));
                } else {
                    gossipResponse.setVectorClockState(request.getVectorClockState());
                }

                debug.log("gossip: " + responseCode);

                responseObserver.onNext(gossipResponse.build());
                responseObserver.onCompleted();
            }
        }
    }

    /**
     * Sort HashMap by value
     * @param map HashMap to sort
     * @return Sorted HashMap
     */
    public static HashMap<String, Instant> sortByValue(ConcurrentHashMap<String, Instant> map) {

        // Create a list from elements of HashMap
        List<Map.Entry<String, Instant>> list =
                new LinkedList<>(map.entrySet());

        // Sort the list in descending order
        list.sort(Map.Entry.comparingByValue());

        // put data from sorted list to hashmap
        HashMap<String, Instant> temp = new LinkedHashMap<>();
        for (Map.Entry<String, Instant> entry : list) {
            temp.put(entry.getKey(), entry.getValue());
        }
        return temp;
    }

    /**
     * Checks if the remote server is more up to date than the local server
     * This is done by checking which of them has the biggest value
     * in the primary server id entry (in their vector clock)
     * @param remoteVectorClock VectorClock of remote server
     * @param remoteServerId Id of remote server
     * @return true if remote server is more up to date
     */
    public boolean isRemoteMoreUpToDate(VectorClock remoteVectorClock, int remoteServerId){

        VectorClock localVectorClock = this.studentsClass.getServerStatus().getVectorClock();
        Set<Integer> vectorKeys = this.studentsClass.getServerStatus().getVectorClock().getVectorClock().keySet();

        int primaryKey = 0;
        int localKey = this.studentsClass.getServerStatus().getServerId();

        for(Integer key: vectorKeys){
            if(key != localKey && key != remoteServerId){
                primaryKey = key;

                if(!localVectorClock.getVectorClock().containsKey(primaryKey)){
                    return true;
                } else if(!remoteVectorClock.getVectorClock().containsKey(primaryKey)){
                    return false;
                }
                return localVectorClock.getValue(primaryKey) <= remoteVectorClock.getValue(primaryKey);
            }
        }
        return true;
    }

    
    /**
     * Convert ClassState to Class
     * @param classState ClassState to convert
     * @return Class
     */
    public Class convertToClass(ClassState classState){

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
}
