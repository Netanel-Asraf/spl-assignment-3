package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;

public class ConnectionsImpl<T> implements Connections<T> {
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> topics = new ConcurrentHashMap<>();
    private int messageIdCounter = 0;

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connectionMap.get(connectionId);
        if(handler != null){
            handler.send(msg);
            return true;
        }   
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void send(String channel, T msg) {
        ConcurrentHashMap<Integer, String> subscribers = topics.get(channel);
        if (subscribers != null) {
            String msgId = Integer.toString(messageIdCounter++);

            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                Integer connectionId = entry.getKey();
                String subscriptionId = entry.getValue();

                String frame = "MESSAGE\n" +
                               "subscription:" + subscriptionId + "\n" +
                               "message-id:" + msgId + "\n" +
                               "destination:" + channel + "\n" +
                               "\n" +
                               msg; 

                send(connectionId, (T) frame);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        Database.getInstance().logout(connectionId);

        connectionMap.remove(connectionId);
        for (ConcurrentHashMap<Integer, String> topicMap : topics.values()) {
            topicMap.remove(connectionId);
        }
    }
    
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connectionMap.put(connectionId, handler);
    }

    public boolean isSubscribed(String channel, int connectionId) {
        return topics.containsKey(channel) && topics.get(channel).containsKey(connectionId);
    }

    public boolean connect(int connectionId, String username, String password) {
        LoginStatus status = Database.getInstance().login(connectionId, username, password);
        
        return status == LoginStatus.LOGGED_IN_SUCCESSFULLY || 
               status == LoginStatus.ADDED_NEW_USER;
    }

    public void subscribe(String channel, int connectionId, String subscriptionId) {
        topics.putIfAbsent(channel, new ConcurrentHashMap<>());
        
        topics.get(channel).put(connectionId, subscriptionId);
    }

    public void unsubscribe(String subscriptionId, int connectionId) {
        for (Map.Entry<String, ConcurrentHashMap<Integer, String>> topicEntry : topics.entrySet()) {
            ConcurrentHashMap<Integer, String> subscribers = topicEntry.getValue();
            
            if (subscribers.containsKey(connectionId) && subscribers.get(connectionId).equals(subscriptionId)) {
                subscribers.remove(connectionId);
            }
        }
    }
}
