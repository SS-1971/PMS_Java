package com.sentrifugo.db.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TargetTypeConverter implements AttributeConverter<TargetType, String> {

    @Override
    public String convertToDatabaseColumn(TargetType attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public TargetType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TargetType.fromWire(dbData);
    }
}
