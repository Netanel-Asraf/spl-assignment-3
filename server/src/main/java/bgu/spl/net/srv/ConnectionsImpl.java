package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> topics = new ConcurrentHashMap<>();

    // 2. NEW: User Database (Username -> Password)
    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();
    
    // 3. NEW: Active Users (Username -> ConnectionId)
    // This tracks who is currently logged in to prevent double logins.
    private final ConcurrentHashMap<String, Integer> activeUsers = new ConcurrentHashMap<>();


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
            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                Integer connectionId = entry.getKey();
                String subscriptionId = entry.getValue();

                if (msg instanceof String) {
                    T personalizedMsg = (T) ("subscription:" + subscriptionId + "\n" + msg); 
                    send(connectionId, personalizedMsg);
                } 
                else {
                    send(connectionId, msg);
                }
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        connectionMap.remove(connectionId);
        for (ConcurrentHashMap<Integer, String> topicMap : topics.values()) {
            topicMap.remove(connectionId);
        }
        activeUsers.entrySet().removeIf(entry -> entry.getValue().equals(connectionId));   
    }
    
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connectionMap.put(connectionId, handler);
    }

    // 4. NEW: Helper for Protocol (The Login Logic)
    // Returns true if login is successful, false if failed (wrong password or already active)
    public boolean connect(int connectionId, String username, String password) {
        // A. Check if user exists
        if (users.containsKey(username)) {
            // User exists, verify password
            if (!users.get(username).equals(password)) {
                return false; // Wrong password
            }
        } else {
            // New user, auto-register [cite: 321]
            users.put(username, password);
        }

        // B. Check if already logged in [cite: 323]
        if (activeUsers.containsKey(username)) {
            return false; // Already active
        }

        // C. Mark as active
        activeUsers.put(username, connectionId);
        return true;
    }

    public void subscribe(String channel, int connectionId, String subscriptionId) {
        // Create the topic map if it doesn't exist yet
        topics.putIfAbsent(channel, new ConcurrentHashMap<>());
        
        // Add the client to the topic
        topics.get(channel).put(connectionId, subscriptionId);
    }
}
