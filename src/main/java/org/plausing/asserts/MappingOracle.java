package org.plausing.asserts;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by Florian on 04.05.2015.
 */
public class MappingOracle {

    public static final ImmutableMap<Class, Class> PRIMITVES_OF_BOXED_TYPES = ImmutableMap.<Class, Class>builder()
            .put(Long.class, long.class)
            .put(Integer.class, int.class)
            .put(Double.class, double.class)
            .build();
    private static Logger LOG = Logger.getLogger(MappingOracle.class);


    public static <SOURCE_FIELD_TYPE, TARGET_FIELD_TYPE> SOURCE_FIELD_TYPE guessUnboxingMapping(SOURCE_FIELD_TYPE sourceValue, Class<SOURCE_FIELD_TYPE> sourceType, Class targetType) {
        if (sourceType.equals(Integer.class) && targetType.equals(int.class)) {
            return sourceValue;
        }
        if (sourceType.equals(Long.class) && targetType.equals(long.class)) {
            return sourceValue;
        }
        if (sourceType.equals(Double.class) && targetType.equals(double.class)) {
            return sourceValue;
        }
        throw new IllegalArgumentException();
    }

    public static <SOURCE_FIELD_TYPE, TARGET_FIELD_TYPE> TARGET_FIELD_TYPE guessConstructorMapping(SOURCE_FIELD_TYPE sourceValue, Class<SOURCE_FIELD_TYPE> sourceType, Class<TARGET_FIELD_TYPE> targetType) {

        if (sourceValue == null) return null;
        return ReflectionUtil.instantiateType(targetType, sourceType, sourceValue);

    }

    /**
     * Tries to find a getter in the source class that returns the target type.
     * @param sourceValue
     * @param sourceType
     * @param targetType
     * @param <SOURCE_FIELD_TYPE>
     * @param <TARGET_FIELD_TYPE>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <SOURCE_FIELD_TYPE, TARGET_FIELD_TYPE> TARGET_FIELD_TYPE guessGetterMapping(SOURCE_FIELD_TYPE sourceValue, Class<SOURCE_FIELD_TYPE> sourceType, Class<TARGET_FIELD_TYPE> targetType, boolean nonNullField) {
        if (sourceValue == null) return null;
        Method[] methods = sourceType.getMethods();
        for (Method method : methods) {

            boolean methodIsPublic =   Modifier.isPublic(method.getModifiers());
            boolean returnTypeIsAssignable = method.getReturnType().isAssignableFrom(targetType);
            boolean returnTypeIsNotStatic = !ReflectionUtil.isStatic(method);
            boolean isGetterMethod = method.getName().matches("get.*|.*value.*|.*Value.*");
            boolean nonNullValueOrNullableField = !(sourceValue == null && nonNullField);

            if (methodIsPublic && returnTypeIsAssignable && returnTypeIsNotStatic && isGetterMethod && nonNullValueOrNullableField) {
                try {
                    return (TARGET_FIELD_TYPE) method.invoke(sourceValue);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException(e);
                } catch (InvocationTargetException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }

        // Try to match primitive types, too.
        Class primitiveType = PRIMITVES_OF_BOXED_TYPES.get(targetType);
        if (primitiveType != null) {
            return (TARGET_FIELD_TYPE) guessGetterMapping(sourceValue, sourceType, primitiveType, nonNullField);
        }

        // We tried hard, but...
        throw new IllegalArgumentException();
    }

    /**
     * Returns sourceValue.name() as a sensible guess for a mapping from an enum to a string.
     * Accepts null values.
     *
     * @param sourceValue the enum
     * @return the name of the enum or null.
     */

    public static String guessEnumToStringMapping( Enum sourceValue) {
        if (sourceValue == null) return null;
        return sourceValue.name();
    }


    /**
     * Returns an enum by its name.
     * Accepts null values.
     *
     * @param sourceValue name of the enum
     * @param targetType class of the enum to be produced
     * @param <TARGET_FIELD_TYPE> enum type
     * @return enum or null
     */
    public static <TARGET_FIELD_TYPE extends Enum> Object guessStringToEnumMapping(String sourceValue, Class<TARGET_FIELD_TYPE> targetType) {
        if (sourceValue == null) return null;
        return Enum.valueOf(targetType, sourceValue);
    }

    /**
     * Tries to map the source enum to the target enum matching by Enum.name().
     * Accepts null values.
     *
     * @param sourceValue source enum value
     * @param targetType target enum value
     * @param <SOURCE_FIELD_TYPE> source enum type
     * @param <TARGET_FIELD_TYPE> target enum type
     * @return the target enum or null
     */
    public static <SOURCE_FIELD_TYPE extends Enum, TARGET_FIELD_TYPE extends Enum<TARGET_FIELD_TYPE>> TARGET_FIELD_TYPE guessEnumToEnumMapping(SOURCE_FIELD_TYPE sourceValue, Class<TARGET_FIELD_TYPE> targetType) {
        if (sourceValue == null) return null;
        String sourceString = sourceValue.name();
        return Enum.valueOf(targetType, sourceString);
    }

    /**
     * Bildet ein einen Wert sourceValue, der in sourceField steht, auf einen Wert mit Typ targetType ab.
     * <p>
     * Strategie:
     * (a) Einfaches Mapping bei identischen Datentypen
     * (b) Enum zu Enum-Mapping ueber den Name
     * (c) String zu Enum-Mapping ueber den Name
     * (d) Collections mappen jedes Element ueber eine Strategie (a-d)
     *
     * @param sourceValue
     * @param sourceType
     * @param targetType
     * @param sourceElementType
     * @param targetElementType
     * @param registeredMappers
     * @return
     */
    @SuppressWarnings("unchecked")
    static <SOURCE_FIELD_TYPE, TARGET_FIELD_TYPE, SOURCE_ELEMENT_TYPE, TARGET_ELEMENT_TYPE>
           TARGET_FIELD_TYPE guessTargetValue(SOURCE_FIELD_TYPE sourceValue,
                                              Class<SOURCE_FIELD_TYPE> sourceType, Class<TARGET_FIELD_TYPE> targetType,
                                              Class<SOURCE_ELEMENT_TYPE> sourceElementType, Class<TARGET_ELEMENT_TYPE> targetElementType,
                                              HashMap<TypePair, Function> registeredMappers, boolean nonNullField) {

        // Use the registered mapper when there is one.
        Function registeredValueMapper = registeredMappers.get(new TypePair(sourceType, targetType));
        boolean hasRegisteredValueMapper = registeredValueMapper != null;
        if (hasRegisteredValueMapper) {
            return (TARGET_FIELD_TYPE) registeredValueMapper.apply(sourceValue);
        }

        // Gather information about the source and target types.
        boolean sourceIsEnum = Enum.class.isAssignableFrom(sourceType);
        boolean targetIsEnum = Enum.class.isAssignableFrom(targetType);
        boolean sourceIsString = String.class.isAssignableFrom(sourceType);
        boolean targetIsString = String.class.isAssignableFrom(targetType);
        boolean sourceIsCollection = Collection.class.isAssignableFrom(sourceType);
        boolean targetIsCollection = Collection.class.isAssignableFrom(targetType);
        boolean sourceTypeIsTargetType = targetType.isAssignableFrom(sourceType);

        // Collection mapping
        if (sourceIsCollection && targetIsCollection) {
            Class<Collection<SOURCE_ELEMENT_TYPE>> sourceCollectionType = (Class<Collection<SOURCE_ELEMENT_TYPE>>) sourceType;
            Class<Collection<TARGET_ELEMENT_TYPE>> targetCollectionType = (Class<Collection<TARGET_ELEMENT_TYPE>>) targetType;
            Collection<SOURCE_ELEMENT_TYPE> sourceValueAsCollection = (Collection<SOURCE_ELEMENT_TYPE>) sourceValue;
            return (TARGET_FIELD_TYPE) guessCollectionMapping(sourceValueAsCollection, sourceCollectionType, targetCollectionType, sourceElementType, targetElementType, registeredMappers);
        }
        // source and target are of the same type (or assignable)
        if (sourceTypeIsTargetType) {
            return (TARGET_FIELD_TYPE) sourceValue;
        }
        // source and target are (different) enums
        if (sourceIsEnum && targetIsEnum) {
            return (TARGET_FIELD_TYPE) guessEnumToEnumMapping((Enum) sourceValue, (Class<Enum>) targetType);
        }
        // map string to enum
        if (sourceIsString && targetIsEnum) {
            return (TARGET_FIELD_TYPE) guessStringToEnumMapping((String) sourceValue, (Class<Enum>) targetType);
        }
        // map enum to string
        if (sourceIsEnum && targetIsString) {
            return (TARGET_FIELD_TYPE) guessEnumToStringMapping((Enum) sourceValue);
        }

        // try to find a constructor in targetType that takes a single parameter of sourceType
        try {
            return (TARGET_FIELD_TYPE) guessConstructorMapping(sourceValue, sourceType, targetType);
        } catch (Exception e) {
            // constructor mapping wasn't successful.
        }

        // try to find a getter in source Type that returns a target type object.
        try {
            return (TARGET_FIELD_TYPE) guessGetterMapping(sourceValue, sourceType, targetType, nonNullField);
        } catch (Exception e) {
            // getter mapping wasn't successful.
        }
        return (TARGET_FIELD_TYPE) guessUnboxingMapping(sourceValue, sourceType, targetType);
    }


    static <SOURCE_ELEMENT_TYPE, TARGET_ELEMENT_TYPE> Collection guessCollectionMapping(Collection<SOURCE_ELEMENT_TYPE> sourceValue, Class<Collection<SOURCE_ELEMENT_TYPE>> sourceType, Class<Collection<TARGET_ELEMENT_TYPE>> targetType, Class<SOURCE_ELEMENT_TYPE> sourceElementType, Class<TARGET_ELEMENT_TYPE> targetElementType,
                                                                                       HashMap<TypePair, Function> registeredMappers) {

        Supplier<Collection> targetCollectionSupplier = UtilException.rethrowSupplier(() -> sourceValue.getClass().newInstance());

        try {
            return sourceValue.stream()
                    .map(collectionElement -> guessTargetValue(collectionElement, sourceElementType, targetElementType, null, null, registeredMappers, false))
                    .collect(Collectors.toCollection(targetCollectionSupplier));
        } catch (Exception e) {
            LOG.info("Collection mapping failed ", e);
        }

        return targetCollectionSupplier.get();
    }
}
