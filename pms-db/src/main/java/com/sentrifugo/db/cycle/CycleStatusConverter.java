package com.sentrifugo.db.cycle;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CycleStatusConverter implements AttributeConverter<CycleStatus, String> {

    @Override
    public String convertToDatabaseColumn(CycleStatus attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public CycleStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CycleStatus.fromWire(dbData);
    }
}
