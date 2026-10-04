package com.sentrifugo.pms.db.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DTO -> entity mappings must never write the id or the audit/soft-delete
 * columns {@code BaseEntity} owns: Hibernate generates the id and Spring Data
 * auditing stamps the rest.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "modifiedBy", ignore = true)
@Mapping(target = "createdDate", ignore = true)
@Mapping(target = "modifiedDate", ignore = true)
@Mapping(target = "isActive", ignore = true)
public @interface IgnoreBaseEntityFields {
}
