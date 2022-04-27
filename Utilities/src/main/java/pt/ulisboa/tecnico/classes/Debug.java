package pt.ulisboa.tecnico.classes;

import java.util.logging.Logger;

public class Debug {

    private final Logger logger;
    private final boolean active;

    public Debug(String className, boolean active){
        this.logger = Logger.getLogger(className);
        this.active = active;
    }

    public void log(String message){
        if(active)
            logger.info(message);
    }
}
