package com.vivareal.search.api.service.parser.factory;

import com.vivareal.search.api.model.query.Field;
import com.vivareal.search.api.service.parser.IndexValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FieldFactory {

    @Autowired
    private IndexValidator indexValidator;

    public Field createField(List<String> names) {
        return createField(false, names);
    }

    public Field createField(Boolean not, Field field) {
        return createField(not, field.getNames());
    }

    private Field createField(boolean not, List<String> names) {
        Field field = new Field(not, names);
        indexValidator.validateField(field);
        return field;
    }

}
