import { useSyncExternalStore } from "react"

/**
 * Whether a CSS media query matches, updating when it changes.
 * Reads the value synchronously on first render, so there is no first render with the wrong answer
 * (which would mount the wrong layout and then swap it).
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query)
      list.addEventListener("change", onChange)
      return () => list.removeEventListener("change", onChange)
    },
    () => window.matchMedia(query).matches,
    () => false,
  )
}

/** Tailwind's "lg" breakpoint: the width at which the desktop layout is used. */
export const DESKTOP_QUERY = "(min-width: 1024px)"
