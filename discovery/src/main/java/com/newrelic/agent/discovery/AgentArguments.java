package com.newrelic.agent.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * The environment and system properties from the attach process are passed to the agent's
 * `agentmain` method as base 64 encoded json.
 */
public class AgentArguments implements JSONAware {
    public static final String NEW_RELIC_APP_NAME_ENV_VARIABLE = "NEW_RELIC_APP_NAME";
    public static final String NEW_RELIC_COMMAND_LINE_ENV_VARIABLE = "NEW_RELIC_COMMAND_LINE";

    private static final String SYSTEM_PROPERTIES_AGENT_ARGS_KEY = "properties";
    private static final String ENVIRONMENT_AGENT_ARGS_KEY = "environment";
    private static final String SERVER_PORT_AGENT_ARGS_KEY = "serverPort";
    private static final String ENDPOINT_AGENT_ARGS_KEY = "endpoint";
    private static final String ID_AGENT_ARGS_KEY = "id";
    private static final String START_ASYNC_AGENT_ARGS_KEY = "startAsync";

    private final Map<String, String> environment;
    private final Map<String, String> systemProperties;
    private Number serverPort;
    private String endpoint;
    private String id;
    private boolean startAsync;

    public AgentArguments(Map<String, String> environment, Map<String, String> systemProperties) {
        this.environment = environment;
        this.systemProperties = systemProperties;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public Number getServerPort() {
        return serverPort;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getId() {
        return id;
    }

    public boolean isStartAsync() {
        return startAsync;
    }

    public void setAppName(String appName) {
        environment.put(NEW_RELIC_APP_NAME_ENV_VARIABLE, appName);
    }

    public static AgentArguments fromArgumentsString(String agentArgs) {
        if (agentArgs != null && !agentArgs.isEmpty()) {
            try {
                return fromJsonObject(new JSONParser().parse(agentArgs));
            } catch (org.json.simple.parser.ParseException e) {
                // FIXME
            }
        }
        return new AgentArguments(new HashMap<>(), new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    public static AgentArguments fromJsonObject(Object object) {
        Map<String, Object> map = (Map<String, Object>) object;
        AgentArguments args = new AgentArguments(
                (Map<String, String>) map.get(ENVIRONMENT_AGENT_ARGS_KEY),
                (Map<String, String>) map.get(SYSTEM_PROPERTIES_AGENT_ARGS_KEY));
        args.serverPort = (Number) map.get(SERVER_PORT_AGENT_ARGS_KEY);
        args.endpoint = (String) map.get(ENDPOINT_AGENT_ARGS_KEY);
        args.id = (String) map.get(ID_AGENT_ARGS_KEY);
        Object startAsync = map.get(START_ASYNC_AGENT_ARGS_KEY);
        args.startAsync = startAsync == null ? false : (Boolean)startAsync;
        return args;
    }

    @Override
    public String toJSONString() {
        Map<String, Object> args = new HashMap<>();
        args.put(ENVIRONMENT_AGENT_ARGS_KEY, environment);
        args.put(SYSTEM_PROPERTIES_AGENT_ARGS_KEY, systemProperties);
        args.put(ID_AGENT_ARGS_KEY, id);
        args.put(START_ASYNC_AGENT_ARGS_KEY, startAsync);
        if (serverPort != null) {
            args.put(SERVER_PORT_AGENT_ARGS_KEY, serverPort);
        }
        if (endpoint != null) {
            args.put(ENDPOINT_AGENT_ARGS_KEY, endpoint);
        }
        return JSONObject.toJSONString(args);
    }

    private static Map<String, String> getEnvironmentMap() {
        final Map<String, String> environment = new HashMap<>();
        for (Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith("NEW_RELIC_")) {
                String value = System.getenv(entry.getKey());
                if (value != null) {
                    environment.put(entry.getKey(), value);
                }
            }
        }
        return environment;
    }

    private static Map<String, String> getSystemPropertiesMap() {
        final Map<String, String> properties = new HashMap<>();
        for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
            if (entry.getKey().toString().startsWith("newrelic.")) {
                Object value = System.getProperties().get(entry.getKey());
                if (value != null) {
                    properties.put(entry.getKey().toString(), value.toString());
                }
            }
        }
        return properties;
    }
}
