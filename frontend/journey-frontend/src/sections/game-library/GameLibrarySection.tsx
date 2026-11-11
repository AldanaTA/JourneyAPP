function GameLibrarySection() {
  return (
    <div className="section-content">
      <div className="section-header">
        <div>
          <p className="eyebrow">Journey collection</p>
          <h2>Library</h2>
        </div>
        <button className="primary-action" type="button">
          + New
        </button>
      </div>

      <div className="story-card featured-card">
        <span className="card-badge">Active campaign</span>
        <h3>Moonfall: The Shattered Crown</h3>
        <p>4 sessions logged • 1 party roster • 3 unresolved mysteries</p>
      </div>

      <div className="mini-grid">
        <article className="story-card">
          <span className="card-badge muted">System</span>
          <h3>Dungeon Crawl</h3>
          <p>Core rules • 3 sessions</p>
        </article>
        <article className="story-card">
          <span className="card-badge muted">System</span>
          <h3>Shadowdark</h3>
          <p>Dark fantasy • 2 parties</p>
        </article>
      </div>
    </div>
  )
}

export default GameLibrarySection
