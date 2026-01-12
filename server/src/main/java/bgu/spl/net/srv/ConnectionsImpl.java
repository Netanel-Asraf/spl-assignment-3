package bgu.spl.net.srv;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue; 

public class ConnectionsImpl<T> implements Connections<T> {
    ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionMap;
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> topics = new ConcurrentHashMap<>();

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connectionMap.get(connectionId);
        if(handler != null){
            handler.send(msg);
            return true;
        }   
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        ConcurrentLinkedQueue<Integer> subscribers = topics.get(channel);
        if (subscribers != null) {
            for (Integer userId : subscribers) {
                send(userId, msg); 
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        connectionMap.remove(connectionId);
    }
}
