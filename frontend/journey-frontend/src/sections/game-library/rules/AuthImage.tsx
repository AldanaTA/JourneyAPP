import { useEffect, useRef, useState } from "react";
import { ImageOff } from "lucide-react";
import { fetchAsset } from "../../../apis/rulesApi";

interface AuthImageProps {
  assetId: string;
  alt: string;
  width?: number;
  height?: number;
  className?: string;
}

/** Start downloading an image when it is this close to the screen. */
const PRELOAD_MARGIN = "400px";

/**
 * An image from the API's asset store. The API needs the access token, which an <img src> cannot send, so the
 * file is fetched with it and shown through an object URL. Like loading="lazy", the download only starts when
 * the image nears the screen, and the object URL is released when the image leaves the page.
 */
export function AuthImage({ assetId, alt, width, height, className }: AuthImageProps) {
  const placeholderRef = useRef<HTMLDivElement>(null);
  // Without IntersectionObserver (very old browsers), load straight away.
  const [visible, setVisible] = useState(() => typeof IntersectionObserver === "undefined");
  const [loaded, setLoaded] = useState<{ assetId: string; url: string } | null>(null);
  const [failed, setFailed] = useState<string | null>(null);

  /** Waits until the placeholder is near the screen. */
  useEffect(() => {
    const element = placeholderRef.current;
    if (!element || visible) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: PRELOAD_MARGIN },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [visible]);

  /** Downloads the image once visible, and releases its object URL afterwards. */
  useEffect(() => {
    if (!visible) return;
    const controller = new AbortController();
    let url: string | null = null;
    fetchAsset(assetId, controller.signal)
      .then((blob) => {
        url = URL.createObjectURL(blob);
        setLoaded({ assetId, url });
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(assetId);
      });
    return () => {
      controller.abort();
      if (url) URL.revokeObjectURL(url);
    };
  }, [assetId, visible]);

  const aspectRatio = width && height ? `${width} / ${height}` : undefined;

  if (loaded?.assetId === assetId) {
    return <img src={loaded.url} alt={alt} width={width} height={height} decoding="async" className={className} />;
  }

  // Same box as the final image, so the page does not jump when it arrives.
  return (
    <div
      ref={placeholderRef}
      role="img"
      aria-label={alt}
      style={{ aspectRatio, width: width ? `min(100%, ${width}px)` : "100%" }}
      className={`${className ?? ""} flex items-center justify-center bg-slate-100 text-slate-400`}
    >
      {failed === assetId && <ImageOff size={24} aria-hidden="true" />}
    </div>
  );
}

export default AuthImage;
