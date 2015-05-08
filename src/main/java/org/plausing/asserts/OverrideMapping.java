package org.plausing.asserts;

import java.util.function.Function;

/**
* Created by Florian on 17.02.2015.
*/
public class OverrideMapping<SOURCE_VALUE, TARGET_VALUE> {

    private final Function<SOURCE_VALUE, TARGET_VALUE> mapping;
    public String sourceFieldName;
    public String targetFieldName;


    public OverrideMapping(String sourceFieldName, String targetFieldName, Function<SOURCE_VALUE, TARGET_VALUE> mapping) {
        this.sourceFieldName = sourceFieldName;
        this.targetFieldName = targetFieldName;
        this.mapping = mapping;
    }

    public TARGET_VALUE map(SOURCE_VALUE sourceValue) {
        return mapping.apply(sourceValue);
    }
}
