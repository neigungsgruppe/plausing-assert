package org.plausing.asserts;

/**
 * Created by Florian on 15.02.2015.
 */
public class TypePair {

    public Class sourceType;
    public Class targetType;

    public TypePair(Class sourceType, Class targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypePair typePair = (TypePair) o;

        if (!sourceType.equals(typePair.sourceType)) return false;
        if (!targetType.equals(typePair.targetType)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = sourceType.hashCode();
        result = 31 * result + targetType.hashCode();
        return result;
    }
}
