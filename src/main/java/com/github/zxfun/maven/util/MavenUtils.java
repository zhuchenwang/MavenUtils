/*
 * Copyright 2016 Zhuchen Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.zxfun.maven.util;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.*;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MavenUtils {

  private static final Logger logger = LoggerFactory.getLogger(MavenUtils.class);

  private static final Config config;
  private static final RepositorySystem repositorySystem;
  private static final List<RemoteRepository> remoteRepositories;
  private static final List<RemoteRepository> remoteReleaseRepositories;
  private static final List<RemoteRepository> remoteSnapshotRepositories;
  private static final File localRepositoryDir;

  static {
    config = ConfigFactory.load(MavenUtils.class.getClassLoader()).getConfig("maven.util");
    repositorySystem = initRepositorySystem();
    remoteRepositories = initRemoteRepositories();
    localRepositoryDir = new File(config.getString("local-repository"));
    List<RemoteRepository> releaseRepos = new LinkedList<RemoteRepository>();
    List<RemoteRepository> snapshotRepos = new LinkedList<RemoteRepository>();
    for (RemoteRepository repository : remoteRepositories) {
      if (repository.getPolicy(false).isEnabled()) {
        releaseRepos.add(repository);
      }
      if (repository.getPolicy(true).isEnabled()) {
        snapshotRepos.add(repository);
      }
    }
    remoteReleaseRepositories = Collections.unmodifiableList(releaseRepos);
    remoteSnapshotRepositories = Collections.unmodifiableList(snapshotRepos);
  }

  private static RepositorySystem initRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        logger.error("Failed to create service for type {}, impl {}", type, impl, exception);
      }
    });
    return locator.getService(RepositorySystem.class);
  }

  private static List<RemoteRepository> initRemoteRepositories() {
    List<RemoteRepository> repositoryList = new LinkedList<RemoteRepository>();
    Config repositories = config.getConfig("remote-repositories");
    for (Map.Entry<String, ConfigValue> entry : repositories.entrySet()) {
      String key = entry.getKey();
      String url = repositories.getString(key);
      RepositoryPolicy releasePolicy;
      RepositoryPolicy snapshotPolicy;
      if (url.endsWith("snapshots")) {
        releasePolicy = new RepositoryPolicy(false, "never", "");
        snapshotPolicy = new RepositoryPolicy(true, "always", "");
      } else {
        releasePolicy = new RepositoryPolicy(true, "never", "");
        snapshotPolicy = new RepositoryPolicy(false, "always", "");
      }
      repositoryList.add(
          new RemoteRepository.Builder(key, "default", url)
              .setReleasePolicy(releasePolicy)
              .setSnapshotPolicy(snapshotPolicy)
              .build()
      );
    }
    return Collections.unmodifiableList(repositoryList);
  }

  public static RepositorySystemSession newSession() {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepository = new LocalRepository(localRepositoryDir);
    session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepository));
    return session;
  }

  /**
   * Get all versions for the given artifact
   *
   * @param groupId       group id of the artifact
   * @param artifactId    artifact id of the artifact
   * @param versionPrefix version prefix for searching
   * @param snapshot      only snapshot/release versions
   * @return list of versions
   */
  public static List<Version> allVersions(String groupId, String artifactId, String versionPrefix, boolean snapshot) {
    String versionRange = (versionPrefix == null || versionPrefix.length() == 0) ? "[0,)" : "[" + versionPrefix + ".*]";
    Artifact artifact = new DefaultArtifact(groupId + ":" + artifactId + ":" + versionRange);
    VersionRangeRequest versionRangeRequest = new VersionRangeRequest(
        artifact, snapshot ? remoteSnapshotRepositories : remoteReleaseRepositories, ""
    );
    try {
      VersionRangeResult versionRangeResult = repositorySystem.resolveVersionRange(newSession(), versionRangeRequest);
      return Collections.unmodifiableList(versionRangeResult.getVersions());
    } catch (VersionRangeResolutionException e) {
      logger.error("Failed to get all versions for {}, {}, {}", artifact, versionRange, snapshot, e);
      return Collections.emptyList();
    }
  }

  public static List<Version> allVersions(String groupId, String artifactId, String versionPrefix) {
    return allVersions(groupId, artifactId, versionPrefix, false);
  }

  public static List<Version> allVersions(String groupId, String artifactId, boolean snapshot) {
    return allVersions(groupId, artifactId, null, snapshot);
  }

  public static List<Version> allVersions(String groupId, String artifactId) {
    return allVersions(groupId, artifactId, false);
  }

  /**
   * Resolve a given artifact from remote repositories and download it to local repository
   *
   * @param artifact               the given artifact to be resolved
   * @param additionalRepositories additional remote repositories used to resolve the artifact
   * @return resolved artifact
   */
  public static Artifact resolveArtifact(Artifact artifact, List<RemoteRepository> additionalRepositories) {
    List<RemoteRepository> repositories = new LinkedList<RemoteRepository>();
    repositories.add(
        new RemoteRepository.Builder("local", "default", "file://" + localRepositoryDir.getAbsolutePath()).build()
    );
    repositories.addAll(remoteRepositories);
    repositories.addAll(additionalRepositories);
    ArtifactRequest artifactRequest = new ArtifactRequest(artifact, repositories, "");
    Artifact resolved = null;
    try {
      ArtifactResult artifactResult = repositorySystem.resolveArtifact(newSession(), artifactRequest);
      resolved = artifactResult.getArtifact();
    } catch (ArtifactResolutionException e) {
      logger.error("Failed to resolve artifact {}", artifact, e);
    }
    return resolved;
  }

  public static Artifact resolveArtifact(Artifact artifact) {
    return resolveArtifact(artifact, Collections.<RemoteRepository>emptyList());
  }

  /**
   * Get all transitive dependencies for given artifact including itself
   *
   * @param artifact               the root artifact
   * @param managedDependencies    managed dependencies used to resolve transitive dependencies
   * @param dependencyFilter       filter out transitive dependencies meet the requirement
   * @param additionalRepositories additional repositories used to resolve transitive dependencies
   * @return all transitive dependencies for the given artifact
   */
  public static Set<Artifact> allDependencies(Artifact artifact,
                                              List<Dependency> managedDependencies,
                                              DependencyFilter dependencyFilter,
                                              List<RemoteRepository> additionalRepositories) {
    List<RemoteRepository> repositories = new LinkedList<RemoteRepository>();
    repositories.add(
        new RemoteRepository.Builder("local", "default", "file://" + localRepositoryDir.getAbsolutePath()).build()
    );
    repositories.addAll(remoteRepositories);
    repositories.addAll(additionalRepositories);

    CollectRequest collectRequest = new CollectRequest(new Dependency(artifact, ""), repositories);
    collectRequest.setManagedDependencies(managedDependencies);
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, dependencyFilter);
    Set<Artifact> results = new HashSet<Artifact>();
    try {
      DependencyResult dependencyResult = repositorySystem.resolveDependencies(newSession(), dependencyRequest);
      for (ArtifactResult result : dependencyResult.getArtifactResults()) {
        results.add(result.getArtifact());
      }
    } catch (DependencyResolutionException e) {
      logger.error("Failed to resolve transitive dependencies for {}", artifact, e);
    }
    return Collections.unmodifiableSet(results);
  }

  public static Set<Artifact> allDependencies(Artifact artifact,
                                               List<Dependency> managedDependencies,
                                               DependencyFilter dependencyFilter) {
    return allDependencies(artifact, managedDependencies, dependencyFilter, Collections.<RemoteRepository>emptyList());
  }

  public static Set<Artifact> allDependencies(Artifact artifact, List<Dependency> managedDependencies) {
    return allDependencies(artifact, managedDependencies, new EmptyDependencyFilter());
  }

  public static Set<Artifact> allDependencies(Artifact artifact, DependencyFilter dependencyFilter) {
    return allDependencies(artifact, Collections.<Dependency>emptyList(), dependencyFilter);
  }

  public static Set<Artifact> allDependencies(Artifact artifact) {
    return allDependencies(artifact, Collections.<Dependency>emptyList());
  }

  /**
   * Resolve the maven model object using the given properties
   *
   * @param obj the maven model object
   * @param properties maven properties
   * @param <T> object type
   * @return resolved object
   */
  @SuppressWarnings("unchecked")
  public static <T> T resolve(T obj, Properties properties) {
    Pattern pattern = Pattern.compile("^\\$\\{(.+)\\}$");
    Class<?> clazz = obj.getClass();
    try {
      T result = (T) clazz.getMethod("clone").invoke(obj);
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().startsWith("get") && method.getParameterTypes().length == 0 && method.getReturnType() == String.class) {
          String value = (String) method.invoke(obj);
          if (value == null) continue;
          Matcher m = pattern.matcher(value);
          if (m.find()) {
            String placeholder = m.group(1);
            String resolvedValue = properties.getProperty(placeholder);
            if (resolvedValue != null) {
              try {
                Method setter = clazz.getDeclaredMethod(method.getName().replaceFirst("get", "set"), method.getReturnType());
                setter.invoke(result, resolvedValue);
              } catch (Exception ignored) {}
            }
          }
        }
      }
      return result;
    } catch (Exception e) {
      logger.error("Failed to resolve {}", obj, e);
      return obj;
    }
  }

  /**
   * Merge 2 maven model object with same type (Dependency merge with Managed Dependency).
   * If some value is null in base object, set it using the same value in target
   *
   * @param base base object
   * @param target target object
   * @param <T> object type
   * @return merged object
   */
  @SuppressWarnings("unchecked")
  public static <T> T merge(T base, T target) {
    Class<?> clazz = base.getClass();
    try {
      T result = (T) clazz.getMethod("clone").invoke(base);
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().startsWith("get") && method.getParameterTypes().length == 0) {
          Object value = method.invoke(target);
          if (value != null) {
            try {
              Method setter = clazz.getDeclaredMethod(method.getName().replaceFirst("get", "set"), method.getReturnType());
              setter.invoke(result, value);
            } catch (Exception ignored) {}
          }
        }
      }
      return result;
    } catch (Exception e) {
      e.printStackTrace();
      return base;
    }
  }

  /**
   * Convert {@link org.apache.maven.model.Dependency} to {@link Dependency}
   *
   * @param dependency {@link org.apache.maven.model.Dependency}
   * @return {@link Dependency}
   */
  public static Dependency mavenDependency2AetherDependency(org.apache.maven.model.Dependency dependency) {
    ArtifactTypeRegistry registry = newSession().getArtifactTypeRegistry();
    ArtifactType artifactType = registry.get(dependency.getType());
    if (artifactType == null) {
      artifactType = new DefaultArtifactType(dependency.getType());
    }
    Map<String, String> props = new HashMap<String, String>();
    String systemPath = dependency.getSystemPath();
    if (systemPath != null) {
      props.put(ArtifactProperties.LOCAL_PATH, systemPath);
    }
    Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
        dependency.getClassifier(), dependency.getType(), dependency.getVersion(), props, artifactType);
    List<Exclusion> exclusions = new LinkedList<Exclusion>();
    for (org.apache.maven.model.Exclusion exclusion : dependency.getExclusions()) {
      exclusions.add(mavenExclusion2AetherExclusion(exclusion));
    }
    return new Dependency(artifact, dependency.getScope(), dependency.isOptional(), exclusions);
  }

  /**
   * Convert {@link org.apache.maven.model.Exclusion} to {@link Exclusion}
   *
   * @param exclusion {@link org.apache.maven.model.Exclusion}
   * @return {@link Exclusion}
   */
  public static Exclusion mavenExclusion2AetherExclusion(org.apache.maven.model.Exclusion exclusion) {
    return new Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*");
  }

  public static class EmptyDependencyFilter implements DependencyFilter {
    @Override
    public boolean accept(DependencyNode node, List<DependencyNode> parents) {
      return true;
    }
  }

}
