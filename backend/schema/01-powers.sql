Begin;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE power_type AS ENUM ('Destruction', 'Support', 'Sabotage', 'Utility');

CREATE TABLE IF NOT EXISTS powers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    lvl INT NOT NULL,
    type power_type NOT NULL,
    --COST--
    tp_cost INT NOT NULL,
    hp_cost INT,
    mp_cost INT,
    ep_cost INT,

    --COMPONENTS--
    material_components Text Default 'None',
    verbal BOOLEAN NOT NULL,
    sight BOOLEAN NOT NULL,
    somatic BOOLEAN NOT NULL,
    is_distinct BOOLEAN NOT NULL,
    concentration BOOLEAN NOT NULL,

    --RANGE, AREA, DURATION--
    range TEXT DEFAULT '5',
    area TEXT DEFAULT '0',
    duration TEXT DEFAULT 'Instantaneous',

    --EFFECT--
    effect TEXT NOT NULL,
    empower_effect TEXT,
    lvl_up_effect TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMIT;