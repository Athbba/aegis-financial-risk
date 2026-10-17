package com.aegis.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Atomic fixed-window counters survive application restarts; no raw identities are stored. */
@Component
public class AttemptLimiter {
  public static final class Limited extends RuntimeException {
    private final long retryAfter;

    public Limited(long retryAfter) {
      super("Too many attempts. Try again later.");
      this.retryAfter = retryAfter;
    }

    public long retryAfter() {
      return retryAfter;
    }
  }

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public AttemptLimiter(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public void acquire(String scope, String identity, int limit, Duration window) {
    Instant now = clock.instant();
    // Bounded cleanup, using the expiry index. This is not financial history.
    jdbc.update(
        "delete from auth_attempt_window where bucket_key in (select bucket_key from"
            + " auth_attempt_window where expires_at<=? order by expires_at limit 1000)",
        java.sql.Timestamp.from(now));
    var result =
        jdbc.queryForObject(
            """
            insert into auth_attempt_window(bucket_key,attempts,expires_at) values(?,1,?)
            on conflict(bucket_key) do update set
              attempts=case when auth_attempt_window.expires_at<=? then 1 else least(auth_attempt_window.attempts+1,?) end,
              expires_at=case when auth_attempt_window.expires_at<=? then excluded.expires_at else auth_attempt_window.expires_at end
            returning attempts,expires_at
            """,
            (rs, n) -> new Window(rs.getInt(1), rs.getTimestamp(2).toInstant()),
            key(scope, identity),
            java.sql.Timestamp.from(now.plus(window)),
            java.sql.Timestamp.from(now),
            limit + 1,
            java.sql.Timestamp.from(now));
    if (result.attempts() > limit)
      throw new Limited(Math.max(1, Duration.between(now, result.expires()).toSeconds() + 1));
  }

  public void clear(String scope, String identity) {
    jdbc.update("delete from auth_attempt_window where bucket_key=?", key(scope, identity));
  }

  private record Window(int attempts, Instant expires) {}

  private static String key(String scope, String identity) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((scope + ":" + identity).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
