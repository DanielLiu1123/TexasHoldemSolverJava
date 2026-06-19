import type { StreetSpec } from "../types";

interface Props {
  street: "flop" | "turn" | "river";
  value: StreetSpec;
  onChange: (spec: StreetSpec) => void;
  withDonk?: boolean;
}

function parseSizes(text: string): number[] | undefined {
  const sizes = text
    .split(/[\s,]+/)
    .filter(Boolean)
    .map(Number)
    .filter((n) => Number.isFinite(n) && n > 0);
  return sizes.length > 0 ? sizes : undefined;
}

const STREET_LABEL: Record<Props["street"], string> = {
  flop: "翻牌 flop",
  turn: "转牌 turn",
  river: "河牌 river",
};

/** Bet sizing controls for one street; sizes are in percent of the pot (e.g. "50 75 100"). */
export function StreetForm({ street, value, onChange, withDonk }: Props) {
  return (
    <fieldset className="street-form">
      <legend>{STREET_LABEL[street]}</legend>
      <label>
        下注 %
        <input
          type="text"
          defaultValue={(value.betSizes ?? [50]).join(" ")}
          onBlur={(e) => onChange({ ...value, betSizes: parseSizes(e.target.value) })}
        />
      </label>
      <label>
        加注 %
        <input
          type="text"
          defaultValue={(value.raiseSizes ?? [50]).join(" ")}
          onBlur={(e) => onChange({ ...value, raiseSizes: parseSizes(e.target.value) })}
        />
      </label>
      {withDonk && (
        <label>
          领打 % (OOP)
          <input
            type="text"
            defaultValue={(value.donkSizes ?? []).join(" ")}
            onBlur={(e) => onChange({ ...value, donkSizes: parseSizes(e.target.value) })}
          />
        </label>
      )}
      <label className="check">
        <input
          type="checkbox"
          checked={value.allin ?? street !== "flop"}
          onChange={(e) => onChange({ ...value, allin: e.target.checked })}
        />
        允许全下
      </label>
    </fieldset>
  );
}
