import { BookOpenText, Library, ScrollText } from "lucide-react"

function CompendiumSection() {
  return (
    <div className="section-content">
      <div className="section-header">
        <div>
          <p className="eyebrow">Reference</p>
          <h2>Compendium</h2>
        </div>
      </div>

      <div className="list-panel">
        <div className="list-row">
          <ScrollText size={18} />
          <div>
            <strong>Bestiary</strong>
            <span>Creatures and monsters</span>
          </div>
        </div>
        <div className="list-row">
          <BookOpenText size={18} />
          <div>
            <strong>Magic Items</strong>
            <span>Relics and enchanted gear</span>
          </div>
        </div>
        <div className="list-row">
          <Library size={18} />
          <div>
            <strong>World Lore</strong>
            <span>History, factions, and maps</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default CompendiumSection
