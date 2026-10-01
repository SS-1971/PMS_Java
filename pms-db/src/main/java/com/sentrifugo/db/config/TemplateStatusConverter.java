package com.sentrifugo.db.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TemplateStatusConverter implements AttributeConverter<TemplateStatus, String> {

    @Override
    public String convertToDatabaseColumn(TemplateStatus attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public TemplateStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TemplateStatus.fromWire(dbData);
    }
}
