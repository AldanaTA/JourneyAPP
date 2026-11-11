function CampaignsSection() {
  return (
    <div className="section-content">
      <div className="section-header">
        <div>
          <p className="eyebrow">Sessions</p>
          <h2>Campaigns</h2>
        </div>
      </div>

      <div className="campaign-list">
        <article className="campaign-card">
          <div>
            <span className="status online">Live</span>
            <h3>Moonfall</h3>
          </div>
          <p>Next session: Friday, 8:30 PM</p>
        </article>
        <article className="campaign-card">
          <div>
            <span className="status paused">Paused</span>
            <h3>Whispering Vault</h3>
          </div>
          <p>3 clues left before the finale</p>
        </article>
      </div>
    </div>
  )
}

export default CampaignsSection
