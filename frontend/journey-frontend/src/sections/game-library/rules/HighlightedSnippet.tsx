import type { ReactNode } from "react";

/** Renders « » search markers from the API as highlighted text. */
export function HighlightedSnippet({ text }: { text: string }) {
  const parts: ReactNode[] = [];
  const pattern = /«([^»]*)»/g;
  let last = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    parts.push(
      <mark key={match.index} className="rounded bg-amber-200/70 px-0.5 text-slate-900">
        {match[1]}
      </mark>,
    );
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <>{parts}</>;
}
