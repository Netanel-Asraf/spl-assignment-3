package bgu.spl.net.impl.stomp;

import java.util.function.Supplier;
import bgu.spl.net.api.StompMessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocolImpl;
import bgu.spl.net.srv.Server;

public class StompServer {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) {
        int port = 7777;
        String serverType = "tpc"; // Default to TPC

        // We can now take the port and type from command line arguments!
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            serverType = args[1];
        }
        
        if (serverType.equals("tpc")) {
            System.out.println("Starting TPC server on port " + port);
            Server.threadPerClient(
                port,
                (Supplier) StompMessagingProtocolImpl::new, 
                StompMessageEncoderDecoder::new   
            ).serve();
        } else if (serverType.equals("reactor")) {
            System.out.println("Starting Reactor server on port " + port);
            Server.reactor(
                Runtime.getRuntime().availableProcessors(), // Number of threads
                port,
                (Supplier) StompMessagingProtocolImpl::new, 
                StompMessageEncoderDecoder::new   
            ).serve();
        }
    }
}
