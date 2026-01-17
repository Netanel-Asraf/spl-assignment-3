package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol; // Add this!
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class Reactor<T> implements Server<T> {

    private final int port;
    private final Supplier<StompMessagingProtocol<T>> protocolFactory; // Change to Stomp factory
    private final Supplier<MessageEncoderDecoder<T>> readerFactory;
    private final ActorThreadPool pool;
    private Selector selector;
    private final ConnectionsImpl<T> connections = new ConnectionsImpl<>(); // Add the Phonebook!
    private int connectionIdCounter = 0; // Add the ID counter!

    private Thread selectorThread;
    private final ConcurrentLinkedQueue<Runnable> selectorTasks = new ConcurrentLinkedQueue<>();

    @SuppressWarnings("unchecked")
    public Reactor(
            int numThreads,
            int port,
            Supplier<MessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> readerFactory) {

        this.pool = new ActorThreadPool(numThreads);
        this.port = port;
        // CASTING MAGIC: Just like in BaseServer!
        this.protocolFactory = (Supplier<StompMessagingProtocol<T>>) (Object) protocolFactory;
        this.readerFactory = readerFactory;
    }

    @Override
    public void serve() {
        selectorThread = Thread.currentThread();
        try (Selector selector = Selector.open();
                ServerSocketChannel serverSock = ServerSocketChannel.open()) {

            this.selector = selector; 

            serverSock.bind(new InetSocketAddress(port));
            serverSock.configureBlocking(false);
            serverSock.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Server started");

            while (!Thread.currentThread().isInterrupted()) {
                selector.select();
                runSelectionThreadTasks();

                for (SelectionKey key : selector.selectedKeys()) {
                    if (!key.isValid()) {
                        continue;
                    } else if (key.isAcceptable()) {
                        handleAccept(serverSock, selector);
                    } else {
                        handleReadWrite(key);
                    }
                }
                selector.selectedKeys().clear();
            }

        } catch (ClosedSelectorException ex) {
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        System.out.println("server closed!!!");
        pool.shutdown();
    }

    private void handleAccept(ServerSocketChannel serverChan, Selector selector) throws IOException {
        SocketChannel clientChan = serverChan.accept();
        clientChan.configureBlocking(false);

        // 1. Get the real Stomp protocol
        StompMessagingProtocol<T> stompProtocol = protocolFactory.get();
        
        // 2. Initialize it
        stompProtocol.start(connectionIdCounter, connections);

        // 3. THE ADAPTER: Bridge Stomp to the Reactor's expected interface
        MessagingProtocol<T> adapter = new MessagingProtocol<T>() {
            @Override
            public T process(T msg) {
                stompProtocol.process(msg);
                return null; // Stomp sends via connections.send()
            }
            @Override
            public boolean shouldTerminate() {
                return stompProtocol.shouldTerminate();
            }
        };

        final NonBlockingConnectionHandler<T> handler = new NonBlockingConnectionHandler<>(
                readerFactory.get(),
                adapter,
                clientChan,
                this);

        // 4. Register in Phonebook
        connections.connect(connectionIdCounter, handler);
        connectionIdCounter++;

        clientChan.register(selector, SelectionKey.OP_READ, handler);
    }

    // ... (keep the rest of Reactor.java the same) ...
    /*package*/ void updateInterestedOps(SocketChannel chan, int ops) {
        final SelectionKey key = chan.keyFor(selector);
        if (Thread.currentThread() == selectorThread) {
            key.interestOps(ops);
        } else {
            selectorTasks.add(() -> {
                key.interestOps(ops);
            });
            selector.wakeup();
        }
    }

    private void handleReadWrite(SelectionKey key) {
        @SuppressWarnings("unchecked")
        NonBlockingConnectionHandler<T> handler = (NonBlockingConnectionHandler<T>) key.attachment();

        if (key.isReadable()) {
            Runnable task = handler.continueRead();
            if (task != null) {
                pool.submit(handler, task);
            }
        }

        if (key.isValid() && key.isWritable()) {
            handler.continueWrite();
        }
    }

    private void runSelectionThreadTasks() {
        while (!selectorTasks.isEmpty()) {
            selectorTasks.remove().run();
        }
    }

    @Override
    public void close() throws IOException {
        if (selector != null) selector.close();
    }
}
