package io.kestra.plugin.clevercloud.deployments.model;

import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Ensures {@link Deployment.EpochMillisStringDeserializer} (Jackson 2) and
 * {@link Deployment.Jackson3EpochMillisStringDeserializer} (Jackson 3) agree on every case, since
 * the two Jackson majors coexist and neither {@code @JsonDeserialize} annotation is bridged to the
 * other Jackson major.
 */
class DeploymentDeserializationTest {
    private static final JsonMapper JACKSON3_MAPPER = JsonMapper.builder().build();

    @Test
    void validEpochMillisString() throws Exception {
        var json = """
            {"date": "1782127329927"}""";

        assertDate(json, Instant.ofEpochMilli(1782127329927L));
    }

    @Test
    void absentField() throws Exception {
        assertDate("{}", null);
    }

    @Test
    void blankString() throws Exception {
        var json = """
            {"date": ""}""";

        assertDate(json, null);
    }

    @Test
    void nonNumericString() throws Exception {
        var json = """
            {"date": "n/a"}""";

        assertDate(json, null);
    }

    private void assertDate(String json, Instant expected) throws Exception {
        var jackson2 = JacksonMapper.ofJson().readValue(json, Deployment.class);
        assertThat(jackson2.getDate(), expected == null ? is(nullValue()) : is(expected));

        var jackson3 = JACKSON3_MAPPER.readValue(json, Deployment.class);
        assertThat(jackson3.getDate(), expected == null ? is(nullValue()) : is(expected));
    }
}
