package io.kestra.plugin.clevercloud.deployments;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Test-only subclass that overrides baseUrl() to point at a MockWebServer instead of the
 * real Clever Cloud API.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class TestableWaitForState extends WaitForState {

    private String testBaseUrl;

    @Override
    protected String baseUrl() {
        return testBaseUrl;
    }
}
