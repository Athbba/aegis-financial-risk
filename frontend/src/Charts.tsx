import {
  CartesianGrid,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Cell,
  Legend,
} from "recharts";
import type { Allocation, ValuePoint, ValueSeries } from "./analyticsTypes";
import { usd } from "./format";

const colors = ["#d7b880", "#8bd3b7", "#91b7e5", "#b6a2d8", "#d69a85"];
const knownColors: Record<string, string> = {
  CASH: colors[0],
  ALTA: colors[1],
  BRIM: colors[2],
  COVE: colors[3],
};
const componentColor = (symbol: string, index: number) =>
  knownColors[symbol] ?? colors[index % colors.length];
const shortDate = (value: string) =>
  new Date(value + "T12:00:00Z").toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
type ChartRow = {
  date: string;
  value: number | null;
  capital: number;
  original: ValuePoint;
};
function ValueTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: readonly { payload?: ChartRow }[];
}) {
  const row = payload?.[0]?.payload;
  if (!active || !row) return null;
  const point = row.original;
  return (
    <div className="chart-tooltip">
      <strong>{point.date} · synthetic close</strong>
      <div>
        Portfolio value{" "}
        <b>
          {point.totalValue === null ? "Unavailable" : usd(point.totalValue)}
        </b>
      </div>
      <div>
        Cash <b>{usd(point.cash)}</b>
      </div>
      <div>
        Total P/L{" "}
        <b>{point.totalPnl === null ? "Unavailable" : usd(point.totalPnl)}</b>
      </div>
      {point.status === "MISSING_PRICES" && (
        <p>Missing exact close: {point.missingSymbols.join(", ")}</p>
      )}
    </div>
  );
}
export function PerformanceChart({ series }: { series: ValueSeries }) {
  // Conversion is only for SVG coordinates; all financial values arrive calculated as decimal strings.
  const rows: ChartRow[] = series.points.map((point) => ({
    date: point.date,
    value: point.totalValue === null ? null : Number(point.totalValue),
    capital: Number(series.initialCapital),
    original: point,
  }));
  if (series.completeDays < 2)
    return (
      <div className="chart-empty" role="status">
        <h3>
          {series.completeDays === 0
            ? "No valued observations in this range"
            : "One valued observation"}
        </h3>
        <p>
          {series.completeDays === 0
            ? "The required closing prices are missing. Review the dated records below or choose another range."
            : "Choose a longer range with at least two complete observations to compare values."}
        </p>
      </div>
    );
  return (
    <figure
      className="equity-chart"
      aria-label="Historical portfolio value chart"
    >
      <figcaption className="sr-only">
        Backend-calculated end-of-day USD value and initial deposited capital.
        Gaps mean missing prices. Focus the chart and use arrow keys, or open
        the dated data table below.
      </figcaption>
      <ResponsiveContainer width="100%" height={300} minWidth={0}>
        <LineChart
          data={rows}
          accessibilityLayer
          margin={{ top: 10, right: 18, left: 0, bottom: 0 }}
        >
          <CartesianGrid
            stroke="#273141"
            strokeDasharray="3 5"
            vertical={false}
          />
          <XAxis
            dataKey="date"
            tickFormatter={shortDate}
            tick={{ fill: "#a3afc2", fontSize: 10 }}
            minTickGap={55}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            width={92}
            domain={["auto", "auto"]}
            tick={{ fill: "#a3afc2", fontSize: 10 }}
            tickFormatter={(v) => usd(String(v))}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<ValueTooltip />} filterNull={false} />
          <Legend wrapperStyle={{ fontSize: 11, paddingTop: 16 }} />
          <Line
            type="linear"
            name="Initial deposited capital"
            dataKey="capital"
            stroke="#8a94a8"
            strokeDasharray="5 5"
            strokeWidth={1}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="linear"
            name="Portfolio value"
            dataKey="value"
            stroke="#d7b880"
            strokeWidth={2}
            dot={rows.length < 20 ? { r: 3 } : false}
            activeDot={{ r: 5 }}
            connectNulls={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </figure>
  );
}
export function AllocationChart({ allocation }: { allocation: Allocation }) {
  if (allocation.status !== "COMPLETE")
    return (
      <div className="chart-empty">
        <h3>Allocation unavailable</h3>
        <p>
          {allocation.status === "MISSING_PRICES"
            ? `Missing closing prices: ${allocation.missingSymbols.join(", ")}. We do not calculate weights from partial values.`
            : "Portfolio value is zero, so allocation percentages are undefined."}
        </p>
      </div>
    );
  const rows = allocation.items
    .map((item, index) => ({
      ...item,
      value: item.marketValue === null ? null : Number(item.marketValue),
      color: componentColor(item.symbol, index),
    }))
    .filter((item) => item.value !== null && item.value > 0);
  return (
    <>
      <div className="allocation-chart" aria-hidden="true">
        <ResponsiveContainer width="100%" height={210} minWidth={0}>
          <PieChart accessibilityLayer={false}>
            <Pie
              data={rows}
              dataKey="value"
              nameKey="symbol"
              innerRadius={58}
              outerRadius={85}
              stroke="#111824"
              strokeWidth={4}
              isAnimationActive={false}
            >
              {rows.map((row) => (
                <Cell key={row.symbol} fill={row.color} />
              ))}
            </Pie>
            <Tooltip
              formatter={(v) => usd(String(v))}
              contentStyle={{
                background: "#162130",
                border: "1px solid #354154",
                color: "#e4e8ef",
                borderRadius: 6,
              }}
              itemStyle={{ color: "#e4e8ef" }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="allocation-list" aria-label="Market-value allocation">
        {allocation.items.map((item, i) => (
          <li key={item.symbol}>
            <span>
              <i style={{ background: componentColor(item.symbol, i) }} />
              {item.symbol === "CASH" ? "Available cash" : item.symbol}
            </span>
            <b>
              {item.weightPercent === null
                ? "Undefined"
                : `${Number(item.weightPercent).toFixed(2)}%`}
            </b>
            <span>
              {item.marketValue === null
                ? "Unavailable"
                : usd(item.marketValue)}
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}
