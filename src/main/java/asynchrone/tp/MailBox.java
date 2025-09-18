package asynchrone.tp;
import java.util.*;

public class MailBox {
    private List<Message> messages;

    public MailBox() {
        this.messages = new ArrayList<>();
    }

    public synchronized void add(Message message) {
        messages.add(message);
        notifyAll(); // Notify any waiting threads that a new message has arrived
    }

    public synchronized Message getMsg() throws InterruptedException {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.remove(0); // Retrieve and remove the first message
    }

    public synchronized boolean isEmpty() {
        return messages.isEmpty();
    }
}
