package bgu.spl.net.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StompMessageEncoderDecoder implements MessageEncoderDecoder<String> {

    private byte[] bytes = new byte[1 << 10]; // Start with 1k
    private int len = 0;

    @Override
    public String decodeNextByte(byte nextByte) {
        // notice that the top 128 ascii characters have the same representation as their utf-8 counterparts
        // this allow us to do the following comparison
        if (nextByte == '\u0000') { // <--- WAIT FOR NULL BYTE, NOT NEWLINE
            return popString();
        }

        pushByte(nextByte);
        return null; // not a complete line yet
    }

    @Override
    public byte[] encode(String message) {
        // We add the null character to the end of the message when sending
        return (message + "\u0000").getBytes(StandardCharsets.UTF_8);
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
    }

    private String popString() {
        //notice that we explicitly requesting that the string will be decoded from UTF-8
        //this is not actually required as it is the default encoding in java.
        String result = new String(bytes, 0, len, StandardCharsets.UTF_8);
        len = 0;
        return result;
    }
}