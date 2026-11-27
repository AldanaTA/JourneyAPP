import { useState } from "react";
import type { InputHTMLAttributes } from "react";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type" | "min" | "max"> & {
  /** The number, or null for an empty box (only when {@code allowEmpty}). */
  value: number | null;
  onChange: (value: number | null) => void;
  min?: number;
  max?: number;
  /** An empty box means "not set" (null) instead of the minimum. */
  allowEmpty?: boolean;
};

/**
 * A whole-number box that can be typed into freely, including a minus sign: half-typed text such as "-" stays in
 * the box until it is a number, and leaving the box shows the stored value again. Where negatives are allowed it
 * asks for the keyboard with a minus key (phone number pads have none).
 */
export function NumberInput({ value, onChange, min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER, allowEmpty = false, onBlur, ...rest }: Props) {
  const shown = value === null ? "" : String(value);
  const [text, setText] = useState(shown);
  const [synced, setSynced] = useState(shown);
  // The value changed from outside (a +/- button, another field): show it.
  if (shown !== synced) {
    setSynced(shown);
    setText(shown);
  }
  const negatives = min < 0;
  return (
    <input
      {...rest}
      type="text"
      inputMode={negatives ? "text" : "numeric"}
      pattern={negatives ? "-?[0-9]*" : "[0-9]*"}
      enterKeyHint="done"
      autoComplete="off"
      value={text}
      onChange={(event) => {
        const raw = event.target.value.replace(negatives ? /[^\d-]/g : /\D/g, "").replace(/(?!^)-/g, "");
        setText(raw);
        if (raw === "" && allowEmpty) {
          setSynced("");
          onChange(null);
          return;
        }
        if (!/^-?\d+$/.test(raw)) return;
        const n = Math.min(max, Math.max(min, Number.parseInt(raw, 10)));
        setSynced(String(n));
        onChange(n);
      }}
      onBlur={(event) => {
        setText(shown);
        onBlur?.(event);
      }}
    />
  );
}
