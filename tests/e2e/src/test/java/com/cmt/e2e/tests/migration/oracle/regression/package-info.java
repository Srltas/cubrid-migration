/**
 * Oracle source — L4 regression fixtures. Each class pins one tracked
 * bug or feature against future regression. Naming:
 * {@code <Source>To<Target>Bug<JiraId>Test} (Jira id lowercased and
 * underscored, e.g. {@code Bug Cmt 1234} → {@code BugCmt1234}).
 *
 * <p>Scenario ids: {@code <source>_to_<target>__bug_<jira_id>}, e.g.
 * {@code oracle_to_cubrid__bug_cmt_1234}. The Jira ticket key stays in
 * the class javadoc so reviewers see the source ticket directly.
 *
 * <p>Lifecycle is shorter than core: a regression fixture can be
 * deleted once the upstream change makes the bug structurally
 * impossible. ARCHITECTURE.md §12 / §3 (L4).
 */
package com.cmt.e2e.tests.migration.oracle.regression;
