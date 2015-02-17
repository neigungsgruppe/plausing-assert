package org.plausing.asserts;

/**
* Created by Florian on 17.02.2015.
*/
public class OverrideMapping<SOURCE, TARGET> {

    public SOURCE sourceValue;
    public TARGET targetValue;
    public String sourceFieldName;
    public String targetFieldName;


    public OverrideMapping(String sourceFieldName, String targetFieldName, SOURCE sourceValue, TARGET targetValue) {
        this.sourceFieldName = sourceFieldName;
        this.targetFieldName = targetFieldName;
        this.sourceValue = sourceValue;
        this.targetValue = targetValue;
    }
}
