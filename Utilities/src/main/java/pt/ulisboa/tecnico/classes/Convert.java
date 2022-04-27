package pt.ulisboa.tecnico.classes;


import pt.ulisboa.tecnico.classes.contract.ClassesDefinitions.*;

import java.time.Instant;

public class Convert {

    /**
     * Convert VectorClockState to VectorClock
     * @param vectorClockState  The VectorClockState to be converted
     * @return                  The converted VectorClock
     */
    public static VectorClock toVectorClock(VectorClockState vectorClockState){

        return new VectorClock(vectorClockState.getVectorClockMap());
    }

    /**
     * Convert VectorClock to VectorClockState
     * @param vectorClock   The vector clock
     * @return              The vector clock state
     */
    public static VectorClockState toVectorClockState(VectorClock vectorClock) {

        VectorClockState.Builder builder = VectorClockState.newBuilder();
        vectorClock.getVectorClock().forEach(builder::putVectorClock);
        return builder.build();
    }

    /**
     * Convert Instant to google.protobuf.Timestamp
     * @param now     The instant
     * @return        The vector clock
     */
    public static com.google.protobuf.Timestamp toGoogleTimestamp(Instant now){
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

    public static Instant toInstant(com.google.protobuf.Timestamp now){
        return Instant.ofEpochSecond(now.getSeconds() , now.getNanos());
    }
}
