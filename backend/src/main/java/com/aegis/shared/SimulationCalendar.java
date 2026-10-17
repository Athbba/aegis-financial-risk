package com.aegis.shared;

import java.time.LocalDate;

/** Version-one synthetic simulation horizon; distinct from account creation and wall-clock time. */
public final class SimulationCalendar {
  public static final LocalDate START = LocalDate.of(2022, 1, 3);
  public static final LocalDate END = LocalDate.of(2025, 12, 31);

  private SimulationCalendar() {}
}
