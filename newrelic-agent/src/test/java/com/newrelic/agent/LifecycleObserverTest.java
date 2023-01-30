package com.newrelic.agent;

import com.google.common.collect.Lists;
import com.newrelic.agent.discovery.AgentArguments;
import com.newrelic.agent.discovery.StatusMessage;
import com.newrelic.agent.logging.IAgentLogger;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LifecycleObserverTest {

    @Test
    public void tryStatusClient() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean running = new AtomicBoolean(true);
        List<StatusMessage> messages = Lists.newCopyOnWriteArrayList();
        final CountDownLatch latch = new CountDownLatch(2);
        try {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                executor.submit(() -> {
                    while (running.get()) {
                        try (Socket socket = serverSocket.accept()) {
                            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                            // dynamic agent first sends back this uid in the stream
                            final long uid = in.readLong();
                            assertEquals(StatusMessage.serialVersionUID, uid);
                            messages.add(StatusMessage.readExternal(in));
                            latch.countDown();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                Map<String, Object> map = new HashMap<>();
                map.put("id", "666");
                map.put("serverPort", serverSocket.getLocalPort());
                AgentArguments args = AgentArguments.fromJsonObject(map);

                LifecycleObserver lifecycleObserver = LifecycleObserver.createLifecycleObserver(Mockito.mock(IAgentLogger.class), args);
                assertTrue(lifecycleObserver.isAgentSafe());
                lifecycleObserver.agentAlreadyRunning();
                latch.await(10, TimeUnit.SECONDS);
            }
        } finally {
            running.set(false);
            executor.shutdown();
        }
        assertEquals(2, messages.size());
        assertEquals("Initializing agent", messages.get(0).getMessage());
        assertEquals(Level.INFO, messages.get(0).getLevel());
        assertEquals("666", messages.get(0).getProcessId());
        assertEquals("The New Relic agent is already attached to this process", messages.get(1).getMessage());
        assertEquals(Level.SEVERE, messages.get(1).getLevel());
    }

    @Test
    public void tryHttp() throws Exception {
        List<Map<String, Object>> messages = Lists.newCopyOnWriteArrayList();
        final CountDownLatch latch = new CountDownLatch(2);
        InetSocketAddress address = new InetSocketAddress(0);
        HttpServer server = HttpServer.create(address, 0);
        server.createContext("/messages", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                Map<String, Object> parsed = (Map<String, Object>) new JSONParser().parse(new InputStreamReader(in));
                messages.add(parsed);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
            latch.countDown();
        });
        try {
            server.start();
            Map<String, Object> map = new HashMap<>();
            map.put("id", "666");
            map.put("endpoint", "http://localhost:" + server.getAddress().getPort() + "/messages");
            AgentArguments args = AgentArguments.fromJsonObject(map);
            LifecycleObserver lifecycleObserver = LifecycleObserver.createLifecycleObserver(Mockito.mock(IAgentLogger.class), args);
            lifecycleObserver.agentAlreadyRunning();
            latch.await(10, TimeUnit.SECONDS);
        } finally {
            server.stop(0);
        }
        assertEquals(2, messages.size());
        assertEquals("Initializing agent", messages.get(0).get("message"));
        assertEquals("INFO", messages.get(0).get("level"));
    }
}