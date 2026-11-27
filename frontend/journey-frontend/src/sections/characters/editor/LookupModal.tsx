import { CompendiumSearch } from "@/sections/campaigns/play/SearchTool";
import { useSearchSourceIds } from "./referenceStore";
import { Modal } from "./ui";

/**
 * Look things up while building a character (e.g. the creatures an Animal Companion can be): the core rules and
 * the character's chosen sources.
 */
export default function LookupModal({ onClose }: { onClose: () => void }) {
  const sourceIds = useSearchSourceIds();
  return (
    <Modal title="Look up" subtitle="Traits, powers, items, creatures, races, jobs, and rules" onClose={onClose} wide tall>
      <CompendiumSearch sourceIds={sourceIds} hint="Search the core rules and this character's sources." />
    </Modal>
  );
}
