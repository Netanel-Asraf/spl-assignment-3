package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;

public class ConnectionsImpl<T> implements Connections<T> {
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> topics = new ConcurrentHashMap<>();

    // // 2. NEW: User Database (Username -> Password)
    // private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();
    
    // // 3. NEW: Active Users (Username -> ConnectionId)
    // // This tracks who is currently logged in to prevent double logins.
    // private final ConcurrentHashMap<String, Integer> activeUsers = new ConcurrentHashMap<>();

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
            // Generate a unique message ID for this broadcast
            String msgId = Integer.toString(messageIdCounter++);

            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                Integer connectionId = entry.getKey();
                String subscriptionId = entry.getValue();

                // Construct the valid STOMP frame
                // Note: 'msg' here is treated as the Body of the message
                String frame = "MESSAGE\n" +
                               "subscription:" + subscriptionId + "\n" +
                               "message-id:" + msgId + "\n" +
                               "destination:" + channel + "\n" +
                               "\n" +
                               msg; // The actual body content

                send(connectionId, (T) frame);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // connectionMap.remove(connectionId);
        // for (ConcurrentHashMap<Integer, String> topicMap : topics.values()) {
        //     topicMap.remove(connectionId);
        // }
        // activeUsers.entrySet().removeIf(entry -> entry.getValue().equals(connectionId));   

        // 1. Notify Database to log the logout time
        Database.getInstance().logout(connectionId);

        // 2. Perform standard cleanup
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

    // 4. NEW: Helper for Protocol (The Login Logic)
    // Returns true if login is successful, false if failed (wrong password or already active)
    public boolean connect(int connectionId, String username, String password) {
        // // A. Check if user exists
        // if (users.containsKey(username)) {
        //     // User exists, verify password
        //     if (!users.get(username).equals(password)) {
        //         return false; // Wrong password
        //     }
        // } else {
        //     // New user, auto-register [cite: 321]
        //     users.put(username, password);
        // }

        // // B. Check if already logged in [cite: 323]
        // if (activeUsers.containsKey(username)) {
        //     return false; // Already active
        // }

        // // C. Mark as active
        // activeUsers.put(username, connectionId);
        // return true;

        // Delegate login to the Database (which talks to Python -> SQL)
        LoginStatus status = Database.getInstance().login(connectionId, username, password);
        
        // Return true only if login was successful
        return status == LoginStatus.LOGGED_IN_SUCCESSFULLY || 
               status == LoginStatus.ADDED_NEW_USER;
    }

    public void subscribe(String channel, int connectionId, String subscriptionId) {
        // Create the topic map if it doesn't exist yet
        topics.putIfAbsent(channel, new ConcurrentHashMap<>());
        
        // Add the client to the topic
        topics.get(channel).put(connectionId, subscriptionId);
    }

    public void unsubscribe(String subscriptionId, int connectionId) {
        // Iterate over all topics to find where this user subscribed with this ID
        for (Map.Entry<String, ConcurrentHashMap<Integer, String>> topicEntry : topics.entrySet()) {
            ConcurrentHashMap<Integer, String> subscribers = topicEntry.getValue();
            
            // Check if this connection has this subscription ID in this topic
            if (subscribers.containsKey(connectionId) && subscribers.get(connectionId).equals(subscriptionId)) {
                subscribers.remove(connectionId);
                // Note: We don't break here just in case (though IDs should be unique per connection)
                // return; // Optional: return if you want to stop after first match
            }
        }
    }
}
