package pokersolver.api;

import pokersolver.utils.JsonUtil;
import io.javalin.json.JsonMapper;
import java.lang.reflect.Type;
import tools.jackson.databind.ObjectMapper;

/** Bridges Javalin's JSON SPI to the project-wide Jackson 3 ({@code tools.jackson}) mapper. */
public final class JacksonJsonMapper implements JsonMapper {

    private final ObjectMapper mapper = JsonUtil.MAPPER;

    @Override
    public String toJsonString(Object obj, Type type) {
        return mapper.writeValueAsString(obj);
    }

    @Override
    public <T> T fromJsonString(String json, Type targetType) {
        return mapper.readValue(json, mapper.constructType(targetType));
    }
}
