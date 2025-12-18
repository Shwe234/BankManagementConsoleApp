package exceptions;

public class insufficientFundsException extends  RuntimeException{
    public insufficientFundsException(String message) {
        super(message);
    }
}
