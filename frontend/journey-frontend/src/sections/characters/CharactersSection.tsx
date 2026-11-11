function CharactersSection() {
  return (
    <div className="section-content">
      <div className="section-header">
        <div>
          <p className="eyebrow">Roster</p>
          <h2>Characters</h2>
        </div>
      </div>

      <div className="list-panel">
        <div className="character-row">
          <div className="avatar small gem">A</div>
          <div>
            <strong>Ari Vale</strong>
            <span>Ranger • Level 7</span>
          </div>
        </div>
        <div className="character-row">
          <div className="avatar small violet">N</div>
          <div>
            <strong>Nyra Sol</strong>
            <span>Wizard • Level 6</span>
          </div>
        </div>
        <div className="character-row">
          <div className="avatar small gold">T</div>
          <div>
            <strong>Thorn</strong>
            <span>Fighter • Level 8</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default CharactersSection
