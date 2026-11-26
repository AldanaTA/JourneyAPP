package com.journey.api_rest_v1.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.characters.CharacterAttribute;
import com.journey.api_rest_v1.domain.characters.CharacterAttributeName;
import com.journey.api_rest_v1.domain.characters.CharacterPower;
import com.journey.api_rest_v1.domain.characters.CharacterRace;
import com.journey.api_rest_v1.domain.characters.CharacterSheet;
import com.journey.api_rest_v1.domain.characters.CharacterSkill;
import com.journey.api_rest_v1.domain.characters.CharacterTrait;
import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.domain.characters.Skill;
import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.character.CharacterAttributeDto;
import com.journey.api_rest_v1.dto.character.CharacterBudgetDto;
import com.journey.api_rest_v1.dto.character.CharacterCapacitiesDto;
import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.CharacterPowerDto;
import com.journey.api_rest_v1.dto.character.CharacterRaceDto;
import com.journey.api_rest_v1.dto.character.CharacterSkillDto;
import com.journey.api_rest_v1.dto.character.CharacterStatsDto;
import com.journey.api_rest_v1.dto.character.CharacterTraitDto;
import com.journey.api_rest_v1.dto.character.NamedRefDto;
import com.journey.api_rest_v1.repository.CharacterAttributeRepository;
import com.journey.api_rest_v1.repository.CharacterPowerRepository;
import com.journey.api_rest_v1.repository.CharacterRaceRepository;
import com.journey.api_rest_v1.repository.CharacterSheetRepository;
import com.journey.api_rest_v1.repository.CharacterSkillRepository;
import com.journey.api_rest_v1.repository.CharacterTraitRepository;
import com.journey.api_rest_v1.repository.JobRepository;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.repository.RaceRepository;
import com.journey.api_rest_v1.repository.SkillRepository;
import com.journey.api_rest_v1.repository.TraitRepository;

/**
 * Builds the full character sheet ({@link CharacterDto}). Budget and stats come from the database views; traits
 * and powers merge the character's edits over the compendium values and say which fields were edited.
 */
@Component
public class CharacterAssembler {

    private static final int MIN_SKILL_RATING = 5;

    private final PlayerCharacterRepository characterRepository;
    private final CharacterSheetRepository sheetRepository;
    private final JobRepository jobRepository;
    private final RaceRepository raceRepository;
    private final CharacterRaceRepository characterRaceRepository;
    private final CharacterAttributeRepository attributeRepository;
    private final SkillRepository skillRepository;
    private final CharacterSkillRepository characterSkillRepository;
    private final TraitRepository traitRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final PowerRepository powerRepository;
    private final CharacterPowerRepository characterPowerRepository;

    /** Wires the repositories the sheet reads from. */
    public CharacterAssembler(PlayerCharacterRepository characterRepository,
            CharacterSheetRepository sheetRepository,
            JobRepository jobRepository,
            RaceRepository raceRepository,
            CharacterRaceRepository characterRaceRepository,
            CharacterAttributeRepository attributeRepository,
            SkillRepository skillRepository,
            CharacterSkillRepository characterSkillRepository,
            TraitRepository traitRepository,
            CharacterTraitRepository characterTraitRepository,
            PowerRepository powerRepository,
            CharacterPowerRepository characterPowerRepository) {
        this.characterRepository = characterRepository;
        this.sheetRepository = sheetRepository;
        this.jobRepository = jobRepository;
        this.raceRepository = raceRepository;
        this.characterRaceRepository = characterRaceRepository;
        this.attributeRepository = attributeRepository;
        this.skillRepository = skillRepository;
        this.characterSkillRepository = characterSkillRepository;
        this.traitRepository = traitRepository;
        this.characterTraitRepository = characterTraitRepository;
        this.powerRepository = powerRepository;
        this.characterPowerRepository = characterPowerRepository;
    }

    /** The whole sheet of a character (call inside a transaction, after the entity manager is flushed). */
    public CharacterDto toDto(PlayerCharacter c) {
        UUID id = c.getId();
        CharacterSheet sheet = c.getCharacterSheetId() == null ? null
                : sheetRepository.findById(c.getCharacterSheetId()).orElse(null);
        Job job = c.getJobId() == null ? null : jobRepository.findById(c.getJobId()).orElse(null);

        List<CharacterRace> characterRaces = characterRaceRepository.findById_CharacterIdOrderBySortOrderAsc(id);
        Map<UUID, Race> racesById = byId(raceRepository.findAllById(
                characterRaces.stream().map(r -> r.getId().getChildId()).toList()), Race::getId);
        List<CharacterRaceDto> races = characterRaces.stream()
                .map(r -> racesById.get(r.getId().getChildId()))
                .filter(Objects::nonNull)
                .map(r -> new CharacterRaceDto(r.getId().toString(), r.getRaceName(), r.getMinRaceSize(),
                        r.getMaxRaceSize(), r.getMovement(), r.getLanguages()))
                .toList();
        Race languageRace = c.getLanguageRaceId() == null ? null : racesById.get(c.getLanguageRaceId());

        return new CharacterDto(
                id.toString(), c.getName(), c.getStatus(),
                c.getDescription(), c.getMotive(), c.getIdeals(), c.getStrengths(), c.getFlaws(),
                c.getHistory(), c.getImportantCharacters(), c.getNotes(),
                sheet == null ? null : new NamedRefDto(sheet.getId().toString(), sheet.getName()),
                job == null ? null : new NamedRefDto(job.getId().toString(), job.getJobName()),
                c.getSize(),
                races,
                languageRace == null ? null : new NamedRefDto(languageRace.getId().toString(), languageRace.getRaceName()),
                languageRace == null ? null : languageRace.getLanguages(),
                c.getLearnedLanguages(),
                characterRepository.findSourceIds(id).stream().map(UUID::toString).toList(),
                c.getStartingExp(), c.getJobPoints(),
                new CharacterCapacitiesDto(c.getCurrentHp(), c.getCurrentWp(), c.getCurrentEp(), c.getCurrentMp(),
                        c.getTempHp(), c.getHpBonus(), c.getWpBonus(), c.getEpBonus(), c.getMpBonus(),
                        c.getPhysicalDamageBonus(), c.getMagicDamageBonus(), c.getPriorityBonus(), c.getMovementBonus()),
                stats(id),
                budget(id),
                attributes(id, sheet),
                skills(id),
                traits(id),
                powers(id),
                String.valueOf(c.getCreatedAt()), String.valueOf(c.getUpdatedAt()));
    }

    /** The budget from {@code character_point_budget}. */
    public CharacterBudgetDto budget(UUID characterId) {
        return characterRepository.findBudget(characterId)
                .map(b -> new CharacterBudgetDto(b.getTotalExp(), b.getAttributeExp(), b.getSkillExp(), b.getTraitExp(),
                        b.getPowerExp(), b.getExpRemaining(), b.getJobPoints(), b.getJobTraitPoints(),
                        b.getJobPointsOnStats(), b.getJobPointsRemaining(), b.getRaceCount(), b.getRacialTraitCost(),
                        b.getCombatSkillAdjustment(), b.getLifeSkillAdjustment()))
                .orElse(null);
    }

    private CharacterStatsDto stats(UUID characterId) {
        return characterRepository.findStats(characterId)
                .map(s -> new CharacterStatsDto(s.getBody(), s.getStrength(), s.getSpeed(), s.getMagic(), s.getMaxHp(),
                        s.getMaxWp(), s.getMaxEp(), s.getMaxMp(), s.getPhysicalDamage(), s.getMagicDamage(),
                        s.getPriority(), s.getMovementFt()))
                .orElse(null);
    }

    private List<CharacterAttributeDto> attributes(UUID characterId, CharacterSheet sheet) {
        Map<CharacterAttributeName, CharacterAttribute> rows = attributeRepository.findById_CharacterId(characterId).stream()
                .collect(Collectors.toMap(a -> a.getId().getAttribute(), Function.identity()));
        List<CharacterAttributeDto> result = new ArrayList<>();
        for (CharacterAttributeName name : CharacterAttributeName.values()) {
            CharacterAttribute a = rows.get(name);
            int base = sheet == null ? 0 : switch (name) {
                case BODY -> sheet.getBaseBody();
                case STRENGTH -> sheet.getBaseStrength();
                case SPEED -> sheet.getBaseSpeed();
                case MAGIC -> sheet.getBaseMagic();
            };
            int exp = a == null ? 0 : a.getExpSpent();
            int job = a == null ? 0 : a.getJobPointsSpent();
            int bonus = a == null ? 0 : a.getBonus();
            int modifier = a == null ? 0 : a.getModifier();
            result.add(new CharacterAttributeDto(name, base, exp, job, bonus, modifier, base + exp + job + bonus + modifier));
        }
        return result;
    }

    private List<CharacterSkillDto> skills(UUID characterId) {
        List<CharacterSkill> rows = characterSkillRepository.findById_CharacterId(characterId);
        Map<UUID, Skill> skillsById = byId(skillRepository.findAllById(
                rows.stream().map(s -> s.getId().getChildId()).toList()), Skill::getId);
        return rows.stream()
                .filter(row -> skillsById.containsKey(row.getId().getChildId()))
                .sorted(Comparator.comparing((CharacterSkill row) -> skillsById.get(row.getId().getChildId()).getSortOrder()))
                .map(row -> {
                    Skill skill = skillsById.get(row.getId().getChildId());
                    int rating = Math.max(MIN_SKILL_RATING, skill.getBaseRating() + row.getCreationAdjustment()
                            + row.getExpSpent() + row.getJobPointsSpent() + row.getPermanentModifier()
                            + row.getTemporaryModifier());
                    return new CharacterSkillDto(skill.getId().toString(), skill.getName(), skill.getCategory(), rating,
                            skill.getBaseRating(), row.getCreationAdjustment(), row.getExpSpent(), row.getJobPointsSpent(),
                            row.getFailures(), row.getPermanentModifier(), row.getTemporaryModifier());
                })
                .toList();
    }

    private List<CharacterTraitDto> traits(UUID characterId) {
        List<CharacterTrait> rows = characterTraitRepository.findById_CharacterIdOrderByCreatedAtAsc(characterId);
        Map<UUID, Trait> traitsById = byId(traitRepository.findAllById(
                rows.stream().map(t -> t.getId().getChildId()).toList()), Trait::getId);
        return rows.stream().filter(row -> traitsById.containsKey(row.getId().getChildId())).map(row -> {
            Trait t = traitsById.get(row.getId().getChildId());
            List<String> edited = new ArrayList<>();
            return new CharacterTraitDto(
                    t.getId().toString(), row.getSource(),
                    row.getRaceId() == null ? null : row.getRaceId().toString(),
                    row.getJobId() == null ? null : row.getJobId().toString(),
                    row.getLvl(), row.getExpSpent(), row.getJobPointsSpent(), row.getNotes(),
                    pick("trait_name", row.getTraitName(), t.getTraitName(), edited),
                    pick("purchase_cost", row.getPurchaseCost(), t.getPurchaseCost(), edited),
                    pick("lvl_up_cost", row.getLvlUpCost(), t.getLvlUpCost(), edited),
                    pick("trait_desc", row.getTraitDesc(), t.getTraitDesc(), edited),
                    pick("trait_uses", row.getTraitUses(), t.getTraitUses(), edited),
                    pick("mp_cost", row.getMpCost(), t.getMpCost(), edited),
                    pick("ep_cost", row.getEpCost(), t.getEpCost(), edited),
                    pick("tp_cost", row.getTpCost(), t.getTpCost(), edited),
                    pick("trait_effect", row.getTraitEffect(), t.getTraitEffect(), edited),
                    pick("lvl_up_effect", row.getLvlUpEffect(), t.getLvlUpEffect(), edited),
                    List.copyOf(edited));
        }).toList();
    }

    private List<CharacterPowerDto> powers(UUID characterId) {
        List<CharacterPower> rows = characterPowerRepository.findById_CharacterIdOrderByCreatedAtAsc(characterId);
        Map<UUID, Power> powersById = byId(powerRepository.findAllById(
                rows.stream().map(p -> p.getId().getChildId()).toList()), Power::getId);
        return rows.stream().filter(row -> powersById.containsKey(row.getId().getChildId())).map(row -> {
            Power p = powersById.get(row.getId().getChildId());
            List<String> edited = new ArrayList<>();
            return new CharacterPowerDto(
                    p.getId().toString(), row.getLvl(), row.getExpSpent(), row.getNotes(),
                    pick("name", row.getName(), p.getName(), edited),
                    pick("type", row.getType(), p.getType() == null ? null : p.getType().name(), edited),
                    pick("tp_cost", row.getTpCost(), p.getTpCost(), edited),
                    pick("hp_cost", row.getHpCost(), p.getHpCost(), edited),
                    pick("mp_cost", row.getMpCost(), p.getMpCost(), edited),
                    pick("ep_cost", row.getEpCost(), p.getEpCost(), edited),
                    pick("material_components", row.getMaterialComponents(), p.getMaterialComponents(), edited),
                    pick("verbal", row.getVerbal(), p.getVerbal(), edited),
                    pick("sight", row.getSight(), p.getSight(), edited),
                    pick("somatic", row.getSomatic(), p.getSomatic(), edited),
                    pick("is_distinct", row.getIsDistinct(), p.getIsDistinct(), edited),
                    pick("concentration", row.getConcentration(), p.getConcentration(), edited),
                    pick("range", row.getRange(), p.getRange(), edited),
                    pick("area", row.getArea(), p.getArea(), edited),
                    pick("duration", row.getDuration(), p.getDuration(), edited),
                    pick("effect", row.getEffect(), p.getEffect(), edited),
                    pick("empower_effect", row.getEmpowerEffect(), p.getEmpowerEffect(), edited),
                    pick("lvl_up_effect", row.getLvlUpEffect(), p.getLvlUpEffect(), edited),
                    List.copyOf(edited));
        }).toList();
    }

    /** The character's value when edited (and records the field name), otherwise the compendium value. */
    private static <T> T pick(String field, T characterValue, T compendiumValue, List<String> edited) {
        if (characterValue == null) {
            return compendiumValue;
        }
        edited.add(field);
        return characterValue;
    }

    private static <T> Map<UUID, T> byId(Iterable<T> entities, Function<T, UUID> idOf) {
        Map<UUID, T> map = new java.util.HashMap<>();
        for (T entity : entities) {
            map.put(idOf.apply(entity), entity);
        }
        return map;
    }
}
