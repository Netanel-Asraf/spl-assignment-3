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
    private final Supplier<StompMessagingProtocol<T>> protocolFactory; // We store it as Stomp factory!
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;
    private final ConnectionsImpl<T> connections = new ConnectionsImpl<>();
    private int connectionIdCounter = 0;

    @SuppressWarnings("unchecked")
    public BaseServer(
            int port,
            Supplier<MessagingProtocol<T>> protocolFactory, // Signature matches Server.java
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        // CASTING MAGIC: We force the generic factory into a Stomp factory
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

                // 1. Get your real Stomp protocol
                StompMessagingProtocol<T> stompProtocol = protocolFactory.get();
                
                // 2. Initialize it (The 'start' function exists here!)
                stompProtocol.start(connectionIdCounter, connections);

                // 3. THE ADAPTER: This allows us to use the old BlockingConnectionHandler
                // without changing its code or the Stomp interface.
                MessagingProtocol<T> adapter = new MessagingProtocol<T>() {
                    @Override
                    public T process(T msg) {
                        stompProtocol.process(msg); // Call your void method
                        return null; // Return null (Handler ignores it)
                    }

                    @Override
                    public boolean shouldTerminate() {
                        return stompProtocol.shouldTerminate();
                    }
                };

                // 4. Pass the Adapter to the handler
                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(
                        clientSock,
                        encdecFactory.get(),
                        adapter); // The Handler is happy with the adapter!

                // 5. Register in Phonebook
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