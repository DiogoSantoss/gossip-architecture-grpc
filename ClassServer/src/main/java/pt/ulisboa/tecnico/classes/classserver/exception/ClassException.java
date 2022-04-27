package pt.ulisboa.tecnico.classes.classserver.exception;

public class ClassException extends Exception{

    private final String message;

    public ClassException(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
