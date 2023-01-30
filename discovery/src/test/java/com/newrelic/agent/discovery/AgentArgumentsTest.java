package com.newrelic.agent.discovery;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONValue;
import org.junit.Test;

public class AgentArgumentsTest {

    @Test
    public void serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "111");
        map.put("serverPort", 123);
        AgentArguments args = AgentArguments.fromJsonObject(map);
        String jsonString = JSONValue.toJSONString(args);
        AgentArguments deserialize = AgentArguments.fromJsonObject(JSONValue.parse(jsonString));
        assertEquals(123, deserialize.getServerPort().intValue());
        assertEquals("111", deserialize.getId());
    }
}
