import { memo } from "react";
import Markdown, { defaultUrlTransform } from "react-markdown";
import type { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import type { RuleChapter } from "../../../models/Rules";
import { AuthImage } from "./AuthImage";
import { RuleEntityCard } from "./RuleEntityCard";
import { isRuleScheme, parseAssetLink, parseEntityLink, resolveEntity } from "./ruleLinks";
import type { RuleEntitySelection } from "./ruleLinks";

interface RuleMarkdownProps {
  /** A section body (Markdown with entity and asset links). */
  body: string;
  chapter: RuleChapter;
  onOpenEntity: (selection: RuleEntitySelection) => void;
}

/** Minimal shape of the hast nodes react-markdown passes to components. */
interface HastNode {
  type: string;
  tagName?: string;
  value?: string;
  properties?: Record<string, unknown>;
  children?: HastNode[];
}

/** Keeps the trait:/power:/.../asset: links; every other URL goes through react-markdown's safe filter. */
function urlTransform(url: string): string {
  return isRuleScheme(url) ? url : defaultUrlTransform(url);
}

/** Plain text of a hast node, used for the link label of a card. */
function textOf(node: HastNode): string {
  if (node.type === "text") return node.value ?? "";
  return (node.children ?? []).map(textOf).join("");
}

/** When a paragraph holds nothing but one entity link, returns that link node (it renders as a card). */
function soleEntityLink(node: HastNode | undefined): HastNode | null {
  const children = (node?.children ?? []).filter((child) => !(child.type === "text" && !child.value?.trim()));
  if (children.length !== 1) return null;
  const [child] = children;
  if (child.type !== "element" || child.tagName !== "a") return null;
  return parseEntityLink(String(child.properties?.href ?? "")) ? child : null;
}

/**
 * Renders one rules section body. Entity links on their own line become compendium cards; inside a sentence
 * they are links that open the details. Images are loaded from the asset endpoint at their stored size.
 */
export const RuleMarkdown = memo(function RuleMarkdown({ body, chapter, onOpenEntity }: RuleMarkdownProps) {
  /** Opens the details modal for a link target, if the entity still exists. */
  const openLink = (target: NonNullable<ReturnType<typeof parseEntityLink>>) => {
    const reference = resolveEntity(chapter, target.type, target.id);
    if (reference.entity) onOpenEntity(reference as RuleEntitySelection);
  };

  const components: Components = {
    p({ node, children }) {
      const link = soleEntityLink(node as HastNode | undefined);
      if (link) {
        const target = parseEntityLink(String(link.properties?.href))!;
        return (
          <RuleEntityCard
            reference={resolveEntity(chapter, target.type, target.id)}
            label={textOf(link)}
            onOpen={onOpenEntity}
          />
        );
      }
      return <p className="my-3 leading-7 text-slate-700">{children}</p>;
    },
    a({ href, children }) {
      const target = parseEntityLink(href);
      if (target) {
        const available = resolveEntity(chapter, target.type, target.id).entity !== null;
        return available ? (
          <button
            type="button"
            onClick={() => openLink(target)}
            className="font-medium text-violet-700 underline decoration-violet-300 underline-offset-2 hover:text-violet-900"
          >
            {children}
          </button>
        ) : (
          <span className="text-slate-500">{children}</span>
        );
      }
      return (
        <a href={href} target="_blank" rel="noreferrer noopener" className="text-violet-700 underline underline-offset-2">
          {children}
        </a>
      );
    },
    img({ src, alt }) {
      const assetId = parseAssetLink(typeof src === "string" ? src : null);
      if (!assetId) return null;
      const asset = chapter.assets[assetId];
      return (
        <AuthImage
          assetId={assetId}
          alt={asset?.altText ?? alt ?? ""}
          width={asset?.width ?? undefined}
          height={asset?.height ?? undefined}
          className="my-4 h-auto max-w-full rounded-xl border border-slate-200 shadow-sm"
        />
      );
    },
    ul: ({ children }) => <ul className="my-3 list-disc space-y-1 pl-6 text-slate-700 marker:text-violet-400">{children}</ul>,
    ol: ({ children }) => <ol className="my-3 list-decimal space-y-1 pl-6 text-slate-700 marker:text-violet-500">{children}</ol>,
    li: ({ children }) => <li className="leading-7 [&>ul]:my-1 [&>ol]:my-1">{children}</li>,
    strong: ({ children }) => <strong className="font-semibold text-slate-900">{children}</strong>,
    blockquote: ({ children }) => (
      <blockquote className="my-4 border-l-4 border-violet-200 bg-violet-50/60 px-4 py-2 text-slate-700">{children}</blockquote>
    ),
    table: ({ children }) => (
      <div className="my-4 overflow-x-auto rounded-xl border border-slate-200">
        <table className="w-full border-collapse text-left text-sm">{children}</table>
      </div>
    ),
    thead: ({ children }) => <thead className="bg-slate-100 text-slate-900">{children}</thead>,
    th: ({ children }) => <th className="border-b border-slate-200 px-3 py-2 font-semibold">{children}</th>,
    td: ({ children }) => <td className="border-t border-slate-100 px-3 py-2 align-top text-slate-700">{children}</td>,
    hr: () => <hr className="my-6 border-slate-200" />,
  };

  return (
    <Markdown remarkPlugins={[remarkGfm]} urlTransform={urlTransform} components={components}>
      {body}
    </Markdown>
  );
});
