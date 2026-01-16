package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

public class Frame {
    private String command;
    private Map<String, String> headers = new HashMap<>();
    private String body;

    public Frame(String command, Map<String, String> headers, String body) {
        this.command = command;
        this.headers = headers;
        this.body = body;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(command).append("\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            sb.append(header.getKey()).append(":").append(header.getValue()).append("\n");
        }
        sb.append("\n"); 
        if (body != null) {
            sb.append(body);
        }
        return sb.toString();
    }

    public static Frame parse(String msg) {
        String[] lines = msg.split("\n");
        int lineIdx = 0;

        String command = "";
        if (lineIdx < lines.length) {
            command = lines[lineIdx].trim();
            lineIdx++;
        }

        Map<String, String> headers = new HashMap<>();
        while (lineIdx < lines.length) {
            String line = lines[lineIdx];
            if (line.isEmpty()) { 
                lineIdx++; 
                break; 
            }
            
            int splitAt = line.indexOf(':');
            if (splitAt != -1) {
                String key = line.substring(0, splitAt);
                String value = line.substring(splitAt + 1);
                headers.put(key, value);
            }
            lineIdx++;
        }

        StringBuilder bodyBuilder = new StringBuilder();
        while (lineIdx < lines.length) {
            bodyBuilder.append(lines[lineIdx]).append("\n");
            lineIdx++;
        }
        String body = bodyBuilder.toString();
        
        if (body.length() > 0 && body.endsWith("\n")) {
             body = body.substring(0, body.length() - 1);
        }

        return new Frame(command, headers, body);
    }
}