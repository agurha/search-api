package com.vivareal.search.api.service.parser.factory;

import com.vivareal.search.api.model.query.Field;
import com.vivareal.search.api.service.parser.IndexValidator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class FieldFactory {

    @Autowired
    private IndexValidator indexValidator;

    public Field createField(List<String> names) {
        Field field = new Field(names);
        indexValidator.validateField(field);
        return field;
    }

}
