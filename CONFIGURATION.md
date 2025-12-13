# Configuration Guide

The Jetty-based GitProxy server supports configuration via YAML files. This allows you to configure providers, filters, and other server settings without modifying code.

## Configuration Files

The server loads configuration from the following files in order:
1. `src/main/resources/application.yml` - Base configuration
2. `src/main/resources/application-local.yml` - Local overrides (merged with base)

Configuration from `application-local.yml` will override or extend settings from `application.yml`.

## Server Configuration

```yaml
server:
  port: 8080  # HTTP port for the server
```

## Provider Configuration

Providers define the Git hosting services that the proxy will forward requests to.

### Built-in Providers

The following providers are built-in:
- `github` - GitHub (https://github.com)
- `gitlab` - GitLab (https://gitlab.com)
- `bitbucket` - Bitbucket (https://bitbucket.org)

### Example Provider Configuration

```yaml
git-proxy:
  base-path: "/git"  # Optional base path for all servlets
  providers:
    github:
      enabled: true
    gitlab:
      enabled: true
      servlet-path: /custom-gitlab  # Optional custom path
    bitbucket:
      enabled: false
    # Custom provider example
    internal-github:
      enabled: true
      servlet-path: /enterprise-github
      uri: https://githubserver.example.com
```

### Provider Options

- `enabled` (boolean): Enable/disable the provider
- `uri` (string): Custom URI for the provider (for self-hosted instances)
- `servlet-path` (string): Custom servlet path (default is based on provider name)
- `log-proxy` (boolean): Enable proxy logging (default: true)
- `connect-timeout` (int): Connection timeout in milliseconds (default: -1, no timeout)
- `read-timeout` (int): Read timeout in milliseconds (default: -1, no timeout)

## Filter Configuration

Filters control access to repositories and enforce policies.

### GitHub User Authentication Filter

Requires authentication for GitHub operations.

```yaml
git-proxy:
  filters:
    github-user-authenticated:
      enabled: true
      order: 1
      operations:
        - PUSH
      required-auth-schemes: bearer, token, basic  # Can be comma-separated or list
      providers:
        - github
```

Options:
- `enabled` (boolean): Enable/disable the filter
- `order` (int): Filter execution order (lower numbers run first)
- `operations` (list): Git operations to apply filter to (PUSH, FETCH)
- `required-auth-schemes` (string or list): Required authentication schemes (bearer, token, basic)
- `providers` (list): Provider names to apply filter to

### Whitelist Filters

Control which repositories can be accessed.

```yaml
git-proxy:
  filters:
    whitelists:
      - enabled: true
        order: 5
        operations:
          - FETCH
          - PUSH
        providers:
          - github
        slugs:
          - coopernetes/test-repo
          - finos/git-proxy
      - enabled: true
        order: 10
        operations:
          - PUSH
        providers:
          - gitlab
        owners:
          - finosfoundation
      - enabled: true
        order: 20
        operations:
          - FETCH
        providers:
          - github
        names:
          - hello-world
```

Options:
- `enabled` (boolean): Enable/disable the filter
- `order` (int): Filter execution order
- `operations` (list): Git operations to apply filter to (PUSH, FETCH)
- `providers` (list): Provider names to apply filter to
- `slugs` (list): Repository slugs (owner/repo) to whitelist
- `owners` (list): Repository owners to whitelist
- `names` (list): Repository names to whitelist

Note: You can use `slugs`, `owners`, and `names` together in a single whitelist entry, or separately for more granular control.

## Running the Server

```bash
# Build the application
./gradlew build

# Run the application
./gradlew run

# Or run the JAR directly
java -jar build/libs/jgit-proxy-*.jar
```

The server will read configuration from the YAML files and start with the configured providers and filters.

## Example Usage

Once the server is running, you can use it as a proxy for Git operations:

```bash
# Clone via proxy
git clone http://localhost:8080/github.com/finos/git-proxy.git

# Clone from GitLab via proxy
git clone http://localhost:8080/gitlab.com/coopernetes/test-repo.git

# Clone from custom provider
git clone http://localhost:8080/debian/salsa/test-repo.git
```

## Logging

The application uses SLF4J with Logback. You can configure logging levels in the YAML files:

```yaml
logging:
  level:
    org.finos.gitproxy: DEBUG
    org.finos.gitproxy.git: DEBUG
```
