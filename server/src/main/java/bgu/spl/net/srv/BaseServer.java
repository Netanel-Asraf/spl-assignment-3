package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol; 
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

public abstract class BaseServer<T> implements Server<T> {

    private final int port;
    private final Supplier<StompMessagingProtocol<T>> protocolFactory; 
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;
    private final ConnectionsImpl<T> connections = new ConnectionsImpl<>();
    private int connectionIdCounter = 0;

    @SuppressWarnings("unchecked")
    public BaseServer(
            int port,
            Supplier<MessagingProtocol<T>> protocolFactory, 
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = (Supplier<StompMessagingProtocol<T>>) (Object) protocolFactory; 
        this.encdecFactory = encdecFactory;
		this.sock = null;
    }

    @Override
    public void serve() {

        try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSocket; 

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSocket.accept();

                StompMessagingProtocol<T> stompProtocol = protocolFactory.get();
                
                stompProtocol.start(connectionIdCounter, connections);

                MessagingProtocol<T> adapter = new MessagingProtocol<T>() {
                    @Override
                    public T process(T msg) {
                        stompProtocol.process(msg); 
                        return null; 
                    }

                    @Override
                    public boolean shouldTerminate() {
                        return stompProtocol.shouldTerminate();
                    }
                };

                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(
                        clientSock,
                        encdecFactory.get(),
                        adapter); 

                connections.connect(connectionIdCounter, handler);
                connectionIdCounter++;

                execute(handler);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        System.out.println("server closed");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<T>  handler);
}   
