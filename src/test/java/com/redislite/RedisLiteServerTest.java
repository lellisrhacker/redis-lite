package com.redislite;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** End-to-end tests: a real server on a free port, talked to over real sockets. */
public class RedisLiteServerTest {

    private RedisLiteServer server;

    @Before
    public void startServer() throws IOException {
        server = new RedisLiteServer(0);
        server.start();
    }

    @After
    public void stopServer() {
        server.stop();
    }

    /** Tiny client used only by the tests. */
    private static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        TestClient(int port) throws IOException {
            socket = new Socket("localhost", port);
            socket.setSoTimeout(5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        String send(String command) throws IOException {
            out.println(command);
            return in.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    public void basicCommandsOverTcp() throws Exception {
        try (TestClient client = new TestClient(server.getPort())) {
            assertEquals("PONG", client.send("PING"));
            assertEquals("OK", client.send("SET lang java"));
            assertEquals("java", client.send("GET lang"));
            assertEquals("(integer) 1", client.send("DEL lang"));
            assertEquals("(nil)", client.send("GET lang"));
            assertEquals("OK", client.send("QUIT"));
        }
    }

    @Test
    public void dataIsSharedBetweenClients() throws Exception {
        try (TestClient a = new TestClient(server.getPort());
             TestClient b = new TestClient(server.getPort())) {
            a.send("SET shared hello");
            assertEquals("hello", b.send("GET shared"));
        }
    }

    @Test
    public void keyExpiresAfterTtl() throws Exception {
        try (TestClient client = new TestClient(server.getPort())) {
            client.send("SET temp value EX 1");
            assertEquals("value", client.send("GET temp"));
            Thread.sleep(1300);
            assertEquals("(nil)", client.send("GET temp"));
        }
    }

    @Test
    public void manyConcurrentClientsDoNotInterfere() throws Exception {
        int clients = 30;
        int opsPerClient = 50;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<Void>> futures = new ArrayList<>();

        for (int c = 0; c < clients; c++) {
            final int id = c;
            futures.add(pool.submit(() -> {
                try (TestClient client = new TestClient(server.getPort())) {
                    for (int i = 0; i < opsPerClient; i++) {
                        String key = "c" + id + "-k" + i;
                        assertEquals("OK", client.send("SET " + key + " " + i));
                        assertEquals(String.valueOf(i), client.send("GET " + key));
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get(20, TimeUnit.SECONDS); // rethrows any assertion failure from the worker
        }
        pool.shutdown();

        try (TestClient client = new TestClient(server.getPort())) {
            assertEquals("7", client.send("GET c3-k7"));
        }
    }
}
