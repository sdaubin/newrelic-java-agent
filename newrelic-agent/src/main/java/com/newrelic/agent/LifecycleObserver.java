package com.newrelic.agent;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONAware;

import com.newrelic.agent.config.IBMUtils;
import com.newrelic.agent.config.SystemPropertyFactory;
import com.newrelic.agent.discovery.AgentArguments;
import com.newrelic.agent.discovery.StatusClient;
import com.newrelic.agent.discovery.StatusMessage;
import com.newrelic.agent.logging.IAgentLogger;
import com.newrelic.agent.service.ServiceManager;
import com.newrelic.bootstrap.BootstrapAgent;

/**
 * This class is used to communicate important startup information back to an attaching
 * process.
 */
public class LifecycleObserver {
    protected LifecycleObserver() {
    }

    void agentStarted() {
    }

    void serviceManagerStarted(ServiceManager serviceManager) {
    }

    void agentAlreadyRunning() {
    }

    public boolean isAgentSafe() {
        return !(IBMUtils.isIbmJVM() &&
            !Boolean.parseBoolean(SystemPropertyFactory.getSystemPropertyProvider()
                .getSystemProperty(BootstrapAgent.TRY_IBM_ATTACH_SYSTEM_PROPERTY)));
    }

    public static LifecycleObserver createLifecycleObserver(IAgentLogger LOG, final AgentArguments args) {
        if (args.getServerPort() != null || args.getEndpoint() != null) {
            try {
                final MessageWriter messageWriter;
                if (args.getEndpoint() != null) {
                    messageWriter = message -> postMessageAsJson(args.getEndpoint(), message);
                } else {
                    final StatusClient client = StatusClient.create(args.getServerPort().intValue());
                    messageWriter = client::write;
                }

                messageWriter.write(StatusMessage.info(args.getId(), "Msg", "Initializing agent"));
                return new AttachLifecycleObserver(messageWriter, args);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e, e.getMessage());
            }
        }
        return new LifecycleObserver();
    }

    private static void postMessageAsJson(String endpoint, JSONAware value)
      throws IOException {

        final String json = value.toJSONString();

        final HttpPost httpPost = new HttpPost(endpoint);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Accept", "application/json");
        httpPost.setEntity(new StringEntity(json));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse response = (CloseableHttpResponse) client
                .execute(httpPost);
        }
    }

    private interface MessageWriter {
        void write(StatusMessage message) throws IOException;
    }

    private static class AttachLifecycleObserver extends LifecycleObserver {
        private final AtomicReference<ServiceManager> serviceManager = new AtomicReference<>();
        private final String id;
        private final MessageWriter messageWriter;

        public AttachLifecycleObserver(MessageWriter messageWriter, AgentArguments args) {
            this.id = args.getId();
            this.messageWriter = messageWriter;
        }

        @Override
        public boolean isAgentSafe() {
            if (!super.isAgentSafe()) {
                writeMessage(StatusMessage.error(id, "Error",
                        "The agent attach feature is not supported for IBM JVMs"));
                return false;
            }
            return true;
        }

        /**
         * Busy waits until the agent establishes a connection with New Relic.
         *
         * Under normal circumstances this can take several minutes. With {@code sync_startup: true} it should be nearly instantaneous.
         */
        @Override
        void agentStarted() {
            writeMessage(StatusMessage.warn(id, "Msg",
                    "The agent has started and is connecting to New Relic. This may take a few minutes."));
            while (!writeConnectMessage()) {
                try {
                    TimeUnit.SECONDS.sleep(30);
                    writeMessage(StatusMessage.warn(id, "Msg", "Establishing a connection with New Relic..."));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Writes a message to an output stream containing the application URL when the agent has successfully connected to New Relic.
         *
         * @return true if the agent has successfully connected to New Relic, otherwise false
         */
        private boolean writeConnectMessage() {
            final ServiceManager serviceManager = this.serviceManager.get();
            if (serviceManager != null) {
                IRPMService rpmService = serviceManager.getRPMServiceManager().getRPMService();
                if (rpmService.isStoppedOrStopping()) {
                    writeMessage(StatusMessage.error(id, "Error", "The agent has shutdown. Make sure that the license key matches the region."));
                    return true;
                }
                if (rpmService.isConnected()) {
                    writeMessage(StatusMessage.success(id, rpmService.getApplicationLink()));
                    return true;
                }
            }
            return false;
        }

        private void writeMessage(StatusMessage message) {
            try {
                System.out.println(message);
                messageWriter.write(message);
            } catch (IOException e) {
                // ignore
            }
        }

        @Override
        public void serviceManagerStarted(ServiceManager serviceManager) {
            this.serviceManager.set(serviceManager);
        }

        public void agentAlreadyRunning() {
            writeMessage(StatusMessage.error(id, "Error", "The New Relic agent is already attached to this process"));
        }
    }
}
