package bgu.spl.net.impl.echo;

import bgu.spl.net.srv.Server;

public class EchoServer {

    public static void main(String[] args) {

        Server.threadPerClient(
                7777, 
                () -> new EchoProtocol(), 
                LineMessageEncoderDecoder::new 
        ).serve();
    }
}
