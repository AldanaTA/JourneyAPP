/**
 * Stores the edited values: a field equal to the compendium (or reset) is no edit; with the compendium unknown,
 * only fields that already were edited or that changed are kept as edits.
 */
export function applyEdits<F extends object>(
  base: F,
  baseKnown: boolean,
  previousEdits: Partial<F>,
  previousValues: F,
  values: F,
  reset: Set<string>,
): Partial<F> {
  const edits: Partial<F> = {};
  for (const key of Object.keys(values) as (keyof F)[]) {
    if (reset.has(String(key))) continue;
    const value = values[key];
    const changed = JSON.stringify(value) !== JSON.stringify(previousValues[key]);
    if (baseKnown) {
      if (JSON.stringify(value) !== JSON.stringify(base[key])) edits[key] = value;
    } else if (changed || key in previousEdits) {
      edits[key] = value;
    }
  }
  return edits;
}
