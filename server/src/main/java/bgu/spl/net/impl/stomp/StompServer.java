package bgu.spl.net.impl.stomp;

import java.util.function.Supplier;

import bgu.spl.net.api.StompMessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocolImpl;
import bgu.spl.net.srv.Server;

public class StompServer {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) {
        int port = 7777;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        // We cast the Stomp factory to a raw Supplier to trick the compiler
        // The BaseServer constructor (which we modified) will catch it and cast it back.
        Server.threadPerClient(
            port,
            (Supplier) StompMessagingProtocolImpl::new, 
            StompMessageEncoderDecoder::new   
        ).serve();
    }
}
