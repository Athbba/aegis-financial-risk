package com.aegis.operations;

import com.aegis.trading.TradingService.Side;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
public class TradeTelemetry {
  private static final Logger log = LoggerFactory.getLogger(TradeTelemetry.class);
  private final MeterRegistry metrics;

  public TradeTelemetry(MeterRegistry metrics) {
    this.metrics = metrics;
  }

  public Observation begin(Side side) {
    Observation observation = new Observation();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            String outcome =
                status == STATUS_COMMITTED
                    ? observation.replay ? "replayed" : "committed"
                    : observation.rejected ? "rejected" : "rolled_back";
            metrics.counter("aegis.trades", "side", side.name(), "outcome", outcome).increment();
            log.info("financial_transaction side={} outcome={}", side, outcome);
          }
        });
    return observation;
  }

  public static final class Observation {
    private boolean replay, rejected;

    public void replay() {
      replay = true;
    }

    public void rejected() {
      rejected = true;
    }
  }
}
