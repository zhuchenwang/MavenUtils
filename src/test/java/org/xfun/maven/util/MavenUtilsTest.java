package org.xfun.maven.util;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.version.Version;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;


public class MavenUtilsTest {

  @Test
  public void testAllReleaseVersions() throws Exception {
    List<Version> versions = MavenUtils.allVersions("org.apache.maven", "maven-core");
    assertTrue(versions.size() > 0);
    for (Version version : versions) {
      assertFalse(version.toString().contains("SNAPSHOT"));
    }
  }

  @Test
  public void testAllSnapshotVersions() throws Exception {
    List<Version> versions = MavenUtils.allVersions("org.apache.maven", "maven-core", true);
    assertTrue(versions.size() == 0);
  }

  @Test
  public void testResolveArtifact() throws Exception {
    Artifact artifact = MavenUtils.resolveArtifact(new DefaultArtifact("org.apache.maven:maven-core:jar:3.3.9"));
    assertTrue(artifact.getFile().exists());
    assertTrue(artifact.getFile().getName().endsWith(".jar"));
  }

  @Test
  public void testAllDependencies() throws Exception {
    Set<Artifact> dependencies = MavenUtils.allDependencies(new DefaultArtifact("junit:junit:4.12"));
    assertTrue(dependencies.size() > 0);
  }

  @Test
  public void testMavenDependency2AetherDependency() throws Exception {
    org.apache.maven.model.Dependency mavenDependency = new org.apache.maven.model.Dependency();
    mavenDependency.setArtifactId("maven-core");
    mavenDependency.setGroupId("org.apache.maven");
    mavenDependency.setType("jar");
    mavenDependency.setScope("compile");
    org.apache.maven.model.Exclusion mavenExclusion = new org.apache.maven.model.Exclusion();
    mavenExclusion.setArtifactId("log4j");
    mavenExclusion.setGroupId("log4j");
    mavenDependency.addExclusion(mavenExclusion);
    Dependency aetherDependency = MavenUtils.mavenDependency2AetherDependency(mavenDependency);
    assertEquals(mavenDependency.getGroupId(), aetherDependency.getArtifact().getGroupId());
    assertEquals(mavenDependency.getArtifactId(), aetherDependency.getArtifact().getArtifactId());
    assertEquals(mavenDependency.getType(), aetherDependency.getArtifact().getExtension());
    assertEquals(mavenDependency.getScope(), aetherDependency.getScope());
    for (Exclusion exclusion : aetherDependency.getExclusions()) {
      org.apache.maven.model.Exclusion me = mavenDependency.getExclusions().remove(0);
      assertEquals(me.getArtifactId(), exclusion.getArtifactId());
      assertEquals(me.getGroupId(), exclusion.getGroupId());
    }
  }

  @Test
  public void testMavenExclusion2AetherExclusion() throws Exception {
    org.apache.maven.model.Exclusion mavenExclusion = new org.apache.maven.model.Exclusion();
    mavenExclusion.setArtifactId("log4j");
    mavenExclusion.setGroupId("log4j");
    Exclusion aetherExclusion = MavenUtils.mavenExclusion2AetherExclusion(mavenExclusion);
    assertEquals(mavenExclusion.getArtifactId(), aetherExclusion.getArtifactId());
    assertEquals(mavenExclusion.getGroupId(), aetherExclusion.getGroupId());
  }

  @Test
  public void testResolve() throws Exception {
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    dependency.setArtifactId("junit");
    dependency.setGroupId("junit");
    dependency.setScope("test");
    dependency.setVersion("${junit.version}");

    Properties props = new Properties();
    props.put("junit.version", "4.12");

    assertEquals("4.12", MavenUtils.resolve(dependency, props).getVersion());
  }

  @Test
  public void testMerge() throws Exception {
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    dependency.setArtifactId("junit");
    dependency.setGroupId("junit");
    dependency.setScope("test");
    assertNull(dependency.getVersion());

    org.apache.maven.model.Dependency managedDependency = new org.apache.maven.model.Dependency();
    managedDependency.setArtifactId("junit");
    managedDependency.setGroupId("junit");
    managedDependency.setScope("test");
    managedDependency.setVersion("4.12");

    assertEquals("4.12", MavenUtils.merge(dependency, managedDependency).getVersion());
  }
}