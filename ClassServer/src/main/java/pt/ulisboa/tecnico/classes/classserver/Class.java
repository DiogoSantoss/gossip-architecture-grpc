package pt.ulisboa.tecnico.classes.classserver;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.classes.classserver.exception.ClassException;

public class Class {

    ServerStatus serverStatus;
    private int capacity = 0;
    private boolean openEnrollments = false;
    private ConcurrentHashMap<String, String> enrolled = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> discarded = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Instant> timestamps = new ConcurrentHashMap<>();

    public Class(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    public synchronized ServerStatus getServerStatus() { return serverStatus; }

    public synchronized int getCapacity() {
        return capacity;
    }

    public synchronized void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public synchronized boolean isOpenEnrollments() {
        return openEnrollments;
    }

    public synchronized void setOpenEnrollments(boolean openEnrollments) {
        this.openEnrollments = openEnrollments;
    }

    public synchronized ConcurrentHashMap<String, String> getEnrolled() {
        return enrolled;
    }

    public synchronized void setEnrolled(ConcurrentHashMap<String, String> enrolled) {
        this.enrolled = enrolled;
    }

    public synchronized ConcurrentHashMap<String, String> getDiscarded() {
        return discarded;
    }

    public synchronized void setDiscarded(ConcurrentHashMap<String, String> discarded) {
        this.discarded = discarded;
    }

    public synchronized ConcurrentHashMap<String, Instant> getTimestamps() { return timestamps; }

    public synchronized void setTimestamps(ConcurrentHashMap<String, Instant> timestamps) { this.timestamps =  timestamps; }

    public synchronized Instant getStudentTimestamp(String studentId){
        return timestamps.get(studentId);
    }

    /**
     * Get student name
     * @param studentId The student's id
     * @return          The student's name
     */
    public synchronized String getStudentName(String studentId){
        if(enrolled.containsKey(studentId)){
            return enrolled.get(studentId);
        } else {
            return discarded.get(studentId);
        }
    }

    /**
     * Check if is valid student id
     * @param studentId The student id
     * @return          The validity of the id
     */
    public boolean isValidStudentId(String studentId){

        String[] studentIdSplit = studentId.split("aluno");

        if(!studentId.startsWith("aluno") || studentIdSplit.length != 2 || studentIdSplit[1].length() != 4) {
            return false;
        }
        try{
            Integer.parseInt(studentIdSplit[1]);
        } catch (NumberFormatException e){
            return false;
        }

        return true;
    }

    /**
     * Check if is valid student name
     * @param studentName   The student name
     * @return              The validity of the id
     */
    public boolean isValidStudentName(String studentName){

        return studentName.length() >= 3 && studentName.length() <= 30;
    }

    /**
     * Throws an error if server is inactive
     */
    public void checkActiveServer() throws ClassException {
        if(!serverStatus.isActive()){
            throw new ClassException("Server is inactive");
        }
    }

    /**
     * Open enrollments with certain capacity
     * @param capacity  The class capacity
     */
    public void openEnrollments(Integer capacity) throws ClassException {

        if(!serverStatus.isActive()){
            throw new ClassException("Server is inactive");
        }
        else if(!serverStatus.getQualifiers().contains("P")) {
            throw new ClassException("Writing not supported");
        }
        else if(isOpenEnrollments()){
            throw new ClassException("Enrollments already open");
        }
        else if(capacity < getEnrolled().size()){
            throw new ClassException("Class is full");
        }

        setOpenEnrollments(true);
        setCapacity(capacity);

        serverStatus.setChanged(true);
        serverStatus.getVectorClock().increment(serverStatus.getServerId());
    }

    /**
     * Close enrollments
     */
    public void closeEnrollments() throws ClassException {

        if(!serverStatus.isActive()){
            throw new ClassException("Server is inactive");
        }
        else if(!serverStatus.getQualifiers().contains("P")) {
            throw new ClassException("Writing not supported");
        }
        else if(!isOpenEnrollments()){
            throw new ClassException("Enrollments already closed");
        }

        setOpenEnrollments(false);

        serverStatus.setChanged(true);
        serverStatus.getVectorClock().increment(serverStatus.getServerId());
    }

    /**
     * Cancel student enrollment
     * @param studentId The student's id
     */
    public void cancelEnrollment(String studentId) throws ClassException {

        if(!serverStatus.isActive()){
            throw new ClassException("Server is inactive");
        }
        else if(!getEnrolled().containsKey(studentId) && !getDiscarded().containsKey(studentId)){
            throw new ClassException("Student not enrolled");
        }
        else if(!isValidStudentId(studentId)){
            throw new ClassException("Invalid student id");
        }

        if(getEnrolled().containsKey(studentId)) {
            String studentName = getEnrolled().get(studentId);
            getEnrolled().remove(studentId);
            getDiscarded().put(studentId, studentName);
            timestamps.put(studentId, Instant.now());
            serverStatus.setChanged(true);
            serverStatus.getVectorClock().increment(serverStatus.getServerId());
        }
    }

    /**
     * Enroll student
     * @param studentId   The student id
     * @param studentName The student name
     */
    public void enroll(String studentId, String studentName) throws ClassException {

        if(!serverStatus.isActive()){
            throw new ClassException("Server is inactive");
        }
        else if(!isOpenEnrollments()){
            throw new ClassException("Enrollments are closed");
        }
        else if(getEnrolled().containsKey(studentId)){
            throw new ClassException("Student already enrolled");
        }
        else if(getEnrolled().size() == getCapacity()){
            throw new ClassException("Class is full");
        }
        else if(!isValidStudentId(studentId)){
            throw new ClassException("Invalid student id");
        }
        else if(!isValidStudentName(studentName)){
            throw new ClassException("Invalid student name");
        }

        getDiscarded().remove(studentId);
        getEnrolled().put(studentId, studentName);
        timestamps.put(studentId, Instant.now());

        serverStatus.setChanged(true);
        serverStatus.getVectorClock().increment(serverStatus.getServerId());
    }

    /**
     * Set server state to ACTIVE
     * If gossip was deactivated by the command 'deactivateGossip' then
     * this method will NOT activate gossip.
     */
    public void activate(){

        serverStatus.setActive(true);
        if(!serverStatus.isDeactivateGossip()){
            serverStatus.setGossipActive(true);
            serverStatus.setDeactivateGossip(true);
        }
    }

    /**
     * Set server state to INACTIVE
     */
    public void deactivate(){

        serverStatus.setActive(false);
        if(serverStatus.isGossipActive()){
            serverStatus.setGossipActive(false);
            serverStatus.setDeactivateGossip(false);
        }
    }

    /**
     * Set server gossip to ACTIVE
     */
    public void activateGossip() {
        serverStatus.setGossipActive(true);
        serverStatus.setDeactivateGossip(false);
    }

    /**
     * Set server gossip to INACTIVE
     */
    public void deactivateGossip() {
        serverStatus.setGossipActive(false);
        serverStatus.setDeactivateGossip(true);
    }

    /**
     * Update class
     * @param capacity          The updated capacity
     * @param openEnrollments   The updated open enrollments
     * @param enrolled          The updated enrolled students
     * @param discarded         The updated discarded students
     */
    public void update(
            Integer capacity,
            boolean openEnrollments,
            ConcurrentHashMap<String, String> enrolled,
            ConcurrentHashMap<String, String> discarded,
            ConcurrentHashMap<String, Instant> timestamps
    ) throws ClassException {

        if(!serverStatus.isActive()){
            throw new ClassException("Server is inactive");
        }

        setCapacity(capacity);
        setOpenEnrollments(openEnrollments);
        setEnrolled(enrolled);
        setDiscarded(discarded);
        setTimestamps(timestamps);
    }
}
