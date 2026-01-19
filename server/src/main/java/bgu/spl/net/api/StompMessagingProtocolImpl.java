package bgu.spl.net.api;

import bgu.spl.net.impl.stomp.Frame;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;


public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private ConnectionsImpl<String> connections;
    private boolean shouldTerminate = false;

    private String currentUser; //added this
    
    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }

    @Override
    public void process(String message) {
        // 1. Parse the message using our Frame helper
        Frame frame = Frame.parse(message);
        
        // 2. Switch based on the Command
        switch (frame.getCommand()) {
            case "CONNECT":
                handleConnect(frame); // <--- Now we actually use your login logic!
                break;
            case "SUBSCRIBE":
                handleSubscribe(frame); // <--- Now we handle subscriptions!
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(frame);
                break;
            case "SEND":
                handleSend(frame);
                break;
            case "DISCONNECT":
                handleDisconnect(frame);
                break;
            default:
                // Even if unknown, we might want to log it or send error
                sendError(frame, "Unknown Command");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void handleSubscribe(Frame frame) {
        String topic = frame.getHeaders().get("destination");
        String subId = frame.getHeaders().get("id");
        String receipt = frame.getHeaders().get("receipt"); // Get receipt if it exists

        // 1. Validate
        if (topic == null || subId == null) {
            sendError(frame, "Malformed SUBSCRIBE frame: missing destination or id");
            return;
        }

        // 2. Subscribe using the phonebook
        connections.subscribe(topic, connectionId, subId);

        // 3. Send Receipt (Only if client asked for it!)
        if (receipt != null) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void handleSend(Frame frame) {
        // String topic = frame.getHeaders().get("destination");
        // String body = frame.getBody();

        // // 1. Validate
        // if (topic == null) {
        //     sendError(frame, "Malformed SEND frame: missing destination");
        //     return;
        // }

        // // 2. Permission Check
        // // "if a client is not subscribed to a topic it is not allowed to send messages to it" 
        // if (!connections.isSubscribed(topic, connectionId)) {
        //     sendError(frame, "Permission denied: You are not subscribed to this topic");
        //     return;
        // }

        // // 3. Broadcast
        // // We pass the 'body' string. ConnectionsImpl will wrap it in a MESSAGE frame.
        // connections.send(topic, body);
        
        // // 4. Receipt (Optional)
        // if (frame.getHeaders().containsKey("receipt")) {
        //     Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
        //     receiptFrame.getHeaders().put("receipt-id", frame.getHeaders().get("receipt"));
        //     connections.send(connectionId, receiptFrame.toString());
        // }
        String topic = frame.getHeaders().get("destination");
        String body = frame.getBody();

        // 1. Validate
        if (topic == null) {
            sendError(frame, "Malformed SEND frame: missing destination");
            return;
        }

        // 2. Permission Check
        if (!connections.isSubscribed(topic, connectionId)) {
            sendError(frame, "Permission denied: You are not subscribed to this topic");
            return;
        }

        // 3. Database: Track File Upload (Requirement 3.3)
        // We look for a custom header "filename" that the Client must send
        String filename = frame.getHeaders().get("filename");
        if (filename != null && currentUser != null) {
            // This calls the Database function you wrote earlier
            // It logs: username, filename, timestamp, and game_channel
            bgu.spl.net.impl.data.Database.getInstance().trackFileUpload(currentUser, filename, topic);
        }

        // 4. Broadcast
        connections.send(topic, body);

        // 5. Receipt (Optional but good practice)
        if (frame.getHeaders().containsKey("receipt")) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", frame.getHeaders().get("receipt"));
            connections.send(connectionId, receiptFrame.toString());
        }       
    }

    private void handleDisconnect(Frame frame) {
        String receipt = frame.getHeaders().get("receipt");

        // 1. Send Receipt (Crucial for graceful shutdown!)
        if (receipt != null) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }

        connections.disconnect(connectionId);
        // 2. Mark for termination
        // The BaseServer loop checks this boolean. If true, it exits the loop and closes the socket.
        shouldTerminate = true; 
        
        // Note: The actual socket close and cleanup happens in BaseServer 
        // because 'shouldTerminate()' returns true.
    }

    private void handleUnsubscribe(Frame frame) {
        String subId = frame.getHeaders().get("id");
        String receipt = frame.getHeaders().get("receipt");

        // 1. Validate
        if (subId == null) {
            sendError(frame, "Malformed UNSUBSCRIBE frame: missing id header");
            return;
        }

        // 2. Unsubscribe
        connections.unsubscribe(subId, connectionId);

        // 3. Receipt
        if (receipt != null) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void handleConnect(Frame frame) {
        // String login = frame.getHeaders().get("login");
        // String passcode = frame.getHeaders().get("passcode");

        // // 1. Validate Headers
        // if (login == null || passcode == null) {
        //     sendError(frame, "Malfromed Frame: Missing login or passcode header");
        //     shouldTerminate = true; // Close connection
        //     return;
        // }

        // // 2. Try to Login
        // // We use our new 'connect' method in ConnectionsImpl
        // boolean success = connections.connect(connectionId, login, passcode);

        // if (success) {
        //     // Login Success!
        //     connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
        // } else {
        //     // Login Failed (Wrong password or already logged in)
        //     sendError(frame, "Login failed: User already logged in or wrong password");
        //     shouldTerminate = true; // Close connection after error [cite: 114]
        // }

        String login = frame.getHeaders().get("login");
        String passcode = frame.getHeaders().get("passcode");

        if (login == null || passcode == null) {
            sendError(frame, "Malformed Frame: Missing login or passcode");
            shouldTerminate = true;
            return;
        }

        boolean success = connections.connect(connectionId, login, passcode);

        if (success) {
            this.currentUser = login; // <--- SAVE THE USERNAME
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
        } else {
            sendError(frame, "Login failed: User already logged in or wrong password");
            shouldTerminate = true;
        }
    }

    // Helper to send ERROR frames cleanly [cite: 113-117]
    private void sendError(Frame frame, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR\n");
        
        // If the client sent a receipt-id, we should include it
        if (frame != null && frame.getHeaders().containsKey("receipt")) {
            sb.append("receipt-id:").append(frame.getHeaders().get("receipt")).append("\n");
        }
        
        sb.append("message:").append(message).append("\n");
        sb.append("\n"); // Empty line
        sb.append("The message:\n-----\n");
        if (frame != null) {
            sb.append(frame.toString());
        }
        sb.append("\n-----\n");
        
        connections.send(connectionId, sb.toString());
    }
    
    
}
