package com.vivareal.search.api.model.query;

import com.google.common.base.Objects;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.springframework.util.CollectionUtils.isEmpty;

public class Field {
    private final Collection<String> names;

    public Field(final Collection<String> names) {
        this.names = isEmpty(names) ? emptyList() : names;
    }


    public String getName() {
        return this.names.stream().collect(joining("."));
    }

    public Collection<String> getNames() {
        return this.names;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        return Objects.equal(this.names, field.names);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.names);
    }
}
