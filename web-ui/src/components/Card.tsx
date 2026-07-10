import { SUIT_COLOR, SUIT_SYMBOL } from "../poker";

/** A single card face: rank in ink, suit in its own colour. Used by the board picker and the deal view. */
export function CardFace({ card }: { card: string }) {
  return (
    <>
      {card[0]}
      <span className="suit" style={{ color: SUIT_COLOR[card[1]] }}>
        {SUIT_SYMBOL[card[1]]}
      </span>
    </>
  );
}
