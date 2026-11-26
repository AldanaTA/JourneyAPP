package com.journey.api_rest_v1.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterPower;
import com.journey.api_rest_v1.domain.characters.CharacterTrait;
import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;
import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.character.AddCharacterPowerRequest;
import com.journey.api_rest_v1.dto.character.AddCharacterTraitRequest;
import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.UpdateCharacterPowerRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterTraitRequest;
import com.journey.api_rest_v1.repository.CharacterPowerRepository;
import com.journey.api_rest_v1.repository.CharacterRaceRepository;
import com.journey.api_rest_v1.repository.CharacterTraitRepository;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.CharacterAbilityService;

/**
 * Default {@link CharacterAbilityService}.
 *
 * <p>Edits are stored only where they differ from the compendium: setting a field back to the compendium value,
 * or naming it in {@code reset}, clears the edit, so later compendium fixes show up again.</p>
 */
@Service
public class CharacterAbilityServiceImpl implements CharacterAbilityService {

    /** The cost of learning a power. */
    static final int POWER_COST = 10;

    private static final Set<String> TRAIT_FIELDS = Set.of("trait_name", "purchase_cost", "lvl_up_cost", "trait_desc",
            "trait_uses", "mp_cost", "ep_cost", "tp_cost", "trait_effect", "lvl_up_effect");
    private static final Set<String> POWER_FIELDS = Set.of("name", "type", "tp_cost", "hp_cost", "mp_cost", "ep_cost",
            "material_components", "verbal", "sight", "somatic", "is_distinct", "concentration", "range", "area",
            "duration", "effect", "empower_effect", "lvl_up_effect");

    private final PlayerCharacterRepository characterRepository;
    private final CharacterRaceRepository characterRaceRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final CharacterPowerRepository characterPowerRepository;
    private final TraitRepository traitRepository;
    private final PowerRepository powerRepository;
    private final CharacterAccess access;
    private final CharacterAssembler assembler;

    /** Wires the repositories and helpers. */
    public CharacterAbilityServiceImpl(PlayerCharacterRepository characterRepository,
            CharacterRaceRepository characterRaceRepository,
            CharacterTraitRepository characterTraitRepository,
            CharacterPowerRepository characterPowerRepository,
            TraitRepository traitRepository,
            PowerRepository powerRepository,
            CharacterAccess access,
            CharacterAssembler assembler) {
        this.characterRepository = characterRepository;
        this.characterRaceRepository = characterRaceRepository;
        this.characterTraitRepository = characterTraitRepository;
        this.characterPowerRepository = characterPowerRepository;
        this.traitRepository = traitRepository;
        this.powerRepository = powerRepository;
        this.access = access;
        this.assembler = assembler;
    }

    // ----------------------------
    // Traits
    // ----------------------------

    @Override
    @Transactional
    public CharacterDto addTrait(String characterId, AddCharacterTraitRequest request) {
        PlayerCharacter c = access.loadOwned(characterId);
        UUID id = c.getId();
        Trait trait = traitRepository.findById(CharacterAccess.parseUuid(request.trait_id(), "trait"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trait not found"));
        CharacterChildId key = new CharacterChildId(id, trait.getId());
        if (characterTraitRepository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The character already has " + trait.getTraitName());
        }
        int cost = trait.getPurchaseCost() == null ? 0 : trait.getPurchaseCost();

        CharacterTrait row = new CharacterTrait();
        row.setId(key);
        row.setSource(request.source());
        row.setLvl(request.lvl() == null ? 1 : request.lvl());
        switch (request.source()) {
            case RACIAL -> {
                UUID raceId = CharacterAccess.parseUuid(request.race_id(), "race");
                List<UUID> races = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(id).stream()
                        .map(r -> r.getId().getChildId()).toList();
                if (!races.contains(raceId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That race is not one of the character's");
                }
                if (!characterRepository.raceHasTrait(raceId, trait.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            trait.getTraitName() + " is not a racial trait of that race");
                }
                if (races.size() > 1) {
                    int total = assembler.budget(id).racialTraitCost() + cost;
                    if (total > CharacterServiceImpl.MIXED_RACE_TRAIT_EXP) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mixed race picks racial traits worth "
                                + CharacterServiceImpl.MIXED_RACE_TRAIT_EXP + " EXP; this would make " + total);
                    }
                }
                row.setRaceId(raceId);
                row.setExpSpent(request.exp_spent() == null ? 0 : request.exp_spent());
            }
            case JOB -> {
                if (c.getJobId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a job first");
                }
                if (!characterRepository.jobHasTrait(c.getJobId(), trait.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            trait.getTraitName() + " is not one of the job's traits");
                }
                row.setJobId(c.getJobId());
                row.setJobPointsSpent(cost);
                row.setExpSpent(request.exp_spent() == null ? 0 : request.exp_spent());
            }
            case PURCHASED -> row.setExpSpent(request.exp_spent() == null ? cost : request.exp_spent());
        }
        characterTraitRepository.save(row);
        access.requireWithinBudget(id);
        return assembler.toDto(c);
    }

    @Override
    @Transactional
    public CharacterDto updateTrait(String characterId, String traitId, UpdateCharacterTraitRequest r) {
        PlayerCharacter c = access.loadOwned(characterId);
        UUID traitUuid = CharacterAccess.parseUuid(traitId, "trait");
        CharacterTrait row = characterTraitRepository.findById(new CharacterChildId(c.getId(), traitUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The character does not have that trait"));
        Trait t = traitRepository.findById(traitUuid).orElseThrow();

        if (r.lvl() != null) row.setLvl(r.lvl());
        if (r.exp_spent() != null) row.setExpSpent(r.exp_spent());
        if (r.notes() != null) row.setNotes(r.notes());
        override(r.trait_name(), t.getTraitName(), row::setTraitName);
        override(r.purchase_cost(), t.getPurchaseCost(), row::setPurchaseCost);
        override(r.lvl_up_cost(), t.getLvlUpCost(), row::setLvlUpCost);
        override(r.trait_desc(), t.getTraitDesc(), row::setTraitDesc);
        override(r.trait_uses(), t.getTraitUses(), row::setTraitUses);
        override(r.mp_cost(), t.getMpCost(), row::setMpCost);
        override(r.ep_cost(), t.getEpCost(), row::setEpCost);
        override(r.tp_cost(), t.getTpCost(), row::setTpCost);
        override(r.trait_effect(), t.getTraitEffect(), row::setTraitEffect);
        override(r.lvl_up_effect(), t.getLvlUpEffect(), row::setLvlUpEffect);
        for (String field : resetFields(r.reset(), TRAIT_FIELDS)) {
            switch (field) {
                case "trait_name" -> row.setTraitName(null);
                case "purchase_cost" -> row.setPurchaseCost(null);
                case "lvl_up_cost" -> row.setLvlUpCost(null);
                case "trait_desc" -> row.setTraitDesc(null);
                case "trait_uses" -> row.setTraitUses(null);
                case "mp_cost" -> row.setMpCost(null);
                case "ep_cost" -> row.setEpCost(null);
                case "tp_cost" -> row.setTpCost(null);
                case "trait_effect" -> row.setTraitEffect(null);
                case "lvl_up_effect" -> row.setLvlUpEffect(null);
                default -> throw new IllegalStateException(field);
            }
        }
        characterTraitRepository.save(row);
        if (row.getSource() == CharacterTraitSource.RACIAL
                && characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(c.getId()).size() > 1) {
            access.requireWithinBudget(c.getId());
            int total = assembler.budget(c.getId()).racialTraitCost();
            if (total > CharacterServiceImpl.MIXED_RACE_TRAIT_EXP) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mixed race picks racial traits worth "
                        + CharacterServiceImpl.MIXED_RACE_TRAIT_EXP + " EXP; this would make " + total);
            }
        }
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    @Override
    @Transactional
    public CharacterDto removeTrait(String characterId, String traitId) {
        PlayerCharacter c = access.loadOwned(characterId);
        CharacterChildId key = new CharacterChildId(c.getId(), CharacterAccess.parseUuid(traitId, "trait"));
        if (!characterTraitRepository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The character does not have that trait");
        }
        characterTraitRepository.deleteById(key);
        characterTraitRepository.flush();
        return assembler.toDto(c);
    }

    // ----------------------------
    // Powers
    // ----------------------------

    @Override
    @Transactional
    public CharacterDto addPower(String characterId, AddCharacterPowerRequest request) {
        PlayerCharacter c = access.loadOwned(characterId);
        Power power = powerRepository.findById(CharacterAccess.parseUuid(request.power_id(), "power"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Power not found"));
        CharacterChildId key = new CharacterChildId(c.getId(), power.getId());
        if (characterPowerRepository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The character already has " + power.getName());
        }
        CharacterPower row = new CharacterPower();
        row.setId(key);
        row.setLvl(request.lvl() == null ? 1 : request.lvl());
        row.setExpSpent(request.exp_spent() == null ? POWER_COST : request.exp_spent());
        characterPowerRepository.save(row);
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    @Override
    @Transactional
    public CharacterDto updatePower(String characterId, String powerId, UpdateCharacterPowerRequest r) {
        PlayerCharacter c = access.loadOwned(characterId);
        UUID powerUuid = CharacterAccess.parseUuid(powerId, "power");
        CharacterPower row = characterPowerRepository.findById(new CharacterChildId(c.getId(), powerUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The character does not have that power"));
        Power p = powerRepository.findById(powerUuid).orElseThrow();

        if (r.lvl() != null) row.setLvl(r.lvl());
        if (r.exp_spent() != null) row.setExpSpent(r.exp_spent());
        if (r.notes() != null) row.setNotes(r.notes());
        override(r.name(), p.getName(), row::setName);
        override(r.type(), p.getType() == null ? null : p.getType().name(), row::setType);
        override(r.tp_cost(), p.getTpCost(), row::setTpCost);
        override(r.hp_cost(), p.getHpCost(), row::setHpCost);
        override(r.mp_cost(), p.getMpCost(), row::setMpCost);
        override(r.ep_cost(), p.getEpCost(), row::setEpCost);
        override(r.material_components(), p.getMaterialComponents(), row::setMaterialComponents);
        override(r.verbal(), p.getVerbal(), row::setVerbal);
        override(r.sight(), p.getSight(), row::setSight);
        override(r.somatic(), p.getSomatic(), row::setSomatic);
        override(r.is_distinct(), p.getIsDistinct(), row::setIsDistinct);
        override(r.concentration(), p.getConcentration(), row::setConcentration);
        override(r.range(), p.getRange(), row::setRange);
        override(r.area(), p.getArea(), row::setArea);
        override(r.duration(), p.getDuration(), row::setDuration);
        override(r.effect(), p.getEffect(), row::setEffect);
        override(r.empower_effect(), p.getEmpowerEffect(), row::setEmpowerEffect);
        override(r.lvl_up_effect(), p.getLvlUpEffect(), row::setLvlUpEffect);
        for (String field : resetFields(r.reset(), POWER_FIELDS)) {
            switch (field) {
                case "name" -> row.setName(null);
                case "type" -> row.setType(null);
                case "tp_cost" -> row.setTpCost(null);
                case "hp_cost" -> row.setHpCost(null);
                case "mp_cost" -> row.setMpCost(null);
                case "ep_cost" -> row.setEpCost(null);
                case "material_components" -> row.setMaterialComponents(null);
                case "verbal" -> row.setVerbal(null);
                case "sight" -> row.setSight(null);
                case "somatic" -> row.setSomatic(null);
                case "is_distinct" -> row.setIsDistinct(null);
                case "concentration" -> row.setConcentration(null);
                case "range" -> row.setRange(null);
                case "area" -> row.setArea(null);
                case "duration" -> row.setDuration(null);
                case "effect" -> row.setEffect(null);
                case "empower_effect" -> row.setEmpowerEffect(null);
                case "lvl_up_effect" -> row.setLvlUpEffect(null);
                default -> throw new IllegalStateException(field);
            }
        }
        characterPowerRepository.save(row);
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    @Override
    @Transactional
    public CharacterDto removePower(String characterId, String powerId) {
        PlayerCharacter c = access.loadOwned(characterId);
        CharacterChildId key = new CharacterChildId(c.getId(), CharacterAccess.parseUuid(powerId, "power"));
        if (!characterPowerRepository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The character does not have that power");
        }
        characterPowerRepository.deleteById(key);
        characterPowerRepository.flush();
        return assembler.toDto(c);
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    /** Stores an edit only where it differs from the compendium (same value = no edit); null leaves it alone. */
    private static <T> void override(T requested, T compendiumValue, Consumer<T> setter) {
        if (requested != null) {
            setter.accept(Objects.equals(requested, compendiumValue) ? null : requested);
        }
    }

    /** The fields to reset: the listed ones, or all of them for "all"; 400 for an unknown name. */
    private static Set<String> resetFields(List<String> reset, Set<String> allowed) {
        if (reset == null || reset.isEmpty()) {
            return Set.of();
        }
        if (reset.contains("all")) {
            return allowed;
        }
        for (String field : reset) {
            if (!allowed.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown field to reset: " + field
                        + " (one of " + String.join(", ", allowed.stream().sorted().toList()) + ", or all)");
            }
        }
        return Set.copyOf(reset);
    }
}
