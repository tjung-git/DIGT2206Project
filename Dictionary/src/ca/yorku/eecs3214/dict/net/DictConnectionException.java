package ca.yorku.eecs3214.dict.net;

public class DictConnectionException extends Exception {

    public DictConnectionException() {
    }

    public DictConnectionException(Throwable cause) {
        super(cause);
    }

    public DictConnectionException(String message) {
        super(message);
    }

    public DictConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
