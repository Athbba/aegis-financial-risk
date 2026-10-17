# Phase 4 reproducible engineering measurements

Actual run: 2026-10-17. Raw measurements/environment: [phase4-local.json](benchmarks/phase4-local.json). Test harness: `backend/src/test/java/com/aegis/EngineeringBenchmark.java` (explicit opt-in, excluded by the normal `*Test` naming convention).

```bash
./scripts/cloud-maven.sh -B -ntp -Dtest=EngineeringBenchmark test
# Portable alternative: mvn -B -ntp -f backend/pom.xml -Dtest=EngineeringBenchmark test
```

The harness starts a disposable PostgreSQL 17 container; it does not use the Compose database. It measures in-process MockMvc requests including security/ownership, MVC validation, application transactions/SQL and JSON serialization. It excludes wire/Nginx latency and password verification. All measured requests assert a successful status. Execution is serial, with 10 warmups per operation; no load/concurrency or peak-throughput claims.

Environment: cloud container, 2 available processors, AMD EPYC 9V74 host model, amd64, approximately 9.7 GiB reported machine RAM, 2 GiB test JVM maximum heap, Temurin 21.0.12.1. Other host tenancy and scheduling are uncontrolled. Inputs: 3 fictional assets, 3,129 persisted closes, 300 initial BUY records plus 110 warmup/measured BUY and 110 partial FIFO SELL records; 730 inclusive calendar days / 521 aligned shocks; 8 stress scenarios; 20-row history pages. The creation benchmark generates 50 additional isolated portfolios. No private financial data appears in this report.

| Operation | Samples | Min ms | p50 ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| create portfolio | 40 | 5.195 | 6.735 | 10.466 | 26.970 |
| trade committed buy | 100 | 7.533 | 11.417 | 21.920 | 33.276 |
| trade committed FIFO sell | 100 | 6.978 | 9.882 | 25.521 | 57.561 |
| historical valuation | 100 | 4.036 | 6.332 | 14.152 | 22.379 |
| historical performance 730 days | 50 | 8.460 | 11.338 | 29.335 | 45.141 |
| risk summary 730 days | 50 | 11.530 | 15.903 | 43.372 | 71.060 |
| stress 8 scenarios | 100 | 3.708 | 5.790 | 11.233 | 24.417 |
| history page 20 | 100 | 1.961 | 2.580 | 4.693 | 16.075 |

Percentiles use nearest rank on measured durations; timings use monotonic `System.nanoTime`. These are observed samples, not a latency SLO or capacity promise. Risk's larger response and chronological calculation take more time than a short history page. The observed dataset shows no bottleneck warranting caching, new indexes or a financial-engine rewrite. Outliers can include JIT/GC, serialization, SQL and shared-host scheduling; this run cannot attribute each outlier to a specific cause.

Existing supported limits remain: 10,000 historical executions, 100 traded assets, 1,461 calendar days, 100-row history pages, eight stress scenarios and 15-second analytics/trading transactions. The benchmark exercises a realistic smaller workload, not every limit at once; successful unit/integration boundary tests are not full-capacity performance certification. Authentication has separate attempt limits and is deliberately excluded from these request timings. Repeat with deployment hardware/data and concurrent workloads before setting operational capacity targets. Financial correctness and all previous numerical tests remain required.
