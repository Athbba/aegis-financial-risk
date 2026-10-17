# Phase 3 dependency and secret checks

**Historical Phase 3 evidence.** Phase 4 resolves these Spring findings with Framework 7.0.9 and expands dependency/image checks. See [the current security review](PHASE4_SECURITY_REVIEW.md). The official 6.2.20 fix is enterprise-only; the prior availability discussion refers to public releases.

Checked 2026-10-17. This records actual checks, not a production-security certification. Phase 4 has not begun. No new application dependencies were added for the risk engine or charts.

## Dependency evidence

- `npm --prefix frontend audit --json`: **0 reported vulnerabilities** in the locked frontend dependency tree (199 total including development/optional dependencies).
- Maven `dependency:list` runtime inventory: **87 resolved Java coordinates** queried against GitHub's reviewed global Maven advisory index using exact `package@version`. All 87 queries succeeded, with no pagination truncation. Reproduction helper: `scripts/check-java-advisories.py`. The final inventory/result is [phase3-java-audit.json](phase3-java-audit.json).
- The initial scan identified seven affected inherited packages. Compatible fixes are explicitly pinned through Spring Boot dependency properties: Jackson BOM **2.21.7**, Tomcat **10.1.59**, PostgreSQL JDBC **42.7.12**, Log4j **2.25.5**, Commons Lang **3.18.0**. The complete backend and browser suites were rerun after these changes. The Jackson BOM and Tomcat properties keep related artifacts aligned.
- Final scan: **one affected package, two remaining advisories**, both Spring MVC **6.2.19**. Do not describe the Java dependency scan as clean. The helper intentionally exits 1 on findings (2 on incomplete scan), while writing its report.

| Remaining advisory | Reported severity | Affected feature / current application evidence |
| --- | --- | --- |
| [GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8) | Critical | Stream corruption when using SSE with rendered view fragments. AEGIS has JSON REST controllers; no SSE/fragment rendering implementation was found in backend source. |
| [GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg) | Critical | XsltView with an implicit view name and wildcard `/**` view-rendering mapping. No XSLT view or view-rendering controller was found; the React SPA is served separately by Nginx. |

The advisory index lists **7.0.9** as patched in the Spring 7 line and no patched Spring 6.2 release. Maven Central's latest 6.2 release at check time was 6.2.19, already managed by the existing Spring Boot 3.5.16 baseline. Moving the platform to Spring 7/Boot 4 would be a framework migration with additional compatibility/testing scope; it is not performed in Phase 3. The affected-feature review narrows apparent exposure but does not remove the dependency advisories or prove exploitability is impossible. Revisit before adding server views/SSE or considering deployment. Existing authentication/session/CSRF/ownership tests continue to pass.

GitHub's repository Dependabot alert API returned 403 with **“Dependabot alerts are disabled for this repository.”** The global reviewed-advisory checks above were completed independently; repository alerts were not claimed to be enabled. The global index is a known-advisory check, not a source audit. Test-only dependencies, Maven plugin internals, container OS packages and independent penetration testing are outside this scan. TLS verification and the configured egress proxy were preserved.

Reproduce (authenticated `gh`, Python 3, JDK/Maven):

```bash
./scripts/cloud-maven.sh -B -ntp dependency:list \
  -DincludeScope=runtime -DoutputFile=/tmp/aegis-maven-deps.txt
python scripts/check-java-advisories.py /tmp/aegis-maven-deps.txt \
  --output /tmp/aegis-java-audit.json
# Expected current exit 1: two documented advisories, no query errors.
npm --prefix frontend audit
```

## Secret scanning evidence

Gitleaks **8.30.1**, downloaded from the official release and SHA-256 verified against its published checksum, scanned the locally available Git history and an export of all tracked/new nonignored source files. Build outputs, the ignored local `.env`, credentials and database backups were excluded from the source export. Reports were redacted and stayed outside the repository. **No leaks found.** Available history was five commits at the first scan; the final scan includes the Phase 3 delivery commits. No secrets were added to source, documentation or report artifacts.

Reproduce using an installed Gitleaks:

```bash
gitleaks git --redact --no-banner
# To include pending changes, export git ls-files --cached --others --exclude-standard
# to a separate temporary directory and run gitleaks dir on that export.
```

Local PostgreSQL volumes and matching ignored credentials were retained. Browser tests create isolated simulated accounts and do not delete preexisting portfolios. Stress tests verify full financial row content remains unchanged after accepted and rejected simulations. This does not claim an independent audit, public deployment suitability or production readiness.
