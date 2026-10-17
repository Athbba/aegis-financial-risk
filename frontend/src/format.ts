// Number conversions below are for display only. The server performs all accounting with BigDecimal.
export const usd = (value: string) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    Number(value),
  );
export const priceUsd = (value: string) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 4,
    maximumFractionDigits: 4,
  }).format(Number(value));
export const qty = (value: string) =>
  new Intl.NumberFormat("en-US", { maximumFractionDigits: 6 }).format(
    Number(value),
  );
export const time = (value: string) =>
  new Date(value).toLocaleString("en-GB", {
    timeZone: "UTC",
    dateStyle: "medium",
    timeStyle: "short",
  }) + " UTC";
export const message = (error: unknown) =>
  error instanceof Error ? error.message : "Something went wrong.";
