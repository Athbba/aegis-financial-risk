package com.aegis.operations;

import com.aegis.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReconciliationController {
  private final CurrentUser auth;
  private final ReconciliationService reconciliation;

  public ReconciliationController(CurrentUser auth, ReconciliationService reconciliation) {
    this.auth = auth;
    this.reconciliation = reconciliation;
  }

  @GetMapping("/api/portfolios/{id}/reconciliation")
  ReconciliationService.Report reconcile(Authentication user, @PathVariable UUID id) {
    return reconciliation.reconcile(auth.id(user), id);
  }
}
