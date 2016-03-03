# MavenUtils

[![Build Status](https://travis-ci.org/zxfun/MavenUtils.svg?branch=master)](https://travis-ci.org/zxfun/MavenUtils)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/bf469dd8b0a9459ba6c6bdb888dbcbb5)](https://www.codacy.com/app/zcx-wang/MavenUtils)

Utility library for analyzing Maven dependencies programmatically

## Usage

Add the following repository to your build system
```
https://oss.sonatype.org/content/repositories/snapshots/

```

Then add the dependency
```
com.github.zxfun:maven-utils:0.1.0-SNAPSHOT

```

## Configuration

[HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) is used as the configuration syntax.

The library contains following default configuration in its `reference.conf` file

```
maven.util {
  local-repository = "maven-repo"

  remote-repositories = {
    "maven_central" = "http://repo1.maven.org/maven2"
  }
}

```

To add other repositories and change local repository, put a application.conf file in the resource directory.
```
maven.util {
  local-repository = "some other local repository folder"

  remote-repositories = {
    "some_other_repo" = "some url"
  }
}

```

___To override the url for default repository, just use the same name and replace the url.___

## Methods

```java

// get all available release versions from configured repositories for given artifact
MavenUtils.allVersions("groupId", "artifactId");

// get all available snapshot versions from configured repositories for given artifact
MavenUtils.allVersions("groupId", "artifactId", true);

// get all available release micro versions in 2.2.X from configured repositories for given artifact
MavenUtils.allVersions("groupId", "artifactId", "2.2");

// resolve an artifact and download the artifact to local repository
MavenUtils.resolveArtifact(new DefaultArtifact("groupId:artifactId:type:version"));

// get all direct and transitive dependencies for an artifact including itself
MavenUtils.allDependencies(new DefaultArtifact("groupId:artifactId:version"));

```
