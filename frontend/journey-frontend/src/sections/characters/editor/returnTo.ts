import { useSearchParams } from "react-router-dom";

/**
 * Where the editor's X goes: the page that opened it (?return=/campaigns/... from a campaign), else the character
 * list. Only paths inside the app are followed. {@code suffix} carries the return address on to another editor
 * page (view -> edit, new -> saved).
 */
export function useReturnTo(): { to: string; suffix: string } {
  const [params] = useSearchParams();
  const value = params.get("return");
  const safe = value && value.startsWith("/") && !value.startsWith("//") ? value : null;
  return { to: safe ?? "/characters", suffix: safe ? `?return=${encodeURIComponent(safe)}` : "" };
}

/** The ?return= part for opening a character from somewhere other than the character list. */
export function returnParam(path: string): string {
  return `?return=${encodeURIComponent(path)}`;
}
