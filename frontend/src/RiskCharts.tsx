import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { Exposure, Returns } from "./riskTypes";
import { usd } from "./format";
const colors = ["#8bd3b7", "#91b7e5", "#b6a2d8", "#d7b880"];
export function ExposureChart({ assets }: { assets: Exposure[] }) {
  const rows = assets
    .filter((a) => a.marketValue !== null)
    .map((a) => ({ name: a.symbol, value: Number(a.marketValue) }));
  return (
    <figure aria-label="Asset and cash exposure chart">
      <ResponsiveContainer width="100%" height={220} minWidth={0}>
        <PieChart accessibilityLayer>
          <Pie
            data={rows}
            dataKey="value"
            nameKey="name"
            innerRadius={60}
            outerRadius={90}
            stroke="#111824"
            isAnimationActive={false}
          >
            {rows.map((r, i) => (
              <Cell key={r.name} fill={colors[i % colors.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(v) => usd(String(v))}
            contentStyle={{
              background: "#111824",
              borderColor: "#455368",
              color: "#e4e8ef",
            }}
          />
        </PieChart>
      </ResponsiveContainer>
      <figcaption className="help">
        Marked USD exposures · cash included. Values and weights listed below.
      </figcaption>
    </figure>
  );
}
export function DistributionChart({ returns }: { returns: Returns }) {
  if (!returns.points.length)
    return (
      <div className="chart-empty">
        <h3>No empirical distribution</h3>
        <p className="help">
          {returns.status === "CASH_ONLY"
            ? "Cash has no asset-price exposure. Zero risk is analytical; no observations are invented."
            : "Choose a complete historical window with price observations."}
        </p>
      </div>
    );
  const values = returns.points.map((p) => Number(p.returnPercent));
  const min = Math.min(...values),
    max = Math.max(...values),
    width = max === min ? 0.01 : (max - min) / 20;
  const rows = Array.from({ length: max === min ? 1 : 20 }, (_, i) => ({
    name: `${(min + i * width).toFixed(2)} to ${(min + (i + 1) * width).toFixed(2)}%`,
    count: 0,
  }));
  for (const v of values)
    rows[Math.min(rows.length - 1, Math.floor((v - min) / width))].count++;
  return (
    <figure aria-label="Historical shock return distribution">
      <ResponsiveContainer width="100%" height={240} minWidth={0}>
        <BarChart
          data={rows}
          accessibilityLayer
          margin={{ left: 0, right: 10, bottom: 10 }}
        >
          <CartesianGrid vertical={false} stroke="#273141" />
          <XAxis
            dataKey="name"
            tick={{ fill: "#a3afc2", fontSize: 10 }}
            interval="preserveStartEnd"
          />
          <YAxis
            allowDecimals={false}
            width={35}
            tick={{ fill: "#a3afc2", fontSize: 10 }}
          />
          <Tooltip
            contentStyle={{
              background: "#111824",
              borderColor: "#455368",
              color: "#e4e8ef",
            }}
          />
          <Bar
            dataKey="count"
            name="Observations"
            fill="#91b7e5"
            radius={[3, 3, 0, 0]}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
      <figcaption className="help">
        Daily hypothetical return (% of selected equity). Focus and use arrow
        keys; exact observations are available below.
      </figcaption>
    </figure>
  );
}
export function GainLossChart({
  rows,
  label,
}: {
  rows: { name: string; value: string }[];
  label: string;
}) {
  return (
    <figure aria-label={label}>
      <ResponsiveContainer
        width="100%"
        height={Math.max(210, rows.length * 38)}
        minWidth={0}
      >
        <BarChart
          data={rows.map((r) => ({ ...r, value: Number(r.value) }))}
          layout="vertical"
          accessibilityLayer
          margin={{ left: 5, right: 25 }}
        >
          <CartesianGrid horizontal={false} stroke="#273141" />
          <XAxis
            type="number"
            tick={{ fill: "#a3afc2", fontSize: 10 }}
            tickFormatter={(v) => usd(String(v))}
          />
          <YAxis
            type="category"
            dataKey="name"
            width={110}
            tick={{ fill: "#a3afc2", fontSize: 10 }}
          />
          <Tooltip
            formatter={(v) => usd(String(v))}
            contentStyle={{
              background: "#111824",
              borderColor: "#455368",
              color: "#e4e8ef",
            }}
          />
          <Bar
            dataKey="value"
            name="Hypothetical gain / loss"
            isAnimationActive={false}
          >
            {rows.map((r, i) => (
              <Cell
                key={i}
                fill={Number(r.value) < 0 ? "#f5a7a9" : "#8bd3b7"}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <figcaption className="help">
        Signed hypothetical gain / loss in USD. Cash contributes $0.00.
      </figcaption>
    </figure>
  );
}
