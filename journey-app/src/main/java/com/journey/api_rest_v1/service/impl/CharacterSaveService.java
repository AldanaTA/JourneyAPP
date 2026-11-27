package com.journey.api_rest_v1.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.CharacterAttribute;
import com.journey.api_rest_v1.domain.characters.CharacterAttributeId;
import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterPower;
import com.journey.api_rest_v1.domain.characters.CharacterRace;
import com.journey.api_rest_v1.domain.characters.CharacterSkill;
import com.journey.api_rest_v1.domain.characters.CharacterStatus;
import com.journey.api_rest_v1.domain.characters.CharacterTrait;
import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;
import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.CharacterInventoryDto;
import com.journey.api_rest_v1.dto.character.SaveCharacterRequest;
import com.journey.api_rest_v1.repository.CharacterAttributeRepository;
import com.journey.api_rest_v1.repository.CharacterPowerRepository;
import com.journey.api_rest_v1.repository.CharacterRaceRepository;
import com.journey.api_rest_v1.repository.CharacterSheetRepository;
import com.journey.api_rest_v1.repository.CharacterSkillRepository;
import com.journey.api_rest_v1.repository.CharacterTraitRepository;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.JobRepository;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.repository.RaceRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.campaign.CampaignContentCheck;
import com.journey.api_rest_v1.dto.campaign.ContentIssueDto;

/**
 * Saves a whole character in one transaction (PUT /characters/{id}): the editor keeps the character in the
 * browser and sends it on Save and on its periodic autosave, instead of one request per change.
 *
 * <p>The same rules as the single-change endpoints apply, checked once for the whole save: EXP and job points
 * within budget, racial traits from the character's races (worth at most 50 EXP for a mixed race), job traits
 * from the character's job, and the inventory rules. Any refusal rolls the whole save back.</p>
 */
@Service
public class CharacterSaveService {

    public static final Set<String> TRAIT_FIELDS = Set.of("trait_name", "purchase_cost", "lvl_up_cost", "trait_desc",
            "trait_uses", "mp_cost", "ep_cost", "tp_cost", "trait_effect", "lvl_up_effect");
    public static final Set<String> POWER_FIELDS = Set.of("name", "type", "tp_cost", "hp_cost", "mp_cost", "ep_cost",
            "material_components", "verbal", "sight", "somatic", "is_distinct", "concentration", "range", "area",
            "duration", "effect", "empower_effect", "lvl_up_effect");
    private static final Set<String> POWER_TYPES = Set.of("Destruction", "Support", "Sabotage", "Utility");

    /** What a save returns: the character and its inventory as they now are. */
    public record SavedCharacter(CharacterDto character, CharacterInventoryDto inventory) {
    }

    private final PlayerCharacterRepository characterRepository;
    private final CharacterSheetRepository sheetRepository;
    private final JobRepository jobRepository;
    private final RaceRepository raceRepository;
    private final CharacterRaceRepository characterRaceRepository;
    private final CharacterAttributeRepository attributeRepository;
    private final CharacterSkillRepository characterSkillRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final CharacterPowerRepository characterPowerRepository;
    private final TraitRepository traitRepository;
    private final PowerRepository powerRepository;
    private final ContentSourceRepository sourceRepository;
    private final CharacterInventoryServiceImpl inventoryService;
    private final CharacterAccess access;
    private final CharacterAssembler assembler;
    private final CurrentUser currentUser;
    private final CampaignContentCheck campaignContent;

    public CharacterSaveService(PlayerCharacterRepository characterRepository,
            CharacterSheetRepository sheetRepository,
            JobRepository jobRepository,
            RaceRepository raceRepository,
            CharacterRaceRepository characterRaceRepository,
            CharacterAttributeRepository attributeRepository,
            CharacterSkillRepository characterSkillRepository,
            CharacterTraitRepository characterTraitRepository,
            CharacterPowerRepository characterPowerRepository,
            TraitRepository traitRepository,
            PowerRepository powerRepository,
            ContentSourceRepository sourceRepository,
            CharacterInventoryServiceImpl inventoryService,
            CharacterAccess access,
            CharacterAssembler assembler,
            CurrentUser currentUser,
            CampaignContentCheck campaignContent) {
        this.campaignContent = campaignContent;
        this.characterRepository = characterRepository;
        this.sheetRepository = sheetRepository;
        this.jobRepository = jobRepository;
        this.raceRepository = raceRepository;
        this.characterRaceRepository = characterRaceRepository;
        this.attributeRepository = attributeRepository;
        this.characterSkillRepository = characterSkillRepository;
        this.characterTraitRepository = characterTraitRepository;
        this.characterPowerRepository = characterPowerRepository;
        this.traitRepository = traitRepository;
        this.powerRepository = powerRepository;
        this.sourceRepository = sourceRepository;
        this.inventoryService = inventoryService;
        this.access = access;
        this.assembler = assembler;
        this.currentUser = currentUser;
    }

    /** Replaces the chosen content sources (each must be one the user can read). */
    @Transactional
    public void setSources(UUID characterId, List<String> sourceIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (String value : sourceIds) {
            ids.add(CharacterAccess.parseUuid(value, "source"));
        }
        if (!ids.isEmpty() && sourceRepository.countAvailable(ids, currentUser.requireId(), currentUser.isAdmin()) != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the content sources is not available to you");
        }
        characterRepository.clearSources(characterId);
        for (UUID id : ids) {
            characterRepository.addSource(characterId, id);
        }
    }

    @Transactional
    public SavedCharacter save(String id, SaveCharacterRequest r) {
        PlayerCharacter c = access.loadOwned(id);
        UUID characterId = c.getId();

        // Identity, choices, and capacities.
        if (r.name() != null) {
            if (r.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank");
            c.setName(r.name().trim());
        }
        if (r.description() != null) c.setDescription(r.description());
        if (r.motive() != null) c.setMotive(r.motive());
        if (r.ideals() != null) c.setIdeals(r.ideals());
        if (r.strengths() != null) c.setStrengths(r.strengths());
        if (r.flaws() != null) c.setFlaws(r.flaws());
        if (r.learned_languages() != null) c.setLearnedLanguages(r.learned_languages());
        if (r.history() != null) c.setHistory(r.history());
        if (r.important_characters() != null) c.setImportantCharacters(r.important_characters());
        if (r.notes() != null) c.setNotes(r.notes());
        if (r.character_sheet_id() != null) {
            UUID sheetId = CharacterAccess.parseUuid(r.character_sheet_id(), "character sheet");
            if (!sheetRepository.existsById(sheetId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Character sheet not found");
            }
            c.setCharacterSheetId(sheetId);
        }
        if (r.size() != null) c.setSize(r.size());
        if (r.starting_exp() != null) c.setStartingExp(r.starting_exp());
        if (r.job_points() != null) c.setJobPoints(r.job_points());
        if (r.current_hp() != null) c.setCurrentHp(r.current_hp());
        if (r.current_wp() != null) c.setCurrentWp(r.current_wp());
        if (r.current_ep() != null) c.setCurrentEp(r.current_ep());
        if (r.current_mp() != null) c.setCurrentMp(r.current_mp());
        if (r.temp_hp() != null) c.setTempHp(r.temp_hp());
        if (r.hp_bonus() != null) c.setHpBonus(r.hp_bonus());
        if (r.wp_bonus() != null) c.setWpBonus(r.wp_bonus());
        if (r.ep_bonus() != null) c.setEpBonus(r.ep_bonus());
        if (r.mp_bonus() != null) c.setMpBonus(r.mp_bonus());
        if (r.physical_damage_bonus() != null) c.setPhysicalDamageBonus(r.physical_damage_bonus());
        if (r.magic_damage_bonus() != null) c.setMagicDamageBonus(r.magic_damage_bonus());
        if (r.priority_bonus() != null) c.setPriorityBonus(r.priority_bonus());
        if (r.movement_bonus() != null) c.setMovementBonus(r.movement_bonus());

        if (r.source_ids() != null) setSources(characterId, r.source_ids());

        // The new job, checked now but set after the old job's traits are gone (they point at the old job).
        UUID newJob = c.getJobId();
        if (r.job_id() != null) {
            newJob = r.job_id().isBlank() ? null : CharacterAccess.parseUuid(r.job_id(), "job");
            if (newJob != null && !jobRepository.existsById(newJob)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job not found");
            }
            if (newJob == null && c.getStatus() != CharacterStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An active character needs a job");
            }
        }
        boolean jobChanged = !Objects.equals(newJob, c.getJobId());

        // 1. Remove traits and powers being replaced (and job traits of a job being left).
        List<CharacterTrait> currentTraits = characterTraitRepository.findById_CharacterIdOrderByCreatedAtAsc(characterId);
        if (r.traits() != null) {
            characterTraitRepository.deleteAll(currentTraits);
        } else if (jobChanged) {
            characterTraitRepository.deleteAll(currentTraits.stream()
                    .filter(t -> t.getSource() == CharacterTraitSource.JOB).toList());
        }
        if (r.powers() != null) {
            characterPowerRepository.deleteAll(characterPowerRepository.findById_CharacterIdOrderByCreatedAtAsc(characterId));
        }
        characterTraitRepository.flush();
        characterPowerRepository.flush();

        // 2. Races: add the new ones, point the language at one of them, then remove the old ones.
        List<UUID> races = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(characterId).stream()
                .map(row -> row.getId().getChildId()).toList();
        if (r.race_ids() != null) {
            races = saveRaces(c, r.race_ids(), r.language_race_id());
        } else if (r.language_race_id() != null) {
            UUID language = CharacterAccess.parseOptionalUuid(r.language_race_id(), "language race");
            if (language != null && !races.contains(language)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The language race must be one of the character's races");
            }
            c.setLanguageRaceId(language);
        }

        // 3. The job.
        c.setJobId(newJob);
        characterRepository.saveAndFlush(c);

        // 4. Traits and powers.
        if (r.traits() != null) saveTraits(characterId, r.traits(), races, newJob);
        if (r.powers() != null) savePowers(characterId, r.powers());

        // 5. Attributes and skills.
        if (r.attributes() != null) {
            for (SaveCharacterRequest.AttributeEntry entry : r.attributes()) {
                CharacterAttribute row = attributeRepository.findById(new CharacterAttributeId(characterId, entry.attribute()))
                        .orElseGet(() -> {
                            CharacterAttribute created = new CharacterAttribute();
                            created.setId(new CharacterAttributeId(characterId, entry.attribute()));
                            return created;
                        });
                row.setExpSpent(entry.exp_spent());
                row.setJobPointsSpent(entry.job_points_spent());
                row.setBonus(entry.bonus());
                row.setModifier(entry.modifier());
                attributeRepository.save(row);
            }
        }
        if (r.skills() != null) {
            Map<UUID, CharacterSkill> skills = characterSkillRepository.findById_CharacterId(characterId).stream()
                    .collect(Collectors.toMap(s -> s.getId().getChildId(), Function.identity()));
            for (SaveCharacterRequest.SkillEntry entry : r.skills()) {
                CharacterSkill row = skills.get(CharacterAccess.parseUuid(entry.skill_id(), "skill"));
                if (row == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill not found: " + entry.skill_id());
                row.setCreationAdjustment(entry.creation_adjustment());
                row.setExpSpent(entry.exp_spent());
                row.setJobPointsSpent(entry.job_points_spent());
                row.setFailures(entry.failures());
                row.setPermanentModifier(entry.permanent_modifier());
                row.setTemporaryModifier(entry.temporary_modifier());
                characterSkillRepository.save(row);
            }
        }

        // 6. Inventory.
        if (r.inventory() != null) inventoryService.replaceAll(characterId, r.inventory());

        // 7. The totals.
        access.requireWithinBudget(characterId);
        if (races.size() > 1) {
            int racial = assembler.budget(characterId).racialTraitCost();
            if (racial > CharacterServiceImpl.MIXED_RACE_TRAIT_EXP) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mixed race picks racial traits worth "
                        + CharacterServiceImpl.MIXED_RACE_TRAIT_EXP + " EXP, not " + racial);
            }
        }
        // A campaign version may only use what its campaign allows.
        if (c.getCampaignId() != null) {
            List<ContentIssueDto> issues = campaignContent.issues(characterId, c.getCampaignId());
            if (!issues.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not allowed in this campaign: "
                        + issues.stream().map(i -> i.name() + " (" + i.source() + ")").distinct()
                                .collect(Collectors.joining(", ")) + ". Remove it to save.");
            }
        }
        PlayerCharacter saved = characterRepository.findById(characterId).orElseThrow();
        return new SavedCharacter(assembler.toDto(saved), inventoryService.snapshot(characterId));
    }

    private List<UUID> saveRaces(PlayerCharacter c, List<String> raceIds, String languageRaceId) {
        UUID characterId = c.getId();
        Set<UUID> wanted = new LinkedHashSet<>();
        for (String raceId : raceIds) {
            wanted.add(CharacterAccess.parseUuid(raceId, "race"));
        }
        Set<UUID> found = raceRepository.findAllById(wanted).stream().map(race -> race.getId()).collect(Collectors.toSet());
        for (UUID raceId : wanted) {
            if (!found.contains(raceId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Race not found: " + raceId);
        }
        UUID language = CharacterAccess.parseOptionalUuid(languageRaceId, "language race");
        if (language == null && wanted.size() == 1) language = wanted.iterator().next();
        if (language == null && wanted.contains(c.getLanguageRaceId())) language = c.getLanguageRaceId();
        if (language != null && !wanted.contains(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The language race must be one of the character's races");
        }
        if ((language == null || wanted.isEmpty()) && c.getStatus() != CharacterStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An active character needs a race and a language set");
        }

        List<CharacterRace> current = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(characterId);
        Map<UUID, CharacterRace> existing = current.stream()
                .collect(Collectors.toMap(row -> row.getId().getChildId(), Function.identity()));
        int order = 0;
        for (UUID raceId : wanted) {
            CharacterRace row = existing.get(raceId);
            if (row == null) {
                row = new CharacterRace();
                row.setId(new CharacterChildId(characterId, raceId));
            }
            row.setSortOrder(order++);
            characterRaceRepository.save(row);
        }
        characterRaceRepository.flush();
        c.setLanguageRaceId(language);
        characterRepository.saveAndFlush(c);
        for (CharacterRace row : current) {
            if (!wanted.contains(row.getId().getChildId())) characterRaceRepository.delete(row);
        }
        characterRaceRepository.flush();
        return List.copyOf(wanted);
    }

    private void saveTraits(UUID characterId, List<SaveCharacterRequest.TraitEntry> entries, List<UUID> races, UUID jobId) {
        Set<UUID> seen = new HashSet<>();
        Map<UUID, Trait> traits = traitRepository.findAllById(entries.stream()
                .map(e -> CharacterAccess.parseUuid(e.trait_id(), "trait")).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Trait::getId, Function.identity()));
        for (SaveCharacterRequest.TraitEntry entry : entries) {
            UUID traitId = CharacterAccess.parseUuid(entry.trait_id(), "trait");
            Trait t = traits.get(traitId);
            if (t == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trait not found: " + traitId);
            if (!seen.add(traitId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getTraitName() + " is listed twice");
            }
            CharacterTrait row = new CharacterTrait();
            row.setId(new CharacterChildId(characterId, traitId));
            row.setSource(entry.source());
            row.setLvl(entry.lvl());
            row.setExpSpent(entry.exp_spent());
            row.setNotes(entry.notes() == null ? "" : entry.notes());
            row.setUsesSpent(entry.uses_spent() == null ? 0 : entry.uses_spent());
            Map<String, Object> edits = checkedEdits(entry.edits(), TRAIT_FIELDS, t.getTraitName());
            set(text(edits, "trait_name"), t.getTraitName(), row::setTraitName);
            set(count(edits, "purchase_cost"), t.getPurchaseCost(), row::setPurchaseCost);
            set(count(edits, "lvl_up_cost"), t.getLvlUpCost(), row::setLvlUpCost);
            set(text(edits, "trait_desc"), t.getTraitDesc(), row::setTraitDesc);
            set(count(edits, "trait_uses"), t.getTraitUses(), row::setTraitUses);
            set(count(edits, "mp_cost"), t.getMpCost(), row::setMpCost);
            set(count(edits, "ep_cost"), t.getEpCost(), row::setEpCost);
            set(count(edits, "tp_cost"), t.getTpCost(), row::setTpCost);
            set(text(edits, "trait_effect"), t.getTraitEffect(), row::setTraitEffect);
            set(text(edits, "lvl_up_effect"), t.getLvlUpEffect(), row::setLvlUpEffect);
            switch (entry.source()) {
                case RACIAL -> {
                    UUID raceId = CharacterAccess.parseUuid(entry.race_id(), "race");
                    if (!races.contains(raceId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                t.getTraitName() + " comes from a race the character does not have");
                    }
                    if (!characterRepository.raceHasTrait(raceId, traitId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getTraitName() + " is not a racial trait of that race");
                    }
                    row.setRaceId(raceId);
                }
                case JOB -> {
                    if (jobId == null || !characterRepository.jobHasTrait(jobId, traitId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, t.getTraitName() + " is not one of the job's traits");
                    }
                    row.setJobId(jobId);
                    // A job trait is paid for with job points: its (possibly edited) purchase cost.
                    Integer cost = row.getPurchaseCost() != null ? row.getPurchaseCost() : t.getPurchaseCost();
                    row.setJobPointsSpent(cost == null ? 0 : cost);
                }
                case PURCHASED -> {
                }
            }
            characterTraitRepository.save(row);
        }
        characterTraitRepository.flush();
    }

    private void savePowers(UUID characterId, List<SaveCharacterRequest.PowerEntry> entries) {
        Set<UUID> seen = new HashSet<>();
        Map<UUID, Power> powers = powerRepository.findAllById(entries.stream()
                .map(e -> CharacterAccess.parseUuid(e.power_id(), "power")).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Power::getId, Function.identity()));
        for (SaveCharacterRequest.PowerEntry entry : entries) {
            UUID powerId = CharacterAccess.parseUuid(entry.power_id(), "power");
            Power p = powers.get(powerId);
            if (p == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Power not found: " + powerId);
            if (!seen.add(powerId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, p.getName() + " is listed twice");
            CharacterPower row = new CharacterPower();
            row.setId(new CharacterChildId(characterId, powerId));
            row.setLvl(entry.lvl());
            row.setExpSpent(entry.exp_spent());
            row.setNotes(entry.notes() == null ? "" : entry.notes());
            Map<String, Object> edits = checkedEdits(entry.edits(), POWER_FIELDS, p.getName());
            String type = text(edits, "type");
            if (type != null && !POWER_TYPES.contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be one of " + POWER_TYPES);
            }
            set(text(edits, "name"), p.getName(), row::setName);
            set(type, p.getType() == null ? null : p.getType().name(), row::setType);
            set(count(edits, "tp_cost"), p.getTpCost(), row::setTpCost);
            set(count(edits, "hp_cost"), p.getHpCost(), row::setHpCost);
            set(count(edits, "mp_cost"), p.getMpCost(), row::setMpCost);
            set(count(edits, "ep_cost"), p.getEpCost(), row::setEpCost);
            set(text(edits, "material_components"), p.getMaterialComponents(), row::setMaterialComponents);
            set(flag(edits, "verbal"), p.getVerbal(), row::setVerbal);
            set(flag(edits, "sight"), p.getSight(), row::setSight);
            set(flag(edits, "somatic"), p.getSomatic(), row::setSomatic);
            set(flag(edits, "is_distinct"), p.getIsDistinct(), row::setIsDistinct);
            set(flag(edits, "concentration"), p.getConcentration(), row::setConcentration);
            set(text(edits, "range"), p.getRange(), row::setRange);
            set(text(edits, "area"), p.getArea(), row::setArea);
            set(text(edits, "duration"), p.getDuration(), row::setDuration);
            set(text(edits, "effect"), p.getEffect(), row::setEffect);
            set(text(edits, "empower_effect"), p.getEmpowerEffect(), row::setEmpowerEffect);
            set(text(edits, "lvl_up_effect"), p.getLvlUpEffect(), row::setLvlUpEffect);
            characterPowerRepository.save(row);
        }
        characterPowerRepository.flush();
    }

    // ----------------------------
    // Edits
    // ----------------------------

    /** Stores an edit only where it differs from the compendium; no edit (null) follows the compendium. */
    private static <T> void set(T edited, T compendiumValue, Consumer<T> setter) {
        setter.accept(edited == null || Objects.equals(edited, compendiumValue) ? null : edited);
    }

    private static Map<String, Object> checkedEdits(Map<String, Object> edits, Set<String> allowed, String what) {
        if (edits == null) return Map.of();
        for (String field : edits.keySet()) {
            if (!allowed.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown field " + field + " in the edits of " + what);
            }
        }
        return new HashMap<>(edits);
    }

    private static String text(Map<String, Object> edits, String field) {
        Object value = edits.get(field);
        if (value == null) return null;
        if (value instanceof String s) return s;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be text");
    }

    private static Integer count(Map<String, Object> edits, String field) {
        Object value = edits.get(field);
        if (value == null) return null;
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.intValue() >= 0) {
            return n.intValue();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a whole number of 0 or more");
    }

    private static Boolean flag(Map<String, Object> edits, String field) {
        Object value = edits.get(field);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be true or false");
    }
}
