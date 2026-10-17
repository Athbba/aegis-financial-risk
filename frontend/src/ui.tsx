import type React from "react";

export function Brand() {
  return (
    <a href="/" className="brand" aria-label="AEGIS home">
      <span className="brand-mark">Λ</span>
      <span>
        AEGIS<small>INVESTMENT LAB</small>
      </span>
    </a>
  );
}

export function Notice({
  children,
  error = false,
}: {
  children: React.ReactNode;
  error?: boolean;
}) {
  return (
    <div
      className={`notice ${error ? "error" : ""}`}
      role={error ? "alert" : "status"}
    >
      {children}
    </div>
  );
}

export function Disclaimer() {
  return (
    <p className="disclaimer">
      <span>SIMULATION ONLY</span> Educational software. All assets and prices
      are deterministic synthetic data. No real trades or investment advice.
    </p>
  );
}

export function Pagination({
  page,
  size,
  total,
  onPage,
}: {
  page: number;
  size: number;
  total: number;
  onPage: (n: number) => void;
}) {
  if (total <= size) return null;
  return (
    <div className="pagination">
      <button
        className="subtle"
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
      >
        Previous
      </button>
      <span>
        Page {page + 1} of {Math.ceil(total / size)}
      </span>
      <button
        className="subtle"
        disabled={(page + 1) * size >= total}
        onClick={() => onPage(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
