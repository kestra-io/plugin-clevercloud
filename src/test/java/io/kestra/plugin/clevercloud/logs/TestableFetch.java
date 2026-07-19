package io.kestra.plugin.clevercloud.logs;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Test-only subclass that overrides baseUrlV4() to point at a WireMock server instead of the
 * real Clever Cloud API.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class TestableFetch extends Fetch {

    private String testBaseUrl;

    @Override
    protected String baseUrlV4() {
        return testBaseUrl;
    }
}
