export type OrderIntent = {
  key: string;
  request: {
    symbol: string;
    side: "BUY" | "SELL";
    quantity: string;
    priceDate: string;
  };
};
const slot = (portfolio: string) => `aegis.pending-order.${portfolio}`;
// This is a tab-local recovery journal, not an authentication credential or an accounting ledger.
export function loadIntent(portfolio: string): OrderIntent | null {
  const stored = sessionStorage.getItem(slot(portfolio));
  if (!stored) return null;
  const value = JSON.parse(stored) as OrderIntent;
  if (
    !value ||
    typeof value.key !== "string" ||
    !/^[A-Za-z0-9_-]{8,80}$/.test(value.key) ||
    !value.request ||
    !["BUY", "SELL"].includes(value.request.side) ||
    typeof value.request.symbol !== "string" ||
    typeof value.request.quantity !== "string" ||
    typeof value.request.priceDate !== "string"
  )
    throw new Error(
      "Saved order intent could not be read. Review transaction history before submitting another order.",
    );
  return value;
}
export function saveIntent(portfolio: string, intent: OrderIntent) {
  sessionStorage.setItem(slot(portfolio), JSON.stringify(intent));
}
export function clearIntent(portfolio: string) {
  sessionStorage.removeItem(slot(portfolio));
}
