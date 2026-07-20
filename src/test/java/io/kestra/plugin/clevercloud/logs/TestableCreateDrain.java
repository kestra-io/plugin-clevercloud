package io.kestra.plugin.clevercloud.logs;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class TestableCreateDrain extends CreateDrain {

    private String testBaseUrl;

    @Override
    protected String baseUrlV4() {
        return testBaseUrl;
    }
}
