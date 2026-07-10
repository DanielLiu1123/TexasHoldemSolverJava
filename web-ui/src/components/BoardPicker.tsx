import { deckCards } from "../poker";
import { CardFace } from "./Card";

interface Props {
  /** Selected cards in deal order, e.g. ["Qs","Jh","2h"]. */
  value: string[];
  onChange: (cards: string[]) => void;
}

/** Street implied by a board of n cards. */
const STREET = ["", "", "", "flop", "turn", "river"];

/**
 * Click cards to toggle them onto the board: three for a flop, four for a turn, five for a river.
 *
 * <p>The deck lays out as four suit rows by thirteen rank columns — the arrangement a dealer would
 * spread, and the one that makes a rank easy to find by eye. {@code deckCards()} is already
 * rank-major, so a column flow fills it directly.
 */
export function BoardPicker({ value, onChange }: Props) {
  const toggle = (card: string) => {
    if (value.includes(card)) onChange(value.filter((c) => c !== card));
    else if (value.length < 5) onChange([...value, card]);
  };

  return (
    <div>
      <div className="board-selection">
        {value.length === 0 ? (
          <span>Pick 3 to 5 community cards</span>
        ) : (
          <>
            {value.map((card) => (
              <button type="button" key={card} className="card" onClick={() => toggle(card)}>
                <CardFace card={card} />
              </button>
            ))}
            <span className="street">{STREET[value.length]}</span>
          </>
        )}
      </div>
      <div className="board">
        {deckCards().map((card) => (
          <button
            type="button"
            key={card}
            className="card"
            aria-pressed={value.includes(card)}
            disabled={value.length >= 5 && !value.includes(card)}
            onClick={() => toggle(card)}
          >
            <CardFace card={card} />
          </button>
        ))}
      </div>
    </div>
  );
}
