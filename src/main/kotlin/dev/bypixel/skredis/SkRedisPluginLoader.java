package dev.bypixel.skredis;

import com.google.gson.Gson;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SkRedisPluginLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        PluginLibraries pluginLibraries = load();

        pluginLibraries.asDependencies().forEach(resolver::addDependency);
        pluginLibraries.asRepositories().forEach(resolver::addRepository);

        classpathBuilder.addLibrary(resolver);
    }

    public PluginLibraries load() {
        try (InputStream inputStream = getClass().getResourceAsStream("/paper-libraries.json");
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, PluginLibraries.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class PluginLibraries {
        private Map<String, String> repositories;
        private List<String> dependencies;

        public Map<String, String> getRepositories() {
            return repositories;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public Stream<Dependency> asDependencies() {
            return dependencies.stream().map(d -> new Dependency(new DefaultArtifact(d), null));
        }

        public Stream<RemoteRepository> asRepositories() {
            return repositories.entrySet().stream().map(entry -> {
                String url = entry.getValue();
                if (url.contains("https://repo1.maven.org/maven2") ||
                        url.contains("http://repo1.maven.org/maven2") ||
                        url.contains("https://repo.maven.apache.org/maven2") ||
                        url.contains("http://repo.maven.apache.org/maven2")) {
                    return new RemoteRepository.Builder(
                            "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
                    ).build();
                } else {
                    return new RemoteRepository.Builder(entry.getKey(), "default", entry.getValue()).build();
                }
            });
        }
    }
}
