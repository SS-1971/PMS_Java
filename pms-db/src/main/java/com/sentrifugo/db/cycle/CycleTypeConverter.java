package com.sentrifugo.db.cycle;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CycleTypeConverter implements AttributeConverter<CycleType, String> {

    @Override
    public String convertToDatabaseColumn(CycleType attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public CycleType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CycleType.fromWire(dbData);
    }
}
