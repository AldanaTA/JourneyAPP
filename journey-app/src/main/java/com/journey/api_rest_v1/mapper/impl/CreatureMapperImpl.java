package com.journey.api_rest_v1.mapper.impl;

import java.util.Comparator;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.creatures.Creature;
import com.journey.api_rest_v1.domain.creatures.CreatureAbility;
import com.journey.api_rest_v1.domain.creatures.CreatureAttack;
import com.journey.api_rest_v1.domain.creatures.CreatureAugment;
import com.journey.api_rest_v1.domain.creatures.CreatureType;
import com.journey.api_rest_v1.domain.items.WeaponProperty;
import com.journey.api_rest_v1.dto.creature.CreateCreatureRequest;
import com.journey.api_rest_v1.dto.creature.CreatureResponseDto;
import com.journey.api_rest_v1.dto.creature.CreatureTypeDto;
import com.journey.api_rest_v1.dto.item.StatusConditionDto;
import com.journey.api_rest_v1.mapper.CreatureMapper;
import com.journey.api_rest_v1.mapper.PowerMapper;
import com.journey.api_rest_v1.mapper.TraitMapper;

/**
 * Default {@link CreatureMapper} implementation that maps fields by hand. Linked traits and powers are
 * converted with their own mappers so a creature embeds the same payload as the trait/power endpoints.
 */
@Component
public class CreatureMapperImpl implements CreatureMapper {

    private final TraitMapper traitMapper;
    private final PowerMapper powerMapper;

    /** Wires the trait and power mappers used for linked abilities. */
    public CreatureMapperImpl(TraitMapper traitMapper, PowerMapper powerMapper) {
        this.traitMapper = traitMapper;
        this.powerMapper = powerMapper;
    }

    /** Converts a creature entity into a response DTO (abilities and attacks in display order). */
    @Override
    public CreatureResponseDto CreateResponse(Creature creature) {
        return new CreatureResponseDto(
            creature.getId() == null ? null : creature.getId().toString(),
            creature.getName(),
            creature.getCreatureType().getName(),
            creature.getThreatLvl(),
            creature.getMinSize(),
            creature.getMaxSize(),
            creature.getDescription(),
            creature.getLanguages(),
            creature.getLore(),
            creature.getHp(),
            creature.getWp(),
            creature.getMp(),
            creature.getEp(),
            creature.getPrio(),
            creature.getMovement(),
            creature.getPd(),
            creature.getMd(),
            creature.getSkills().stream().map(CreatureMapperImpl::toSkill).toList(),
            creature.getAugments().stream()
                .sorted(Comparator.comparing((CreatureAugment a) -> a.getId().getKind())
                        .thenComparing(a -> a.getId().getTarget(), String.CASE_INSENSITIVE_ORDER))
                .map(a -> new CreatureResponseDto.Augment(
                        a.getId().getKind(),
                        a.getId().getTarget(),
                        a.getDamageType() == null ? null : ItemMapperImpl.toDamageType(a.getDamageType()),
                        a.getStatusCondition() == null ? null : new StatusConditionDto(
                                a.getStatusCondition().getName(), a.getStatusCondition().getDescription())))
                .toList(),
            creature.getAbilities().stream()
                .sorted(Comparator.comparing(CreatureAbility::getSortOrder))
                .map(this::toAbility)
                .toList(),
            creature.getAttacks().stream()
                .sorted(Comparator.comparing(CreatureAttack::getSortOrder))
                .map(CreatureMapperImpl::toAttack)
                .toList(),
            creature.getSource() == null ? null : creature.getSource().getSource_name()
        );
    }

    /** Converts a creature type entity into its DTO. */
    @Override
    public CreatureTypeDto CreateTypeResponse(CreatureType type) {
        return new CreatureTypeDto(
            type.getId().toString(),
            type.getName(),
            type.getDescription(),
            type.getSource() == null ? null : type.getSource().getSource_name()
        );
    }

    /** Returns the request unchanged (records are immutable). */
    @Override
    public CreateCreatureRequest CreateRequest(CreateCreatureRequest request) {
        return request;
    }

    private CreatureResponseDto.Ability toAbility(CreatureAbility ability) {
        return new CreatureResponseDto.Ability(
            ability.getTiming(),
            ability.getName(),
            ability.getEffect(),
            ability.getLvl(),
            ability.getUses(),
            ability.getMpCost(),
            ability.getEpCost(),
            ability.getTpCost(),
            ability.getTrait() == null ? null : traitMapper.CreateResponse(ability.getTrait()),
            ability.getPower() == null ? null : powerMapper.CreateResponse(ability.getPower())
        );
    }

    private static CreatureResponseDto.Attack toAttack(CreatureAttack attack) {
        return new CreatureResponseDto.Attack(
            attack.getName(),
            attack.getSkill(),
            attack.getRangeFt(),
            attack.getDamageDice(),
            attack.getDamageBonus(),
            ItemMapperImpl.toDamageType(attack.getDamageType()),
            attack.getMag(),
            attack.getProperties().stream()
                .sorted(Comparator.comparing(WeaponProperty::getName, String.CASE_INSENSITIVE_ORDER))
                .map(ItemMapperImpl::toWeaponProperty)
                .toList()
        );
    }

    private static CreatureResponseDto.Skill toSkill(Map<String, Object> skill) {
        Object rating = skill.get("rating");
        return new CreatureResponseDto.Skill(
            String.valueOf(skill.get("skill")),
            rating instanceof Number number ? number.intValue() : null
        );
    }
}
