package io.kestra.plugin.clevercloud.organisations;

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
public class TestableListMembers extends ListMembers {

    private String testBaseUrl;

    @Override
    protected String baseUrl() {
        return testBaseUrl;
    }
}
