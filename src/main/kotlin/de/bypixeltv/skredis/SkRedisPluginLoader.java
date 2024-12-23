package de.bypixeltv.skredis;

import com.google.gson.Gson;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
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
        try (InputStreamReader inputStream = new InputStreamReader(
                getClass().getResourceAsStream("/paper-libraries.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(inputStream, PluginLibraries.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class PluginLibraries {
        private Map<String, String> repositories;
        private List<String> dependencies;

        public Stream<Dependency> asDependencies() {
            return dependencies.stream().map(dep -> new Dependency(new DefaultArtifact(dep), null));
        }

        public Stream<RemoteRepository> asRepositories() {
            return repositories.entrySet().stream().map(entry ->
                    new RemoteRepository.Builder(entry.getKey(), "default", entry.getValue()).build());
        }

        public Map<String, String> getRepositories() {
            return repositories;
        }

        public void setRepositories(Map<String, String> repositories) {
            this.repositories = repositories;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }
    }
}