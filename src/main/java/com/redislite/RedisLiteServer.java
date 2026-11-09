package com.redislite;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP server. The accept loop runs on its own thread and hands every new
 * connection to a fixed-size worker pool, so many clients can be served at once.
 */
public class RedisLiteServer {

    public static final int DEFAULT_PORT = 6380;
    private static final int MAX_CLIENTS = 100;

    private final int port;
    private final KeyValueStore store = new KeyValueStore();
    private final CommandHandler commandHandler = new CommandHandler(store);
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();
    private final AtomicInteger threadCounter = new AtomicInteger();

    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_CLIENTS, runnable -> {
        Thread t = new Thread(runnable, "client-worker-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService expiryCleaner = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "expiry-cleaner");
        t.setDaemon(true);
        return t;
    });

    private ServerSocket serverSocket;
    private volatile boolean running;

    public RedisLiteServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port); // port 0 = pick any free port (used by tests)
        running = true;

        expiryCleaner.scheduleAtFixedRate(store::removeExpired, 1, 1, TimeUnit.SECONDS);

        Thread acceptThread = new Thread(this::acceptLoop, "accept-loop");
        acceptThread.start();
        System.out.println("Redis-Lite listening on port " + getPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                activeClients.add(client);
                workers.execute(new ClientHandler(client, commandHandler, activeClients));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // already closed
        }
        for (Socket client : activeClients) {
            try {
                client.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
        workers.shutdownNow();
        expiryCleaner.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        RedisLiteServer server = new RedisLiteServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
