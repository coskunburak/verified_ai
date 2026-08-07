package com.verifiedai.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RepositoryHierarchyTest {

    @Test
    void rootControllerServiceRepositoryEntityPackagesAreNotUsed() throws Exception {
        Path mainPackage = Path.of("src/main/java/com/verifiedai");
        List<String> forbidden = List.of("controller", "controllers", "service", "services", "repository", "repositories", "entity", "entities");

        for (String name : forbidden) {
            assertThat(Files.exists(mainPackage.resolve(name))).as(name).isFalse();
        }
    }
}

