package pt.ulisboa.tecnico.classes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VectorClock {

    private Map<Integer, Integer> vectorClock;

    public VectorClock(){
        this.vectorClock = new ConcurrentHashMap<>();
    }

    public VectorClock(Map<Integer, Integer> v){
        this.vectorClock = v;
    }

    public Map<Integer, Integer> getVectorClock() {
        return vectorClock;
    }

    public void setVectorClock(Map<Integer, Integer> vectorClock) {
        this.vectorClock = vectorClock;
    }

    public int getValue(int key) { return this.vectorClock.getOrDefault(key,0); }

    public boolean containsServerId(int serverId) { return this.vectorClock.containsKey(serverId); }

    public void addServerId(int serverId) { this.vectorClock.put(serverId, 0); }

    public VectorClock copyVectorClock(VectorClock v){

        VectorClock newVectorClock = new VectorClock();
        v.getVectorClock().forEach(newVectorClock::addEntry);

        return newVectorClock;
    }

    public void addEntry(int serverId, int value){
        this.vectorClock.put(serverId,value);
    }

    /**
     * Increment the clock entry of the serverId
     * @param serverId The server id
     */
    public void increment(int serverId){
        if(!this.vectorClock.containsKey(serverId)){
            this.vectorClock.put(serverId,1);
        } else {
            int previousValue = this.vectorClock.get(serverId);
            this.vectorClock.put(serverId, previousValue+1);
        }
    }

    /**
     * Merge this vector clock with the given vector clock
     * @param v The other vector clock to merge
     */
    public void merge(VectorClock v){
        this.getVectorClock().forEach((serverId,counter) -> {
            this.mergeEntry(v, serverId);
        });
        v.getVectorClock().forEach((serverId,counter) -> {
            this.mergeEntry(v, serverId);
        });
    }

    /**
     * Merge this vector clock with the given vector clock for the given server id
     * @param v         The other clock to merge
     * @param serverId  The server id
     */
    public void mergeEntry(VectorClock v, int serverId){
        int maxValue = Math.max(this.getValue(serverId), v.getValue(serverId));
        this.vectorClock.put(serverId, maxValue);
    }

    /**
     * Compare this vector clock with the given vector clock
     * @param v         The other clock to compare with
     * @return boolean  True if the given clock is greater than this clock
     */
    public boolean happensBefore(VectorClock v) {


        if( v.getVectorClock().size() == 0 ){
            return false;
        }

        boolean notEqual = false;

        for(int serverId : v.getVectorClock().keySet()){
            if(v.getValue(serverId) < this.getValue(serverId)){
                return false;
            } else if (v.getValue(serverId) > this.getValue(serverId)) {
                notEqual = true;
            }
        }

        for(int serverId : this.getVectorClock().keySet()){
            if(v.getValue(serverId) < this.getValue(serverId)){
                return false;
            } else if (v.getValue(serverId) > this.getValue(serverId)) {
                notEqual = true;
            }
        }

        return notEqual;
    }

    /**
     * Test if vectors are concurrent
     * @param v The other vector clock
     * @return boolean True if the given vector clock is concurrent with this vector clock
     */
    public boolean concurrent(VectorClock v){

        int before = 0;
        int after = 0;

        Set<Integer> keys = new HashSet<>();
        keys.addAll(this.vectorClock.keySet());
        keys.addAll(v.getVectorClock().keySet());

        for (Integer key : keys) {

            if (this.getValue(key) < v.getValue(key)) {
                before ++;
            } else if (this.getValue(key) > v.getValue(key)) {
                after ++;
            }
        }

        return before != 0 && after != 0;
    }

    @Override
    public boolean equals(Object o){
        if(!(o instanceof VectorClock vctClock)){
            return false;
        }

        if(vctClock.getVectorClock().size() != this.vectorClock.size()){
            return false;
        }

        return vctClock.getVectorClock().entrySet().stream()
                .allMatch(v -> v.getValue().equals(this.vectorClock.get(v.getKey())));
    }

    @Override
    public String toString(){
        return this.vectorClock.entrySet().stream().
                map(entrySet -> entrySet.getKey() + ":"+ entrySet.getValue()).
                collect(Collectors.joining(","));
    }
}
