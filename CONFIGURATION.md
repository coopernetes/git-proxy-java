# Configuration Guide

The Jetty-based GitProxy server supports configuration via YAML files and environment variables. This allows you to configure providers, filters, and other server settings without modifying code.

## Configuration Files

The server loads configuration from the following files in order:
1. `src/main/resources/git-proxy.yml` - Base configuration
2. `src/main/resources/git-proxy-local.yml` - Local overrides (merged with base)
3. Environment variables with `GITPROXY_` prefix

Configuration from `git-proxy-local.yml` will override or extend settings from `git-proxy.yml`, and environment variables will override both.

## Environment Variable Overrides

You can override certain configuration values using environment variables with the `GITPROXY_` prefix:

- `GITPROXY_SERVER_PORT`: Override the server port (e.g., `GITPROXY_SERVER_PORT=9090`)
- `GITPROXY_GITPROXY_BASEPATH`: Override the base path (e.g., `GITPROXY_GITPROXY_BASEPATH=/proxy`)

Note: Whitelist configurations are not supported via environment variables due to their complex structure.

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

Note: GitHub already enforces authentication for push operations using personal access tokens (PATs). The proxy transparently forwards requests upstream and returns errors from GitHub directly, so authentication checking is handled by GitHub itself.

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
