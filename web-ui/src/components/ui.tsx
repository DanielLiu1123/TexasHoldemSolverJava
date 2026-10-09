import { useId } from "react";
import type { ReactNode } from "react";

/**
 * A titled block, separated from its neighbours by a rule. The label sits in a fixed gutter so every
 * section aligns on the same vertical line; the optional note earns its place only where the control
 * below it is not self-explanatory.
 */
export function Section({ label, note, children }: { label: string; note?: ReactNode; children: ReactNode }) {
  return (
    <section className="section">
      <div className="section-head">
        <h2>{label}</h2>
        {note && <p>{note}</p>}
      </div>
      {children}
    </section>
  );
}

/** A labelled numeric input. */
export function Field({
  label,
  value,
  onChange,
  step,
  min,
  suffix,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  step?: number;
  min?: number;
  suffix?: string;
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="field-label">
        {label}
      </label>
      <div className="field-input">
        <input
          id={id}
          type="number"
          step={step}
          min={min}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
        />
        {suffix && <span className="field-suffix">{suffix}</span>}
      </div>
    </div>
  );
}

/** A labelled readout. The label is small and quiet; the number is the point. */
export function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: string }) {
  return (
    <div>
      <span className="stat-label">{label}</span>
      <span className={tone ? `stat-value ${tone}` : "stat-value"}>{value}</span>
    </div>
  );
}
