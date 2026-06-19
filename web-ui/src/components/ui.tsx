import { useId, useState } from "react";
import type { ReactNode } from "react";

/** A numbered step section with a plain-language title and one-line hint. */
export function Section({
  step,
  title,
  hint,
  done,
  children,
}: {
  step: number;
  title: string;
  hint?: ReactNode;
  done?: boolean;
  children: ReactNode;
}) {
  return (
    <section className={`step${done ? " step-done" : ""}`}>
      <div className="step-head">
        <span className="step-num" aria-hidden>
          {done ? "✓" : step}
        </span>
        <div>
          <h2 className="step-title">{title}</h2>
          {hint && <p className="step-hint">{hint}</p>}
        </div>
      </div>
      <div className="step-body">{children}</div>
    </section>
  );
}

/** A two-state pill toggle (used for the game type). */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: { value: T; label: string; hint?: string }[];
  onChange: (value: T) => void;
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          role="tab"
          aria-selected={opt.value === value}
          className={`segment${opt.value === value ? " segment-on" : ""}`}
          onClick={() => onChange(opt.value)}
        >
          <span>{opt.label}</span>
          {opt.hint && <small>{opt.hint}</small>}
        </button>
      ))}
    </div>
  );
}

/** A labeled numeric field with a short hint underneath. */
export function Field({
  label,
  hint,
  value,
  onChange,
  step,
  min,
  suffix,
}: {
  label: string;
  hint?: string;
  value: number;
  onChange: (value: number) => void;
  step?: number;
  min?: number;
  suffix?: string;
}) {
  const id = useId();
  return (
    <div className="field">
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
      {hint && <p className="field-hint">{hint}</p>}
    </div>
  );
}

/** A disclosure block — collapsed by default unless `defaultOpen`. */
export function Collapsible({
  title,
  subtitle,
  defaultOpen = false,
  children,
}: {
  title: string;
  subtitle?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className={`disclosure${open ? " disclosure-open" : ""}`}>
      <button type="button" className="disclosure-head" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <span className="disclosure-caret" aria-hidden>
          ▸
        </span>
        <span className="disclosure-title">{title}</span>
        {subtitle && <span className="disclosure-sub">{subtitle}</span>}
      </button>
      {open && <div className="disclosure-body">{children}</div>}
    </div>
  );
}

/** A small "?" affordance that reveals an explanation on hover/focus. */
export function InfoTip({ children }: { children: ReactNode }) {
  return (
    <span className="infotip" tabIndex={0}>
      <span className="infotip-mark" aria-hidden>
        ?
      </span>
      <span className="infotip-body" role="tooltip">
        {children}
      </span>
    </span>
  );
}
