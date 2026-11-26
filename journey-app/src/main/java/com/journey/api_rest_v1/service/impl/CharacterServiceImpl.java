package com.journey.api_rest_v1.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.CharacterAttribute;
import com.journey.api_rest_v1.domain.characters.CharacterAttributeId;
import com.journey.api_rest_v1.domain.characters.CharacterAttributeName;
import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterExpAward;
import com.journey.api_rest_v1.domain.characters.CharacterInventory;
import com.journey.api_rest_v1.domain.characters.CharacterRace;
import com.journey.api_rest_v1.domain.characters.CharacterSkill;
import com.journey.api_rest_v1.domain.characters.CharacterStatus;
import com.journey.api_rest_v1.domain.characters.CharacterTrait;
import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;
import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.domain.characters.Skill;
import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.domain.races.RaceSize;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.character.CharacterBudgetDto;
import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.CharacterExpAwardDto;
import com.journey.api_rest_v1.dto.character.CharacterReferenceDataDto;
import com.journey.api_rest_v1.dto.character.CharacterSheetDto;
import com.journey.api_rest_v1.dto.character.CharacterSummaryDto;
import com.journey.api_rest_v1.dto.character.CreateCharacterRequest;
import com.journey.api_rest_v1.dto.character.CreateExpAwardRequest;
import com.journey.api_rest_v1.dto.character.CreationCheckDto;
import com.journey.api_rest_v1.dto.character.SetCharacterJobRequest;
import com.journey.api_rest_v1.dto.character.SetCharacterRacesRequest;
import com.journey.api_rest_v1.dto.character.SkillDto;
import com.journey.api_rest_v1.dto.character.UpdateCharacterAttributeRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterSkillRequest;
import com.journey.api_rest_v1.repository.CharacterAttributeRepository;
import com.journey.api_rest_v1.repository.CharacterExpAwardRepository;
import com.journey.api_rest_v1.repository.CharacterInventoryRepository;
import com.journey.api_rest_v1.repository.CharacterRaceRepository;
import com.journey.api_rest_v1.repository.CharacterSheetRepository;
import com.journey.api_rest_v1.repository.CharacterSkillRepository;
import com.journey.api_rest_v1.repository.CharacterTraitRepository;
import com.journey.api_rest_v1.repository.JobRepository;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.repository.RaceRepository;
import com.journey.api_rest_v1.repository.SkillRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.CharacterService;
import com.journey.api_rest_v1.service.CurrentUser;

/**
 * Default {@link CharacterService}. Characters are per-user and change often, so they are not cached; only the
 * reference data (sheets and skills) is.
 */
@Service
public class CharacterServiceImpl implements CharacterService {

    /** Mixed-race characters pick racial traits worth exactly this much EXP. */
    static final int MIXED_RACE_TRAIT_EXP = 50;

    private final PlayerCharacterRepository characterRepository;
    private final CharacterSheetRepository sheetRepository;
    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final RaceRepository raceRepository;
    private final TraitRepository traitRepository;
    private final CharacterRaceRepository characterRaceRepository;
    private final CharacterAttributeRepository attributeRepository;
    private final CharacterSkillRepository characterSkillRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final CharacterExpAwardRepository expAwardRepository;
    private final CharacterInventoryRepository inventoryRepository;
    private final CharacterAccess access;
    private final CharacterAssembler assembler;
    private final CurrentUser currentUser;

    /** Wires the repositories and helpers. */
    public CharacterServiceImpl(PlayerCharacterRepository characterRepository,
            CharacterSheetRepository sheetRepository,
            SkillRepository skillRepository,
            JobRepository jobRepository,
            RaceRepository raceRepository,
            TraitRepository traitRepository,
            CharacterRaceRepository characterRaceRepository,
            CharacterAttributeRepository attributeRepository,
            CharacterSkillRepository characterSkillRepository,
            CharacterTraitRepository characterTraitRepository,
            CharacterExpAwardRepository expAwardRepository,
            CharacterInventoryRepository inventoryRepository,
            CharacterAccess access,
            CharacterAssembler assembler,
            CurrentUser currentUser) {
        this.characterRepository = characterRepository;
        this.sheetRepository = sheetRepository;
        this.skillRepository = skillRepository;
        this.jobRepository = jobRepository;
        this.raceRepository = raceRepository;
        this.traitRepository = traitRepository;
        this.characterRaceRepository = characterRaceRepository;
        this.attributeRepository = attributeRepository;
        this.characterSkillRepository = characterSkillRepository;
        this.characterTraitRepository = characterTraitRepository;
        this.expAwardRepository = expAwardRepository;
        this.inventoryRepository = inventoryRepository;
        this.access = access;
        this.assembler = assembler;
        this.currentUser = currentUser;
    }

    // ----------------------------
    // Reference data and lists
    // ----------------------------

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "characterReferenceData", key = "'all'")
    public CharacterReferenceDataDto getReferenceData() {
        List<CharacterSheetDto> sheets = sheetRepository.findAllByOrderBySortOrderAsc().stream()
                .map(s -> new CharacterSheetDto(s.getId().toString(), s.getName(), s.getDescription(), s.getBaseBody(),
                        s.getBaseStrength(), s.getBaseSpeed(), s.getBaseMagic()))
                .toList();
        List<SkillDto> skills = skillRepository.findAllByOrderBySortOrderAsc().stream()
                .map(s -> new SkillDto(s.getId().toString(), s.getName(), s.getCategory(), s.getBaseRating(),
                        s.getWeaponTypeId() == null ? null : s.getWeaponTypeId().toString()))
                .toList();
        return new CharacterReferenceDataDto(sheets, skills);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacterSummaryDto> listMine() {
        List<PlayerCharacter> characters = characterRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.requireId());
        Map<UUID, String> jobNames = jobRepository.findAllById(characters.stream().map(PlayerCharacter::getJobId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Job::getId, Job::getJobName));
        List<CharacterSummaryDto> result = new ArrayList<>();
        for (PlayerCharacter c : characters) {
            List<UUID> raceIds = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(c.getId()).stream()
                    .map(r -> r.getId().getChildId()).toList();
            Map<UUID, Race> races = raceRepository.findAllById(raceIds).stream()
                    .collect(Collectors.toMap(Race::getId, Function.identity()));
            result.add(new CharacterSummaryDto(c.getId().toString(), c.getName(), c.getStatus(),
                    c.getJobId() == null ? null : jobNames.get(c.getJobId()),
                    raceIds.stream().map(races::get).filter(Objects::nonNull).map(Race::getRaceName).toList(),
                    String.valueOf(c.getUpdatedAt())));
        }
        return result;
    }

    // ----------------------------
    // Character
    // ----------------------------

    @Override
    @Transactional
    public CharacterDto create(CreateCharacterRequest request) {
        PlayerCharacter c = new PlayerCharacter();
        c.setUserId(currentUser.requireId());
        c.setName(request.name().trim());
        c.setCharacterSheetId(requireSheet(request.character_sheet_id()));
        c.setJobId(requireJob(request.job_id()));
        c.setSize(request.size());
        if (request.starting_exp() != null) c.setStartingExp(request.starting_exp());
        if (request.job_points() != null) c.setJobPoints(request.job_points());
        applyText(c, request.description(), request.motive(), request.ideals(), request.strengths(), request.flaws(),
                request.learned_languages(), request.history(), request.important_characters(), request.notes());
        c = characterRepository.saveAndFlush(c);

        UUID id = c.getId();
        for (CharacterAttributeName name : CharacterAttributeName.values()) {
            CharacterAttribute attribute = new CharacterAttribute();
            attribute.setId(new CharacterAttributeId(id, name));
            attributeRepository.save(attribute);
        }
        for (Skill skill : skillRepository.findAllByOrderBySortOrderAsc()) {
            CharacterSkill row = new CharacterSkill();
            row.setId(new CharacterChildId(id, skill.getId()));
            characterSkillRepository.save(row);
        }
        CharacterInventory inventory = new CharacterInventory();
        inventory.setCharacterId(id);
        inventoryRepository.save(inventory);
        access.requireWithinBudget(id);
        return assembler.toDto(c);
    }

    @Override
    @Transactional(readOnly = true)
    public CharacterDto get(String id) {
        return assembler.toDto(access.loadOwned(id));
    }

    @Override
    @Transactional
    public CharacterDto update(String id, UpdateCharacterRequest r) {
        PlayerCharacter c = access.loadOwned(id);
        if (r.name() != null) {
            if (r.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank");
            }
            c.setName(r.name().trim());
        }
        applyText(c, r.description(), r.motive(), r.ideals(), r.strengths(), r.flaws(), r.learned_languages(),
                r.history(), r.important_characters(), r.notes());
        if (r.character_sheet_id() != null) c.setCharacterSheetId(requireSheet(r.character_sheet_id()));
        if (r.size() != null) c.setSize(r.size());
        if (r.starting_exp() != null) c.setStartingExp(r.starting_exp());
        if (r.job_points() != null) c.setJobPoints(r.job_points());
        if (r.status() != null && r.status() != c.getStatus()) {
            if (r.status() == CharacterStatus.ACTIVE && c.getStatus() == CharacterStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A draft becomes active with POST /characters/{id}/finalize, which checks the creation rules");
            }
            c.setStatus(r.status());
        }
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
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    @Override
    @Transactional
    public void delete(String id) {
        characterRepository.delete(access.loadOwned(id));
    }

    // ----------------------------
    // Races and job
    // ----------------------------

    @Override
    @Transactional
    public CharacterDto setRaces(String id, SetCharacterRacesRequest request) {
        PlayerCharacter c = access.loadOwned(id);
        UUID characterId = c.getId();
        Set<UUID> wanted = new LinkedHashSet<>();
        for (String raceId : request.race_ids()) {
            wanted.add(CharacterAccess.parseUuid(raceId, "race"));
        }
        Map<UUID, Race> races = raceRepository.findAllById(wanted).stream()
                .collect(Collectors.toMap(Race::getId, Function.identity()));
        for (UUID raceId : wanted) {
            if (!races.containsKey(raceId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Race not found: " + raceId);
            }
        }
        UUID languageRace = CharacterAccess.parseOptionalUuid(request.language_race_id(), "language race");
        if (languageRace == null) {
            languageRace = wanted.size() == 1 ? wanted.iterator().next()
                    : wanted.contains(c.getLanguageRaceId()) ? c.getLanguageRaceId() : null;
        }
        if (languageRace != null && !wanted.contains(languageRace)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The language race must be one of the character's races");
        }
        if (languageRace == null && c.getStatus() != CharacterStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pick whose language set the character speaks (language_race_id)");
        }

        // Add new races first, then point the language at one that exists, then remove the old races
        // (removing a race also removes the racial traits it granted).
        List<CharacterRace> current = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(characterId);
        Set<UUID> existing = current.stream().map(r -> r.getId().getChildId()).collect(Collectors.toSet());
        int order = 0;
        for (UUID raceId : wanted) {
            CharacterRace row = existing.contains(raceId)
                    ? current.stream().filter(r -> r.getId().getChildId().equals(raceId)).findFirst().orElseThrow()
                    : new CharacterRace();
            if (!existing.contains(raceId)) {
                row.setId(new CharacterChildId(characterId, raceId));
            }
            row.setSortOrder(order++);
            characterRaceRepository.save(row);
        }
        characterRaceRepository.flush();
        c.setLanguageRaceId(languageRace);
        characterRepository.saveAndFlush(c);
        for (UUID raceId : existing) {
            if (!wanted.contains(raceId)) {
                characterRaceRepository.deleteRace(characterId, raceId);
            }
        }

        // A single race comes with all of its racial traits.
        if (wanted.size() == 1) {
            UUID raceId = wanted.iterator().next();
            Set<UUID> have = characterTraitRepository.findById_CharacterIdOrderByCreatedAtAsc(characterId).stream()
                    .map(t -> t.getId().getChildId()).collect(Collectors.toSet());
            for (UUID traitId : characterRepository.findRaceTraitIds(raceId)) {
                if (!have.contains(traitId)) {
                    CharacterTrait trait = new CharacterTrait();
                    trait.setId(new CharacterChildId(characterId, traitId));
                    trait.setSource(CharacterTraitSource.RACIAL);
                    trait.setRaceId(raceId);
                    characterTraitRepository.save(trait);
                }
            }
        }
        access.requireWithinBudget(characterId);
        return assembler.toDto(characterRepository.findById(characterId).orElseThrow());
    }

    @Override
    @Transactional
    public CharacterDto setJob(String id, SetCharacterJobRequest request) {
        PlayerCharacter c = access.loadOwned(id);
        UUID jobId = requireJob(request.job_id());
        if (Objects.equals(jobId, c.getJobId())) {
            return assembler.toDto(c);
        }
        if (jobId == null && c.getStatus() != CharacterStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An active character needs a job");
        }
        UUID characterId = c.getId();
        // The previous job's free traits go first (the database refuses a job change while they exist).
        characterTraitRepository.deleteBySource(characterId, CharacterTraitSource.JOB);
        c = characterRepository.findById(characterId).orElseThrow();
        c.setJobId(jobId);
        characterRepository.saveAndFlush(c);
        access.requireWithinBudget(characterId);
        return assembler.toDto(c);
    }

    // ----------------------------
    // Attributes, skills, EXP
    // ----------------------------

    @Override
    @Transactional
    public CharacterDto updateAttribute(String id, String attribute, UpdateCharacterAttributeRequest r) {
        PlayerCharacter c = access.loadOwned(id);
        CharacterAttributeName name = parseAttribute(attribute);
        CharacterAttribute row = attributeRepository.findById(new CharacterAttributeId(c.getId(), name))
                .orElseGet(() -> {
                    CharacterAttribute created = new CharacterAttribute();
                    created.setId(new CharacterAttributeId(c.getId(), name));
                    return created;
                });
        if (r.exp_spent() != null) row.setExpSpent(r.exp_spent());
        if (r.job_points_spent() != null) row.setJobPointsSpent(r.job_points_spent());
        if (r.bonus() != null) row.setBonus(r.bonus());
        if (r.modifier() != null) row.setModifier(r.modifier());
        attributeRepository.save(row);
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    @Override
    @Transactional
    public CharacterDto updateSkill(String id, String skillId, UpdateCharacterSkillRequest r) {
        PlayerCharacter c = access.loadOwned(id);
        UUID skill = CharacterAccess.parseUuid(skillId, "skill");
        if (!skillRepository.existsById(skill)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
        }
        CharacterSkill row = characterSkillRepository.findById(new CharacterChildId(c.getId(), skill))
                .orElseGet(() -> {
                    CharacterSkill created = new CharacterSkill();
                    created.setId(new CharacterChildId(c.getId(), skill));
                    return created;
                });
        if (r.creation_adjustment() != null) row.setCreationAdjustment(r.creation_adjustment());
        if (r.exp_spent() != null) row.setExpSpent(r.exp_spent());
        if (r.job_points_spent() != null) row.setJobPointsSpent(r.job_points_spent());
        if (r.failures() != null) row.setFailures(r.failures());
        if (r.permanent_modifier() != null) row.setPermanentModifier(r.permanent_modifier());
        if (r.temporary_modifier() != null) row.setTemporaryModifier(r.temporary_modifier());
        characterSkillRepository.save(row);
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacterExpAwardDto> listExpAwards(String id) {
        PlayerCharacter c = access.loadOwned(id);
        return expAwardRepository.findByCharacterIdOrderByCreatedAtAsc(c.getId()).stream()
                .map(a -> new CharacterExpAwardDto(a.getId().toString(), a.getAmount(), a.getReason(),
                        a.getAwardedByUserId() == null ? null : a.getAwardedByUserId().toString(),
                        String.valueOf(a.getCreatedAt())))
                .toList();
    }

    @Override
    @Transactional
    public CharacterDto addExpAward(String id, CreateExpAwardRequest request) {
        PlayerCharacter c = access.loadOwned(id);
        if (request.amount() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The amount must not be 0");
        }
        CharacterExpAward award = new CharacterExpAward();
        award.setCharacterId(c.getId());
        award.setAmount(request.amount());
        award.setReason(request.reason() == null ? "" : request.reason().trim());
        award.setAwardedByUserId(currentUser.requireId());
        expAwardRepository.save(award);
        access.requireWithinBudget(c.getId());
        return assembler.toDto(c);
    }

    // ----------------------------
    // Finishing creation
    // ----------------------------

    @Override
    @Transactional(readOnly = true)
    public CreationCheckDto checkCreation(String id) {
        List<String> problems = creationProblems(access.loadOwned(id));
        return new CreationCheckDto(problems.isEmpty(), problems);
    }

    @Override
    @Transactional
    public CharacterDto finalizeCreation(String id) {
        PlayerCharacter c = access.loadOwned(id);
        if (c.getStatus() != CharacterStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft can be finished");
        }
        List<String> problems = creationProblems(c);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The character does not meet the creation rules yet: " + String.join("; ", problems));
        }
        c.setStatus(CharacterStatus.ACTIVE);
        characterRepository.saveAndFlush(c);
        return assembler.toDto(c);
    }

    /** Every creation rule the character still breaks, as sentences for the player. */
    private List<String> creationProblems(PlayerCharacter c) {
        List<String> problems = new ArrayList<>();
        UUID id = c.getId();
        if (c.getCharacterSheetId() == null) problems.add("Choose a character sheet");
        if (c.getJobId() == null) problems.add("Choose a job");

        List<UUID> raceIds = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(id).stream()
                .map(r -> r.getId().getChildId()).toList();
        List<Race> races = raceRepository.findAllById(raceIds);
        if (races.isEmpty()) {
            problems.add("Choose a race");
        } else {
            if (c.getLanguageRaceId() == null) problems.add("Choose whose language set the character speaks");
            if (c.getSize() == null) {
                problems.add("Choose a size");
            } else if (races.stream().noneMatch(r -> fits(c.getSize(), r))) {
                problems.add("Size " + c.getSize() + " is outside the size range of the character's races");
            }
        }

        CharacterBudgetDto budget = assembler.budget(id);
        if (budget.expRemaining() < 0) problems.add("EXP is overspent by " + (-budget.expRemaining()));
        if (budget.jobPointsRemaining() < 0) problems.add("Job points are overspent by " + (-budget.jobPointsRemaining()));
        if (budget.combatSkillAdjustment() != 0) {
            problems.add("Combat skill swaps must balance (now " + signed(budget.combatSkillAdjustment()) + ")");
        }
        if (budget.lifeSkillAdjustment() != 0) {
            problems.add("Life skill swaps must balance (now " + signed(budget.lifeSkillAdjustment()) + ")");
        }

        if (races.size() == 1) {
            Race race = races.get(0);
            Set<UUID> have = characterTraitRepository.findById_CharacterIdOrderByCreatedAtAsc(id).stream()
                    .filter(t -> t.getSource() == CharacterTraitSource.RACIAL)
                    .map(t -> t.getId().getChildId()).collect(Collectors.toSet());
            List<String> missing = new ArrayList<>();
            List<UUID> raceTraits = characterRepository.findRaceTraitIds(race.getId());
            Map<UUID, Trait> traits = traitRepository.findAllById(raceTraits).stream()
                    .collect(Collectors.toMap(Trait::getId, Function.identity()));
            for (UUID traitId : raceTraits) {
                if (!have.contains(traitId) && traits.containsKey(traitId)) {
                    missing.add(traits.get(traitId).getTraitName());
                }
            }
            if (!missing.isEmpty()) {
                problems.add("Missing racial traits of " + race.getRaceName() + ": " + String.join(", ", missing));
            }
        } else if (races.size() > 1 && budget.racialTraitCost() != MIXED_RACE_TRAIT_EXP) {
            problems.add("A mixed race picks racial traits worth " + MIXED_RACE_TRAIT_EXP + " EXP (now "
                    + budget.racialTraitCost() + ")");
        }
        return problems;
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private static boolean fits(RaceSize size, Race race) {
        RaceSize min = race.getMinRaceSize();
        RaceSize max = race.getMaxRaceSize() == null ? min : race.getMaxRaceSize();
        return min != null && size.ordinal() >= min.ordinal() && size.ordinal() <= max.ordinal();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private UUID requireSheet(String sheetId) {
        UUID id = CharacterAccess.parseOptionalUuid(sheetId, "character sheet");
        if (id != null && !sheetRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Character sheet not found");
        }
        return id;
    }

    private UUID requireJob(String jobId) {
        UUID id = CharacterAccess.parseOptionalUuid(jobId, "job");
        if (id != null && !jobRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job not found");
        }
        return id;
    }

    private static CharacterAttributeName parseAttribute(String value) {
        try {
            return CharacterAttributeName.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown attribute: " + value
                    + " (body, strength, speed, or magic)");
        }
    }

    /** Sets the identity text fields that are present (null leaves a field unchanged). */
    private static void applyText(PlayerCharacter c, String description, String motive, String ideals,
            String strengths, String flaws, String learnedLanguages, String history, String importantCharacters,
            String notes) {
        if (description != null) c.setDescription(description);
        if (motive != null) c.setMotive(motive);
        if (ideals != null) c.setIdeals(ideals);
        if (strengths != null) c.setStrengths(strengths);
        if (flaws != null) c.setFlaws(flaws);
        if (learnedLanguages != null) c.setLearnedLanguages(learnedLanguages);
        if (history != null) c.setHistory(history);
        if (importantCharacters != null) c.setImportantCharacters(importantCharacters);
        if (notes != null) c.setNotes(notes);
    }
}
