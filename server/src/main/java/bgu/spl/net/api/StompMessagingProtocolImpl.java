package bgu.spl.net.api;

import bgu.spl.net.impl.stomp.Frame;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;


public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private ConnectionsImpl<String> connections;
    private boolean shouldTerminate = false;

    private String currentUser; 
    
    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }

    @Override
    public void process(String message) {
        Frame frame = Frame.parse(message);
        
        switch (frame.getCommand()) {
            case "CONNECT":
                handleConnect(frame); 
                break;
            case "SUBSCRIBE":
                handleSubscribe(frame); 
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
        String receipt = frame.getHeaders().get("receipt");

        if (topic == null || subId == null) {
            sendError(frame, "Malformed SUBSCRIBE frame: missing destination or id");
            connections.disconnect(connectionId); 
            shouldTerminate = true;           
            return;
        }

        connections.subscribe(topic, connectionId, subId);

        if (receipt != null) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void handleSend(Frame frame) {
        String topic = frame.getHeaders().get("destination");
        String body = frame.getBody();

        if (topic == null) {
            sendError(frame, "Malformed SEND frame: missing destination");
            connections.disconnect(connectionId); 
            shouldTerminate = true;               
            return;
        }

        if (!connections.isSubscribed(topic, connectionId)) {
            sendError(frame, "Permission denied: You are not subscribed to this topic");
            connections.disconnect(connectionId); 
            shouldTerminate = true;           
            return;
        }

        String filename = frame.getHeaders().get("filename");
        if (filename != null && currentUser != null) {
            bgu.spl.net.impl.data.Database.getInstance().trackFileUpload(currentUser, filename, topic);
        }

        connections.send(topic, body);

        if (frame.getHeaders().containsKey("receipt")) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", frame.getHeaders().get("receipt"));
            connections.send(connectionId, receiptFrame.toString());
        }       
    }

    private void handleDisconnect(Frame frame) {
        String receipt = frame.getHeaders().get("receipt");

        if (receipt != null) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }

        connections.disconnect(connectionId);
        shouldTerminate = true; 
    }

    private void handleUnsubscribe(Frame frame) {
        String subId = frame.getHeaders().get("id");
        String receipt = frame.getHeaders().get("receipt");

        if (subId == null) {
            sendError(frame, "Malformed UNSUBSCRIBE frame: missing id header");
            connections.disconnect(connectionId); 
            shouldTerminate = true;             
            return;
        }

        connections.unsubscribe(subId, connectionId);

        if (receipt != null) {
            Frame receiptFrame = new Frame("RECEIPT", new java.util.HashMap<>(), null);
            receiptFrame.getHeaders().put("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void handleConnect(Frame frame) {
        String login = frame.getHeaders().get("login");
        String passcode = frame.getHeaders().get("passcode");

        if (login == null || passcode == null) {
            sendError(frame, "Malformed Frame: Missing login or passcode");
            shouldTerminate = true;
            return;
        }

        boolean success = connections.connect(connectionId, login, passcode);

        if (success) {
            this.currentUser = login; 
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
        } else {
            sendError(frame, "Login failed: User already logged in or wrong password");
            shouldTerminate = true;
        }
    }

    private void sendError(Frame frame, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR\n");
        
        if (frame != null && frame.getHeaders().containsKey("receipt")) {
            sb.append("receipt-id:").append(frame.getHeaders().get("receipt")).append("\n");
        }
        
        sb.append("message:").append(message).append("\n");
        sb.append("\n");
        sb.append("The message:\n-----\n");
        if (frame != null) {
            sb.append(frame.toString());
        }
        sb.append("\n-----\n");
        
        connections.send(connectionId, sb.toString());
    }
    
    
}
