package org.plausing.asserts;

import junit.framework.AssertionFailedError;
import org.apache.log4j.Logger;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Fail.fail;
import static org.plausing.asserts.ReflectionUtil.getFields;

/**
 * Asserts that a function that maps an object of type SOURCE to an object of type TARGET has a plausible mapping logic.
 *
 * <p/>
 * MapperAssert asserts that the following assumptions are valid:
 * - Every field of the TARGET object will be set by the mapper.
 * - Every field of the SOURCE object will be be mapped to (at most) one field of the TARGET object.
 * - The whole range of every field of the SOURCE object can be processed by the mapper.
 *   This includes null values for types with boxing / unboxing.
 * - If a field of the SOURCE object is set to value x, the corresponding TARGET field is set to exactly this value x.
 * <p/>
 * MapperAssert relies on the following assumptions, but doesn't test them:
 * - Fields are independent of each other. Every field is tested in isolation, so the test will finish in O(n) time
 *   with n = number of fields.
 */
public class MapperAssert<SOURCE, TARGET> extends AbstractAssert<MapperAssert<SOURCE, TARGET>, Function> {

    /* Test data. */
    public static final Date TEST_DATE = Date.from(LocalDate.of(1977, 4, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    /* Test data. */
    public static final java.sql.Date TEST_SQL_DATE = new java.sql.Date(TEST_DATE.getTime());
    /* Test data. */
    private static final String A_TEST_STRING = "A test string.";
    /* Logger. */
    private static Logger LOG = Logger.getLogger(MapperAssert.class);
    /* The mapperUnderTest function under Test */
    private final Function<SOURCE, TARGET> mapperUnderTest;
    /* Test data container */
    private MapperAssertTestData testData;
    /* Fields that should not be mapped by the mapperUnderTest */
    private Set<String> excludedTargetFields = new HashSet<String>();
    private Supplier<SOURCE> sourceSupplier;
    private TARGET targetReference = null;


    /**
     * Protected constructor. Use the builder {@link org.plausing.asserts.PlausingAssertions#assertThat(java.util.function.Function)} to get a new instance.
     *
     * @param mapperUnderTest mapper to be tested
     * @param selfType        self type to be used in class hierarchy.
     */
    protected MapperAssert(Function<SOURCE, TARGET> mapperUnderTest, Class<?> selfType) {
        super(mapperUnderTest, selfType);
        this.mapperUnderTest = mapperUnderTest;
        this.testData = new MapperAssertTestData();

        // Default test values.
        withTestAndLearningValuesForType(String.class, Arrays.asList(A_TEST_STRING, null), A_TEST_STRING);
        withTestAndLearningValuesForType("int", Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0), 1);
        withTestAndLearningValuesForType(Integer.class, Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0, null), 1);
        withTestAndLearningValuesForType("long", Arrays.asList(Long.MIN_VALUE, Long.MAX_VALUE, 1L, -1L, 0L), 1L);
        withTestAndLearningValuesForType(Long.class, Arrays.asList(Long.MIN_VALUE, Long.MAX_VALUE, 1L, -1L, 0L, null), 1L);
        withTestAndLearningValuesForType("double", Arrays.asList(Double.MIN_VALUE, Double.MAX_VALUE, 1.0, -1.0, 0.0), 1.0);
        withTestAndLearningValuesForType(Double.class, Arrays.asList(Double.MIN_VALUE, Double.MAX_VALUE, 1.0, -1.0, 0.0, null), 1.0);
        withTestAndLearningValuesForType(java.util.Date.class, Arrays.asList(TEST_DATE, null), TEST_DATE);
        withTestAndLearningValuesForType(java.sql.Date.class, Arrays.asList(TEST_SQL_DATE, null), TEST_SQL_DATE);
    }

    /*---------------------------------------------------------------------------------------------------------------
      Assertions
      ---------------------------------------------------------------------------------------------------------------*/

    /**
     * Asserts that the mapper maps SOURCE to TARGET in a plausible way.
     *
     * @param sourceSupplier Function that creates a new instance of SOURCE on demand.
     * @return this.
     */
    public MapperAssert<SOURCE, TARGET> hasPlausibleMappingFor(Supplier<SOURCE> sourceSupplier) {


        this.sourceSupplier = sourceSupplier;

        String message = "Testing Mapper " + sourceSupplier.get().getClass().getCanonicalName() + " --> ";

        // Get an instance for SOURCE and map it
        // This assumes that the SOURCE can be constructed and that
        // the mapping succeeds.
        SOURCE sourceReference;
        try {
            sourceReference = sourceSupplier.get();
        } catch (Throwable e) {
            throw new AssertionFailedError("Unable to get instance of source");
        }
        try {
            targetReference = mapperUnderTest.apply(sourceReference);
        } catch (Throwable e) {
            AssertionFailedError assertionFailedError = new AssertionFailedError("Exception while creating target reference");
            assertionFailedError.initCause(e);
            throw assertionFailedError;

        }

        LOG.info(message + targetReference.getClass().getCanonicalName());

        ArrayList<Field> sourceFields = getFields(sourceReference);
        ArrayList<Field> targetFields = getFields(targetReference);

        // Wir nehmen fuer Enums alle zulaessigen Werte als Test-Werte
        addEnumTestValues(sourceFields);

        // Wir lernen das Mapping und testen dabei, ob ein Feld auf mehrere Felder abgebildet wird.
        Set<Field> changedTargetFields = new HashSet<Field>();
        Map<Field, Field> mapping = new HashMap<Field, Field>();
        learnMapping(sourceReference, targetReference, sourceFields, changedTargetFields, mapping);

        // Wir pruefen, dass alle Target-Felder gemappt wurden.
        assertAllTargetFieldsAreMapped(targetFields, changedTargetFields);

        // Vierter Schritt: Wir pruefen, ob die Werte, die in einer Spalte enthalten sind,
        // exakt gleich gemappt werden.
        try {
            assertThatAllTestValuesAreMappedToTheirExpectedValues(sourceFields, mapping);
        } catch (IllegalAccessException | InstantiationException | NoSuchFieldException | SQLException e) {
            throw new AssertionFailedError(e.getMessage());
        }

        return myself;
    }

    /**
     * Alternative implementation of hasPlausibleMappingFor that takes a source class.
     *
     * @param sourceClass class to be used
     * @return this.
     */
    public MapperAssert<SOURCE, TARGET> hasPlausibleMappingFor(Class<SOURCE> sourceClass) {
        return hasPlausibleMappingFor(ReflectionUtil.createSupplierFromClass(sourceClass));
    }


    /*---------------------------------------------------------------------------------------------------------------
      Configuration
      ---------------------------------------------------------------------------------------------------------------*/

    /**
     * Excludes fields in the target object from being tested.
     *
     * @param excludedTargetFields
     * @return
     */
    public MapperAssert<SOURCE, TARGET> withExcludedTargetFields(Set<String> excludedTargetFields) {
        this.excludedTargetFields = excludedTargetFields;
        return myself;
    }

    /**
     * Excludes fields in the target object from being tested.
     *
     * @param excludedTargetFields
     * @return
     */
    public MapperAssert<SOURCE, TARGET> withExcludedTargetFields(String... excludedTargetFields) {
        this.excludedTargetFields = new HashSet<String>(Arrays.asList(excludedTargetFields));
        return myself;
    }


    /**
     * Sets the list of Enum.name() as test values for the field with name fieldName.
     *
     * @param fieldName name of the field
     * @param enumClass class of the enum to be used.
     * @param <E>       enum type
     * @return this.
     */
    public <E extends Enum> MapperAssert<SOURCE, TARGET> withEnumNamesAsTestValuesForField(String fieldName, Class<E> enumClass) {
        List<String> testValues = toStringValuesList(enumClass);
        return this.withTestAndLearningValuesForField(fieldName, testValues, testValues.get(0));
    }


    /**
     * Sets a list of test values and a learning value for the field with name fieldName.
     *
     * @param fieldName     name of the field.
     * @param testValues    list of test values
     * @param learningValue learing value
     * @param <T>           type of test data
     * @return this.
     */
    private <T> MapperAssert<SOURCE, TARGET> withTestAndLearningValuesForField(String fieldName, List<T> testValues, T learningValue) {
        testData.TEST_VALUES_BY_FIELDNAME.put(fieldName, testValues);
        testData.LEARN_VALUES_BY_FIELDNAME.put(fieldName, learningValue);
        return myself;
    }


    /**
     * Sets a list of test values and a learning value for all fields with type aClass.
     *
     * @param aClass
     * @param testValues
     * @param learningValue
     * @param <T>           Type of testValues and learningValue
     * @return this.
     */
    public <T> MapperAssert<SOURCE, TARGET> withTestAndLearningValuesForType(Class<T> aClass, List<T> testValues, T learningValue) {
        return withTestAndLearningValuesForType(aClass.getCanonicalName(), testValues, learningValue);
    }

    /**
     * Sets a list of test values and a learning value for all fields with type name typeName.
     *
     * @param typeName
     * @param testValues
     * @param learningValue
     * @param <T>           Type of testValues and learningValue
     * @return this.
     */

    public <T> MapperAssert<SOURCE, TARGET> withTestAndLearningValuesForType(String typeName, List<T> testValues, T learningValue) {
        testData.TEST_VALUES_BY_TYPE.put(typeName, testValues);
        testData.LEARN_VALUES_BY_TYPE.put(typeName, learningValue);
        return myself;
    }

    /**
     * Sets the list of testValues and learningValue from the instances of the enum enumClass.
     *
     * @param enumClass the enum.
     * @param <T>       the enum.
     * @return this.
     */
    public <T extends Enum> MapperAssert<SOURCE, TARGET> withTestAndLearningValuesForEnumType(Class<T> enumClass) {
        return withTestAndLearningValuesForType(enumClass.getCanonicalName(), toValuesList(enumClass), enumClass.getEnumConstants()[0]);
    }



    /*----------------------------------------------------------------------------------------------------------------
        HELPER METHODS
      ---------------------------------------------------------------------------------------------------------------*/

    /**
     * Adds enum test values for all types that don't have test values yet.
     *
     * @param sourceFields
     */
    protected void addEnumTestValues(ArrayList<Field> sourceFields) {
        for (Field sourceField : sourceFields) {
            if (!testData.TEST_VALUES_BY_TYPE.containsKey(sourceField.getType())) {
                if (Enum.class.isAssignableFrom(sourceField.getType())) {
                    withTestAndLearningValuesForEnumType((Class<Enum>) sourceField.getType());
                }
            }
        }
    }


    /**
     * Asserts that all fields in TARGET have been set by the mapper
     * except the fields that have been excluded by withExcludedTargetFields()
     *
     * @param targetFields        fields of TARGET
     * @param changedTargetFields fields that have been changed in the test.
     */
    protected void assertAllTargetFieldsAreMapped(ArrayList<Field> targetFields, Set<Field> changedTargetFields) {
        String unchangedTargetFieldNames =
                targetFields.stream()
                        .filter(targetField -> !changedTargetFields.contains(targetField)
                                && !excludedTargetFields.contains(targetField.getName()))
                        .map(f -> f.getName())
                        .collect(joining(", "));

        if (!"".equals(unchangedTargetFieldNames)) {
            fail("Unchanged target fields: " + unchangedTargetFieldNames);
        }
    }


    /**
     * Asserts that all fields map to corresponding fields in the target class with the expected values.
     *
     * @param sourceFields
     * @param sourceToTargetFields
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws NoSuchFieldException
     * @throws java.sql.SQLException
     */
    private MapperAssert<SOURCE, TARGET> assertThatAllTestValuesAreMappedToTheirExpectedValues(ArrayList<Field> sourceFields, Map<Field, Field> sourceToTargetFields) throws IllegalAccessException, InstantiationException, NoSuchFieldException, SQLException {
        for (Field sourceField : sourceFields) {
            Field targetField = sourceToTargetFields.get(sourceField);
            if (targetField == null) continue;

            LOG.info(String.format("Testing field mapping: %s --> %s ... ", sourceField, targetField));

            for (Object v : getTestValuesForField(sourceField)) {
                assertThatFieldIsMappedToExpectedValue(sourceField, targetField, v);
            }
        }

        return myself;
    }

    private void setFieldValue(SOURCE source, Field sourceField, Object v) throws IllegalAccessException {
        sourceField.setAccessible(true);
        sourceField.set(source, v);
    }


    private MapperAssert<SOURCE, TARGET> assertThatFieldIsMappedToExpectedValue(Field sourceField, Field targetField, Object v) throws SQLException, IllegalAccessException, NoSuchFieldException {

        SOURCE source = sourceSupplier.get();
        setFieldValue(source, sourceField, v);

        TARGET target = mapperUnderTest.apply(source);
        Object actual = getFieldValue(targetField, target);

        Object sourceValue = getFieldValue(sourceField, source);
        Object expected = getExpectedFieldValue(sourceField, targetField, sourceValue);
        Optional<Object> override = getOverrideForExpectedFieldValue(sourceField.getName(), targetField.getName(), sourceValue);

        if (override.isPresent()) {
            Assertions.assertThat(actual)
                    .as(String.format("Error in mapping (with override) %s --> %s: ", sourceField.getName(), targetField.getName()))
                    .isEqualTo(override.get());
            return myself;
        }

        Assertions.assertThat(actual)
                .as(String.format("Error in mapping %s --> %s: ", sourceField.getName(), targetField.getName()))
                .isEqualTo(expected);

        return myself;
    }

    private Object getFieldValue(Field targetField, Object object) throws IllegalAccessException {
        targetField.setAccessible(true);
        return targetField.get(object);
    }


    /**
     * .
     *
     * @param name
     * @param targetFieldName
     * @param sourceValue
     * @return
     */
    @SuppressWarnings("unchecked")
    private <SOURCE_VALUE> Optional<SOURCE_VALUE> getOverrideForExpectedFieldValue(String name, String targetFieldName, SOURCE_VALUE sourceValue) {
        for (OverrideMapping override : testData.overrideMappingValues) {
            if (override.sourceFieldName.equals(name)
                    && override.targetFieldName.equals(targetFieldName)
                    && ((override.sourceValue == null && sourceValue == null)
                    || (override.sourceValue != null && override.sourceValue.equals(sourceValue)))) {
                return Optional.ofNullable((SOURCE_VALUE) override.targetValue);
            }
        }
        return Optional.empty();
    }


    /**
     * Bildet ein einen Wert sourceValue, der in sourceField steht, auf einen Wert in targetField ab.
     * <p/>
     * Strategie:
     * (a) Einfaches Mapping bei identischen Datentypen
     * (b) Enum zu Enum-Mapping ueber den Name
     * (c) String zu Enum-Mapping ueber den Name
     *
     * @param sourceField
     * @param targetField
     * @param sourceValue
     * @return
     */
    private Object getExpectedFieldValue(Field sourceField, Field targetField, Object sourceValue) {
        Class sourceType = sourceField.getType();
        Class targetType = targetField.getType();

        Function valueMapper = testData.mappers.get(new TypePair(sourceType.getCanonicalName(), targetType.getCanonicalName()));
        Object expected = null;
        if (valueMapper != null) {
            expected = valueMapper.apply(sourceValue);
        } else if (valueMapper == null && Enum.class.isAssignableFrom(sourceType) && Enum.class.isAssignableFrom(targetType)) {
            expected = guessEnumToEnumMapping((Enum) sourceValue, (Class<Enum>) targetType);
        } else if (valueMapper == null && String.class.isAssignableFrom(sourceType) && Enum.class.isAssignableFrom(targetType)) {
            expected = guessStringToEnumMapping((String) sourceValue, (Class<Enum>) targetType);
        } else if (valueMapper == null && Enum.class.isAssignableFrom(sourceType) && String.class.isAssignableFrom(targetType)) {
            expected = guessEnumToStringMapping((Enum) sourceValue);
        } else if (targetType.isAssignableFrom(sourceType)) {
            expected = sourceValue;
        } else {
            try {
                expected = guessConstructorMapping(sourceValue, sourceType, targetType);
            } catch (Exception e) {
                expected = guessGetterMapping(sourceValue, sourceType, targetType);
            }
        }
        return expected;
    }

    private <SOURCE, TARGET> TARGET guessConstructorMapping(SOURCE sourceValue, Class<SOURCE> sourceType, Class<TARGET> targetType) {

        if (sourceValue == null) return null;
        return ReflectionUtil.instantiateType(targetType, sourceType, sourceValue);

    }

    private <SOURCE, TARGET> TARGET guessGetterMapping(SOURCE sourceValue, Class<SOURCE> sourceType, Class<TARGET> targetType) {
        if (sourceValue == null) return null;
        Method[] methods = sourceType.getMethods();
        for (Method method : methods) {
            if (targetType.equals(method.getReturnType())
                    && !ReflectionUtil.isStatic(method)
                    && method.getName().startsWith("get")) {
                try {
                    return (TARGET) method.invoke(sourceValue);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException();
                } catch (InvocationTargetException e) {
                    throw new IllegalArgumentException();
                }
            }
        }

        if (Long.class.equals(targetType)) {
            return (TARGET) guessGetterMapping(sourceValue, sourceType, long.class);
        }

        if (Integer.class.equals(targetType)) {
            return (TARGET) guessGetterMapping(sourceValue, sourceType, int.class);
        }

        if (Double.class.equals(targetType)) {
            return (TARGET) guessGetterMapping(sourceValue, sourceType, double.class);
        }

        throw new IllegalArgumentException();
    }

    private String guessEnumToStringMapping(Enum sourceValue) {
        if (sourceValue == null) return null;
        return sourceValue.name();
    }

    private <TARGET extends Enum> Object guessStringToEnumMapping(String sourceValue, Class<TARGET> targetType) {
        if (sourceValue == null) return null;
        return Enum.valueOf(targetType, sourceValue);
    }

    private <SOURCE extends Enum, TARGET extends Enum> Object guessEnumToEnumMapping(SOURCE sourceValue, Class<TARGET> targetType) {
        if (sourceValue == null) return null;
        String sourceString = sourceValue.name();
        return Enum.valueOf(targetType, sourceString);
    }

    public <SOURCE_PROPERTY, TARGET_PROPERTY> MapperAssert<SOURCE, TARGET> withPropertyMapping(String sourcePropertyName, String targetPropertyName, Function<SOURCE_PROPERTY, TARGET_PROPERTY> propertyMapping) {
        return myself;
    }


    private <T extends Enum> List<T> toValuesList(Class<T> enumClass) {
        List<T> result = Arrays.stream(enumClass.getEnumConstants()) // Enum-Werte als Array
                .filter(e -> !(testData.ENUM_NAMES_TO_IGNORE.contains(e.name()))) // Die Namen sind nicht in der zu ignorierenden Liste
                .collect(Collectors.toList());// In Liste konvertieren
        result.add(null); // Null als Wert zur Liste hinzufuegen
        return result;
    }

    /**
     * Erzeugt aus einem Enum die Liste seiner String-Werte inklusive null.
     *
     * @param enumClass Class des zu verwendenden Enums
     * @param <T>       Der Enum
     * @return Liste der String-Werte
     */
    private <T extends Enum> List<String> toStringValuesList(Class<T> enumClass) {
        List<String> result = Arrays.stream(enumClass.getEnumConstants()) // Enum-Werte als Array
                .filter(e -> !(testData.ENUM_NAMES_TO_IGNORE.contains(e.name()))) // Die Namen sind nicht in der zu ignorierenden Liste
                .map(Enum::name)
                .collect(Collectors.toList());// In Liste konvertieren
        result.add(null); // Null als Wert zur Liste hinzufuegen
        return result;
    }

    /**
     * Adds a mapperUnderTest to the MapperAssert that maps from a list of source values to a list of target values by index.
     *
     * @param sourceClass
     * @param targetClass
     * @param sourceValues
     * @param targetValues
     * @return
     */
    public MapperAssert<SOURCE, TARGET> withValueListMapper(final Class<SOURCE> sourceClass, final Class<TARGET> targetClass, final List<SOURCE> sourceValues, final List<TARGET> targetValues) {
        testData.mappers.put(new TypePair(sourceClass.getCanonicalName(), targetClass.getCanonicalName()),
                sourceValue -> {
                    // Test the list for matches
                    for (int co = 0; co < sourceValues.size(); co++) {
                        // Mapping for null values
                        if (sourceValue == null && sourceValues.get(co) == null) {
                            return (TARGET) targetValues.get(co);
                        } // Mapping for non-null values
                        else if (sourceValue.equals(sourceValues.get(co))) {
                            return (TARGET) targetValues.get(co);
                        }
                    }
                    throw new IllegalArgumentException("Kein Mapping definiert fuer " + sourceClass.getCanonicalName() + " mit " + sourceValue);
                }
        );
        return myself;
    }

    private <SOURCE, TARGET> void learnMapping(SOURCE sourceReference, TARGET targetReference, ArrayList<Field> sourceFields, Set<Field> changedTargetFields, Map<Field, Field> mapping) {
        for (Field sourceField : sourceFields) {

            Set<Field> changedTargetFieldsByField = shake(sourceReference, targetReference, sourceField, getTestValuesForField(sourceField));

            if (changedTargetFieldsByField.size() > 1) {
                String changedFieldsList = changedTargetFieldsByField.stream()
                        .map(f -> f.getName())
                        .collect(joining(", "));
                String message = String.format("Mapping error: %s --> [%s]", sourceField.getName(), changedFieldsList);
                LOG.info(message);
                fail("Source field maps to more than one target fields. " + message);
            }
            if (changedTargetFieldsByField.size() > 0) {
                Field changedTargetField = changedTargetFieldsByField.stream().findAny().get();
                LOG.info(String.format("Learned mapping: %s --> %s", sourceField.getName(), changedTargetField.getName()));
                mapping.put(sourceField, (Field) changedTargetField);
            }
            if (changedTargetFieldsByField.size() == 0) {
                LOG.info(String.format("No mapping: %s --> []", sourceField.getName()));
            }
            changedTargetFields.addAll(changedTargetFieldsByField);
        }
    }

    private <A, B> Set<Field> shake(Object sourceReference, Object targetReference, Field field, List<?> testValues) {
        Set<Field> changed = new HashSet<Field>();
        for (Object testValue : testValues) {
            changed.addAll(setSourceFieldAndApplyMapper(field, testValue));
        }
        return changed;
    }


    /**
     * Sets the source Field
     *
     * @param field
     * @param testValue
     * @return
     */
    private Set<Field> setSourceFieldAndApplyMapper(Field field, Object testValue) {
        SOURCE source = (SOURCE) sourceSupplier.get();
        TARGET target;

        try {
            setFieldValue(source, field, testValue);
            target = mapperUnderTest.apply(source);
        } catch (Throwable e) {
            AssertionFailedError assertionFailedError = new AssertionFailedError("Exception while learning the mapping using field " + field.getName() + " with value " + testValue);
            assertionFailedError.initCause(e);
            throw assertionFailedError;
        }

        Set<Field> changed = getChangedFields(target);
        return changed;
    }

    private <TARGET> Set<Field> getChangedFields(TARGET target) {
        Set<Field> result = new HashSet<Field>();
        ArrayList<Field> fields = getFields(targetReference);
        for (Field field : fields) {
            field.setAccessible(true);
            Object vReference;
            Object vTarget;

            try {
                vReference = field.get(targetReference);
                vTarget = field.get(target);
            } catch (Throwable e) {
                AssertionFailedError assertionFailedError = new AssertionFailedError("Exception while collecting changed fields: " + field.getName());
                assertionFailedError.initCause(e);
                throw assertionFailedError;
            }

            if (vReference == null && vTarget == null) {
                continue;
            } else if (vReference == null && vTarget != null) {
                result.add(field);
            } else if (!vReference.equals(vTarget)) {
                result.add(field);
            }
        }
        return result;
    }

    private List getTestValuesForField(Field field) {
        // First, try to get test values by field name
        List testValues = testData.TEST_VALUES_BY_FIELDNAME.get(field.getName());
        if (testValues != null) return testValues;

        // Second, try to get test values by type
        Class<?> type = field.getType();
        testValues = testData.TEST_VALUES_BY_TYPE.get(type.getCanonicalName());
        if (testValues != null) return testValues;

        // Third, try to generate test values from a spawning type
        for (String stringName : testData.TEST_VALUES_BY_TYPE.keySet()) {
            Class spawningClass;
            try {
                spawningClass = Class.forName(stringName);
            } catch (ClassNotFoundException e) {
                continue;
            }
            try {
                testValues = generateTestValuesFromGeneratingType(type, spawningClass);
                return testValues;
            } catch (Exception e) {
                continue;
            }
        }


        if (testValues == null)
            throw new IllegalArgumentException("Keine Testdaten fuer Typ " + type.getCanonicalName());

        return testValues;
    }

    /**
     * Generates test values from a generating type.
     * <p/>
     * Example:
     * Let generatingType be int. Then the test values are Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0
     * This method will take every test value and create a new instance of Class<TARGET> by calling
     * the constructor with the test value as a parameter.
     *
     * @param targetClass
     * @param generatingType
     * @param <GENERATING_TYPE>
     * @param <TARGET>
     * @return
     */
    @SuppressWarnings(value = "unchecked")
    public <GENERATING_TYPE, TARGET> List<TARGET> generateTestValuesFromGeneratingType(Class<TARGET> targetClass, Class<GENERATING_TYPE> generatingType) {
        // Get the values of the generating type from the testData
        List<GENERATING_TYPE> generatingTestValues = testData.TEST_VALUES_BY_TYPE.get(generatingType.getCanonicalName());

        // Instantiate an object of type targetClass for each value of the generatingTestValues
        return generatingTestValues.stream()
                .map(v -> v == null ? null : ReflectionUtil.instantiateType(targetClass, generatingType, v))
                .collect(Collectors.toList());

    }

}

