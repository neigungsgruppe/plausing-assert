package org.plausing.asserts;

/**
 * Created by Florian on 15.02.2015.
 */
public class TypePair {

    public String sourceType;
    public String targetType;

    public TypePair(String sourceType, String targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypePair typePair = (TypePair) o;

        if (sourceType != null ? !sourceType.equals(typePair.sourceType) : typePair.sourceType != null)
            return false;
        if (targetType != null ? !targetType.equals(typePair.targetType) : typePair.targetType != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = sourceType != null ? sourceType.hashCode() : 0;
        result = 31 * result + (targetType != null ? targetType.hashCode() : 0);
        return result;
    }
}
