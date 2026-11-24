/** Shared helpers for the compendium filter sheets. */

/** Adds the value to the list, or removes it when already present. */
export function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}
