package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.github.GithubDownloadPlugin;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.RetryUtils;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;


abstract public class PushManifestListTask extends DefaultTask {

    public PushManifestListTask() {
        getDigestFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".digest")
        );
    }

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Input
    public abstract MapProperty<Architecture, String> getArchitectureTags();

    @Input
    public abstract Property<String> getTag();

    @OutputFile
    public abstract RegularFileProperty getDigestFile();

    @Internal
    public Provider<String> getDigest() {
        return getDigestFile().map(regularFile -> {
            try {
                return Files.readAllLines(regularFile.getAsFile().toPath()).get(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void pushManifestList() throws IOException {
        final Set<String> templates = getArchitectureTags().get().values().stream()
                .map(each -> {
                    String template = each;
                    for (Architecture value : Architecture.values()) {
                        template = template.replace(value.dockerName(), "ARCH");
                    }
                    return template;
                })
                .collect(Collectors.toSet());
        if (templates.isEmpty()) {
            throw new GradleException("Can't push manifest list, no input tags are present");
        }
        if (templates.size() > 1) {
            throw new GradleException("Can't derive template from manifest list: " + templates);
        }

        final String output = RetryUtils.retry(() -> pushManifestList(templates))
                .maxAttempt(6)
                .exponentialBackoff(1000, 30000)
                .onRetryError(error -> getLogger().warn("Error while pushing manifest. Retrying", error))
                .execute();

        if (output.startsWith("Digest: sha256:")) {
            Files.write(
                    getDigestFile().get().getAsFile().toPath(),
                    output.substring(8, 79).getBytes(StandardCharsets.UTF_8)
            );
        } else {
            if (output.isEmpty()) {
                throw new GradleException("manifest-tool succeeded but generated no ouxput. Check the task output for additional details.");
            } else {
                throw new GradleException("manifest-tool succeeded but generated unexpected output: `" + output +
                        "`. Check the task output for additional details.");
            }
        }
    }

    @NotNull
    private String pushManifestList(Set<String> templates) {
        try {
            final ExecResult result;
            final String output;
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                result = getExecOperations().exec(spec -> {
                    spec.setEnvironment(Collections.emptyMap());
                    spec.setExecutable(GithubDownloadPlugin.getExecutable(getProject(), "manifest-tool"));
                    spec.setArgs(Arrays.asList(
                            "push", "from-args",
                            "--platforms",
                            getArchitectureTags().get().keySet().stream()
                                    .map(each -> "linux/" + each.map(ImmutableMap.of(
                                            Architecture.AARCH64, "arm64",
                                            Architecture.X86_64, "amd64"
                                    )))
                                    .collect(Collectors.joining(",")),
                            "--template", templates.iterator().next(),
                            "--target", getTag().get()
                    ));
                    spec.setStandardOutput(out);
                    spec.setIgnoreExitValue(true);
                });
                output = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            }
            if (result.getExitValue() != 0) {
                throw new GradleException("Creating the manifest list failed: " + output);
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
