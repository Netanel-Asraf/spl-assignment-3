package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        if(message.startsWith("CONNECT")){
            
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n\u0000");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    
}
