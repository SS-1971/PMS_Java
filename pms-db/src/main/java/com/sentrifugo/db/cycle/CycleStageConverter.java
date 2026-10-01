package com.sentrifugo.db.cycle;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CycleStageConverter implements AttributeConverter<CycleStage, String> {

    @Override
    public String convertToDatabaseColumn(CycleStage attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public CycleStage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CycleStage.fromWire(dbData);
    }
}
