package net.runelite.client.plugins.microbot.kspmule;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Short-lived localhost RPC client for KSP Trade Receiver. */
@Slf4j
final class KspLocalMuleClient
{
    enum State { UNKNOWN, QUEUED, ACTIVE, COMPLETE, FAILED, CANCELLED }

    static final class Status
    {
        final State state;
        final int queuePosition;
        final String muleName;
        final int world;
        final int x;
        final int y;
        final int plane;
        final long coins;
        final String message;

        private Status(State state, int queuePosition, String muleName,
                       int world, int x, int y, int plane, long coins, String message)
        {
            this.state = state;
            this.queuePosition = queuePosition;
            this.muleName = muleName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.coins = coins;
            this.message = message;
        }

        static Status unknown(String message)
        {
            return new Status(State.UNKNOWN, 0, "", 0, 0, 0, 0, 0L, message);
        }
    }

    Status ready(int port, String requestId, String accountName, long coins, int world)
    {
        return parse(call(port, "READY\t" + requestId + "\t" + encode(accountName)
                + "\t" + coins + "\t" + world));
    }

    Status status(int port, String requestId)
    {
        return parse(call(port, "STATUS\t" + requestId));
    }

    void cancel(int port, String requestId)
    {
        if (requestId != null && !requestId.isBlank()) call(port, "CANCEL\t" + requestId);
    }

    boolean acknowledgeComplete(int port, String requestId)
    {
        return requestId != null && !requestId.isBlank()
                && "ACKED".equals(call(port, "COMPLETE_ACK\t" + requestId));
    }

    boolean ping(int port)
    {
        return "PONG".equals(call(port, "PING"));
    }

    private String call(int port, String request)
    {
        if (port < 1024 || port > 65535) return "ERROR\tINVALID_PORT";
        try (Socket socket = new Socket())
        {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_500);
            socket.setSoTimeout(2_500);
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)))
            {
                out.write(request);
                out.newLine();
                out.flush();
                String response = in.readLine();
                return response == null ? "ERROR\tEMPTY_RESPONSE" : response;
            }
        }
        catch (IOException ex)
        {
            log.debug("Local mule RPC unavailable on 127.0.0.1:{}: {}", port, ex.getMessage());
            return "ERROR\tUNREACHABLE";
        }
    }

    private Status parse(String response)
    {
        if (response == null || response.isBlank()) return Status.unknown("Empty response");
        String[] p = response.split("\\t", -1);
        switch (p[0])
        {
            case "QUEUED":
                return new Status(State.QUEUED, p.length > 1 ? parseInt(p[1]) : 0,
                        "", 0, 0, 0, 0, 0L, "");
            case "ACTIVE":
                if (p.length < 7) return Status.unknown("Malformed ACTIVE response");
                return new Status(State.ACTIVE, 0, decode(p[1]), parseInt(p[2]),
                        parseInt(p[3]), parseInt(p[4]), parseInt(p[5]), parseLong(p[6]), "");
            case "COMPLETE":
                return new Status(State.COMPLETE, 0, "", 0, 0, 0, 0, 0L, "");
            case "FAILED":
                return new Status(State.FAILED, 0, "", 0, 0, 0, 0, 0L,
                        p.length > 1 ? decode(p[1]) : "Mule job failed");
            case "CANCELLED":
                return new Status(State.CANCELLED, 0, "", 0, 0, 0, 0, 0L, "Cancelled");
            case "UNKNOWN":
                return Status.unknown("Unknown job");
            default:
                return Status.unknown(p.length > 1 ? p[1] : response);
        }
    }

    private static int parseInt(String value)
    {
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return 0; }
    }

    private static long parseLong(String value)
    {
        try { return Long.parseLong(value); } catch (NumberFormatException ex) { return 0L; }
    }

    private static String encode(String value)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value)
    {
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { return ""; }
    }
}
