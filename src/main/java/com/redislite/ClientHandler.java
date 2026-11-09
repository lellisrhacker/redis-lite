package com.redislite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Serves a single connected client. One instance runs on one worker thread.
 * Reads a line, runs the command, writes back the reply, repeats until QUIT or disconnect.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final CommandHandler commandHandler;
    private final Set<Socket> activeClients;

    public ClientHandler(Socket socket, CommandHandler commandHandler, Set<Socket> activeClients) {
        this.socket = socket;
        this.commandHandler = commandHandler;
        this.activeClients = activeClients;
    }

    @Override
    public void run() {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().equalsIgnoreCase("QUIT")) {
                    out.println("OK");
                    break;
                }
                out.println(commandHandler.execute(line));
            }
        } catch (IOException e) {
            // client dropped the connection - nothing to do
        } finally {
            activeClients.remove(socket);
        }
    }
}
