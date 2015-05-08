package org.plausing.asserts;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Created by Florian on 02.05.2015.
 */
public class Mappers {


    public static class Mapper {
        TypePair typePair;
        Function mappingFunction;

        public Mapper(TypePair typePair, Function mappingFunction) {
            this.typePair = typePair;
            this.mappingFunction = mappingFunction;
        }

        public <SOURCE_TYPE, TARGET_TYPE> Mapper(final Class<SOURCE_TYPE> sourceType, final Class<TARGET_TYPE> targetType, final List<SOURCE_TYPE> sourceValues, final List<TARGET_TYPE> targetValues) {
            this(new TypePair(sourceType, targetType),
                    (sv) -> {
                        // Test the list for matches
                        for (int co = 0; co < sourceValues.size(); co++) {
                            if (Objects.equals(sv, sourceValues.get(co))) {
                                return (TARGET_TYPE) targetValues.get(co);
                            }
                        }
                        throw new IllegalArgumentException(String.format("No mapping has been defined for type %s with value \"%s\"", sourceType.getCanonicalName(), sv));
                    }
            );
        }
    }

    public static Mapper IntegerToLongMapper = new Mapper(
            Integer.class, Long.class,
            Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0, null),
            Arrays.asList((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE, 1L, -1L, 0L, null));


}
