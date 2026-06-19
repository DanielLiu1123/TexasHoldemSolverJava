import { SUIT_COLOR, SUIT_SYMBOL, deckCards } from "../poker";

interface Props {
  ranks: string[];
  /** Selected cards in deal order, e.g. ["Qs","Jh","2h"]. */
  value: string[];
  onChange: (cards: string[]) => void;
}

/** Click cards to toggle them onto the board (3 = flop, 4 = turn, 5 = river). */
export function BoardPicker({ ranks, value, onChange }: Props) {
  const toggle = (card: string) => {
    if (value.includes(card)) onChange(value.filter((c) => c !== card));
    else if (value.length < 5) onChange([...value, card]);
  };

  return (
    <div className="board-picker">
      <div className="board-current">
        {value.length === 0 && <span className="muted">点下方牌面，选出 3–5 张公共牌 →</span>}
        {value.map((card) => (
          <button type="button" key={card} className="card selected" onClick={() => toggle(card)}>
            {card[0]}
            <span style={{ color: SUIT_COLOR[card[1]] }}>{SUIT_SYMBOL[card[1]]}</span>
          </button>
        ))}
      </div>
      <div className="deck" style={{ gridTemplateColumns: `repeat(${ranks.length}, 1fr)` }}>
        {deckCards(ranks).map((card) => (
          <button
            type="button"
            key={card}
            className={`card${value.includes(card) ? " selected" : ""}`}
            onClick={() => toggle(card)}
          >
            {card[0]}
            <span style={{ color: SUIT_COLOR[card[1]] }}>{SUIT_SYMBOL[card[1]]}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
