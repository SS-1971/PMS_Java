package com.sentrifugo.db.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CompetencyCategoryConverter implements AttributeConverter<CompetencyCategory, String> {

    @Override
    public String convertToDatabaseColumn(CompetencyCategory attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public CompetencyCategory convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CompetencyCategory.fromWire(dbData);
    }
}
