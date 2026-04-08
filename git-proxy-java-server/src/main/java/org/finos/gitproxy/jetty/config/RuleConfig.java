package org.finos.gitproxy.jetty.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Binds a single entry under {@code rules.allow[]} (or {@code rules.deny[]}) in git-proxy.yml. */
@Data
public class RuleConfig {

    private boolean enabled = true;
    private int order = 1100;

    /** Git operations this entry matches: {@code FETCH}, {@code PUSH}. Empty = none. */
    private List<String> operations = new ArrayList<>();

    /** Provider names to scope this entry to. Empty = all providers. */
    private List<String> providers = new ArrayList<>();

    /**
     * Repository slugs ({@code owner/repo}). Supports glob patterns and {@code regex:}-prefixed Java regular
     * expressions.
     */
    private List<String> slugs = new ArrayList<>();

    /** Repository owner/org names. Supports glob patterns and {@code regex:}-prefixed Java regular expressions. */
    private List<String> owners = new ArrayList<>();

    /** Repository names. Supports glob patterns and {@code regex:}-prefixed Java regular expressions. */
    private List<String> names = new ArrayList<>();
}
