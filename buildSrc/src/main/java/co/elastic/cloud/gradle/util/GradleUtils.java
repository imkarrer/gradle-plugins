package co.elastic.cloud.gradle.util;

import org.gradle.api.Project;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.testing.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GradleUtils {

    public static boolean areTestsFiltered(Test task) {
        return Optional.ofNullable(task.getFilter())
                .filter(it -> it instanceof DefaultTestFilter)
                .map(it -> (DefaultTestFilter) it)
                .map(it -> {
                    Set<String> filters = new HashSet<>();
                    filters.addAll(Optional.ofNullable(it.getCommandLineIncludePatterns()).orElse(Collections.emptySet()));
                    filters.addAll(Optional.ofNullable(it.getIncludePatterns()).orElse(Collections.emptySet()));
                    return filters;
                })
                .map(filters -> !filters.isEmpty())
                .orElse(false);
    }

    public static boolean isCi() {
        return System.getenv("BUILD_URL") != null;
    }
}
