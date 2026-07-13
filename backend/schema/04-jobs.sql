BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    description TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_jobs_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS job_traits (
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    trait_id UUID NOT NULL REFERENCES traits(id) ON DELETE CASCADE,

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (job_id, trait_id),

    CONSTRAINT uq_job_traits_sort_order UNIQUE (job_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_job_traits_job_id
ON job_traits (job_id);

CREATE INDEX IF NOT EXISTS idx_job_traits_trait_id
ON job_traits (trait_id);

CREATE TABLE IF NOT EXISTS job_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    category TEXT NOT NULL,
    description TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_job_categories_category UNIQUE (category)
);

CREATE TABLE IF NOT EXISTS job_category_assignments (
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES job_categories(id) ON DELETE CASCADE,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (job_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_job_category_assignments_job_id
ON job_category_assignments (job_id);

CREATE INDEX IF NOT EXISTS idx_job_category_assignments_category_id
ON job_category_assignments (category_id);

-- Optional: only allow one primary category per job
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_category_assignments_one_primary
ON job_category_assignments (job_id)
WHERE is_primary = TRUE;

-- Default job categories
INSERT INTO job_categories (category, description)
VALUES
    (
        'Generalist',
        'Jobs with broad, flexible tools that do not commit to one narrow combat or magic identity.'
    ),
    (
        'Martial',
        'Jobs focused on weapon combat, unarmed combat, physical durability, or direct fighting techniques.'
    ),
    (
        'Caster',
        'Jobs focused primarily on MP powers, spellcasting, magical effects, or supernatural control.'
    ),
    (
        'Half-Caster',
        'Jobs that blend martial combat with meaningful magical or supernatural abilities.'
    ),
    (
        'Ranged',
        'Jobs focused on firearms, missile weapons, long-range attacks, or precision shooting.'
    ),
    (
        'Defender',
        'Jobs focused on protection, shielding, durability, threat control, or keeping allies safe.'
    ),
    (
        'Support',
        'Jobs focused on healing, recovery, buffs, ally movement, protection, or combat assistance.'
    ),
    (
        'Saboteur',
        'Jobs focused on debuffs, control, disruption, stealth, confusion, or weakening enemies.'
    ),
    (
        'Scout',
        'Jobs focused on mobility, exploration, stealth, tracking, travel, or objective-based play.'
    ),
    (
        'Social',
        'Jobs focused on persuasion, performance, leadership, deception, investigation, or roleplay utility.'
    ),
    (
        'Crafter',
        'Jobs focused on creating, modifying, preparing, enhancing, or efficiently using items.'
    ),
    (
        'Companion',
        'Jobs focused on animal companions, summons, minions, spirits, undead, or controlled allies.'
    ),
    (
        'Nature',
        'Jobs focused on animals, plants, primal magic, natural terrain, or wilderness themes.'
    ),
    (
        'Divine',
        'Jobs focused on holy power, faith, miracles, anti-undead tools, or sacred protection.'
    ),
    (
        'Occult',
        'Jobs focused on curses, pacts, forbidden magic, souls, blood magic, or unnatural powers.'
    ),
    (
        'Specialist',
        'Jobs with a narrow or unusual identity that should be searchable separately from broad role categories.'
    )
ON CONFLICT (category)
DO UPDATE SET
    description = EXCLUDED.description,
    updated_at = NOW();
    
COMMIT;