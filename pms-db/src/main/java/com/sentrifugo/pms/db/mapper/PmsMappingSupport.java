package com.sentrifugo.pms.db.mapper;

import com.sentrifugo.pms.db.enums.PmsCompetencyCategory;
import com.sentrifugo.pms.db.enums.PmsCycleStage;
import com.sentrifugo.pms.db.enums.PmsCycleStatus;
import com.sentrifugo.pms.db.enums.PmsCycleType;
import com.sentrifugo.pms.db.enums.PmsTargetType;
import com.sentrifugo.pms.db.enums.PmsTemplateStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

/**
 * Conversions shared by the other mappers: persisted enums are exposed to the
 * API by their lowercase wire form (DB stores the constant name), and an
 * unknown wire value fails with the contract's {@code VALIDATION_ERROR}.
 */
@Mapper(componentModel = "spring")
public interface PmsMappingSupport {

    default String toWire(PmsCycleStatus value) {
        return value == null ? null : value.wire();
    }

    default PmsCycleStatus toCycleStatus(String wire) {
        return wire == null ? null : PmsCycleStatus.fromWire(wire);
    }

    default String toWire(PmsCycleType value) {
        return value == null ? null : value.wire();
    }

    default PmsCycleType toCycleType(String wire) {
        return wire == null ? null : PmsCycleType.fromWire(wire);
    }

    default String toWire(PmsCycleStage value) {
        return value == null ? null : value.wire();
    }

    default PmsCycleStage toCycleStage(String wire) {
        return wire == null ? null : PmsCycleStage.fromWire(wire);
    }

    default String toWire(PmsTemplateStatus value) {
        return value == null ? null : value.wire();
    }

    default PmsTemplateStatus toTemplateStatus(String wire) {
        return wire == null ? null : PmsTemplateStatus.fromWire(wire);
    }

    default String toWire(PmsTargetType value) {
        return value == null ? null : value.wire();
    }

    /** Blank is treated as "not supplied" (the goal-template KPI override is optional). */
    default PmsTargetType toTargetType(String wire) {
        return wire == null || wire.isBlank() ? null : PmsTargetType.fromWire(wire);
    }

    default String toWire(PmsCompetencyCategory value) {
        return value == null ? null : value.wire();
    }

    default PmsCompetencyCategory toCompetencyCategory(String wire) {
        return wire == null ? null : PmsCompetencyCategory.fromWire(wire);
    }

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}
