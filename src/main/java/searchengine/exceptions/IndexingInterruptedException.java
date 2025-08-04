package searchengine.exceptions;

public class IndexingInterruptedException extends RuntimeException {
    public IndexingInterruptedException(String message) {
        super(message);
    }
}
