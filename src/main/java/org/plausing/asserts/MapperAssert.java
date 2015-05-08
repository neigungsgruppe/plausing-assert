package org.plausing.asserts;

import junit.framework.AssertionFailedError;
import org.apache.log4j.Logger;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.lang.reflect.Field;
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
import static org.plausing.asserts.ReflectionUtil.instantiateType;

/**
 * Asserts that a function that maps an object of type SOURCE to an object of type TARGET has a plausible mapping logic.
 * <p>
 * <p>
 * MapperAssert asserts that the following assumptions are valid:
 * - Every field of the TARGET object will be set by the mapper.
 * - Every field of the SOURCE object will be be mapped to (at most) one field of the TARGET object.
 * - The whole range of every field of the SOURCE object can be processed by the mapper.
 * This includes null values for types with boxing / unboxing.
 * - If a field of the SOURCE object is set to value x, the corresponding TARGET field is set to exactly this value x.
 * <p>
 * MapperAssert relies on the following assumptions, but doesn't test them:
 * - Fields are independent of each other. Every field is tested in isolation, so the test will finish in O(n) time
 * with n = number of fields.
 */
public class MapperAssert<SOURCE, TARGET> extends AbstractAssert<MapperAssert<SOURCE, TARGET>, Function> {
    /** Logger. */
    private static Logger LOG = Logger.getLogger(MapperAssert.class);

    /** The mapperUnderTest function under Test */
    private final Function<SOURCE, TARGET> mapperUnderTest;

    /** A function that generates a new instance of the source class. */
    private Supplier<SOURCE> sourceSupplier;

    /** A target instance that is used as a reference */
    private TARGET targetReference;

    /** Test data container */
    private MapperAssertTestData testData;

    /** Fields that should not be mapped by the mapperUnderTest */
    private Set<String> excludedTargetFields;

    /** A lookup table of the elements being contained in collection fields. */
    private Map<String, Class> collectionElementTypes = new HashMap<String, Class>();


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
        this.excludedTargetFields = new  HashSet<String>();
        this.targetReference = null;

        // Default test values.
        Date testDate = Date.from(LocalDate.of(1977, 4, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        java.sql.Date testSqlDate = new java.sql.Date(testDate.getTime());
        String aTestString = "A test string.";

        whenUsingTestAndTrainingValuesForType(String.class, Arrays.asList(aTestString, null), aTestString);
        whenUsingTestAndTrainingValuesForType(int.class, Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0), 1);
        whenUsingTestAndTrainingValuesForType(Integer.class, Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1, 0, null), 1);
        whenUsingTestAndTrainingValuesForType(long.class, Arrays.asList(Long.MIN_VALUE, Long.MAX_VALUE, 1L, -1L, 0L), 1L);
        whenUsingTestAndTrainingValuesForType(Long.class, Arrays.asList(Long.MIN_VALUE, Long.MAX_VALUE, 1L, -1L, 0L, null), 1L);
        whenUsingTestAndTrainingValuesForType(double.class, Arrays.asList(Double.MIN_VALUE, Double.MAX_VALUE, 1.0, -1.0, 0.0), 1.0);
        whenUsingTestAndTrainingValuesForType(Double.class, Arrays.asList(Double.MIN_VALUE, Double.MAX_VALUE, 1.0, -1.0, 0.0, null), 1.0);
        whenUsingTestAndTrainingValuesForType(java.util.Date.class, Arrays.asList(testDate, null), testDate);
        whenUsingTestAndTrainingValuesForType(java.sql.Date.class, Arrays.asList(testSqlDate, null), testSqlDate);
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
     * @param ignoredTargetFields fields in the target class that should not tested.
     * @return reference to the MapperAssert.
     */
    public MapperAssert<SOURCE, TARGET> whenIgnoringTargetFields(String... ignoredTargetFields) {
        this.excludedTargetFields = new HashSet<String>(Arrays.asList(ignoredTargetFields));
        return myself;
    }

    /**
     * Sets the type of the collection elements of field fieldName to class type.
     *
     * @param fieldName
     * @param type
     * @return
     */
    public MapperAssert<SOURCE, TARGET> whenSettingCollectionElementType(String fieldName, Class type) {
        this.collectionElementTypes.put(fieldName, type);
        return myself;
    }

    /**
     * Sets the list of Enum.name() as test values for the field with name fieldName.
     *
     * @param fieldName name of the field
     * @param enumType  class of the enum to be used.
     * @param <E>       enum type
     * @return this.
     */
    public <E extends Enum> MapperAssert<SOURCE, TARGET> whenUsingEnumNamesAsTestValuesForField(String fieldName, Class<E> enumType) {
        List<String> testValues = toStringValuesList(enumType);
        return this.withTestAndTrainingValuesForField(fieldName, testValues, testValues.get(0));
    }


    /**
     * Sets a list of test values and a training value for the field with name fieldName.
     *
     * @param fieldName     name of the field.
     * @param testValues    list of test values
     * @param trainingValue learing value
     * @param <T>           type of test data
     * @return this.
     */
    private <T> MapperAssert<SOURCE, TARGET> withTestAndTrainingValuesForField(String fieldName, List<T> testValues, T trainingValue) {
        testData.TEST_VALUES_BY_FIELDNAME.put(fieldName, testValues);
        testData.LEARN_VALUES_BY_FIELDNAME.put(fieldName, trainingValue);
        return myself;
    }


    /**
     * Sets a list of test values and a training value for all fields with type aClass.
     *
     * @param type
     * @param testValues
     * @param trainingValue
     * @param <T>           Type of testValues and trainingValue
     * @return this.
     */
    public <T> MapperAssert<SOURCE, TARGET> whenUsingTestAndTrainingValuesForType(Class<T> type, List<T> testValues, T trainingValue) {
        testData.TEST_VALUES_BY_TYPE.put(type, testValues);
        testData.LEARN_VALUES_BY_TYPE.put(type, trainingValue);
        return myself;
    }

    /**
     * Sets the list of testValues and trainingValue from the instances of the enum enumClass.
     *
     * @param enumClass the enum.
     * @param <T>       the enum.
     * @return this.
     */
    public <T extends Enum> MapperAssert<SOURCE, TARGET> withTestAndTrainingValuesForEnumType(Class<T> enumClass) {
        return whenUsingTestAndTrainingValuesForType(enumClass, toValuesList(enumClass), enumClass.getEnumConstants()[0]);
    }


    public <SOURCE_PROPERTY, TARGET_PROPERTY> MapperAssert<SOURCE, TARGET> withPropertyMapping(String sourcePropertyName, String targetPropertyName, Function<SOURCE_PROPERTY, TARGET_PROPERTY> propertyMapping) {
        throw new NotImplementedException();
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
                    withTestAndTrainingValuesForEnumType((Class<Enum>) sourceField.getType());
                }
            }
        }
    }


    /**
     * Asserts that all fields in TARGET have been set by the mapper
     * except the fields that have been excluded by whenIgnoringTargetFields()
     *
     * @param targetFields        fields of TARGET
     * @param changedTargetFields fields that have been changed in the test.
     */
    protected void assertAllTargetFieldsAreMapped(ArrayList<Field> targetFields, Set<Field> changedTargetFields) {
        String unchangedTargetFieldNames =
                targetFields.stream()
                        .filter(targetField -> !changedTargetFields.contains(targetField)
                                && !excludedTargetFields.contains(targetField.getName()))
                        .map(Field::getName)
                        .collect(joining(", "));

        if (!"".equals(unchangedTargetFieldNames)) {
            fail("Unchanged target fields: " + unchangedTargetFieldNames);
        }
    }

    /**
     * Asserts that all fields map to corresponding fields in the target class with the expected values.
     *
     * @param sourceFields         list of all fields of the source class
     * @param sourceToTargetFields map
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws NoSuchFieldException
     * @throws java.sql.SQLException
     */
    private MapperAssert<SOURCE, TARGET> assertThatAllTestValuesAreMappedToTheirExpectedValues(
            List<Field> sourceFields, Map<Field, Field> sourceToTargetFields)
            throws IllegalAccessException, InstantiationException, NoSuchFieldException, SQLException {

        for (Field sourceField : sourceFields) {

            Field targetField = sourceToTargetFields.get(sourceField);
            if (targetField == null) continue;

            LOG.info(String.format("Testing field mapping: %s --> %s ... ", sourceField, targetField));

            boolean nonNullField = testData.NON_NULL_FIELDS.contains(sourceField.getName());

            for (Object v : getTestValuesForField(sourceField)) {
                boolean nullValueOrNonNullableField = v == null && nonNullField;
                if (nullValueOrNonNullableField) continue;
                LOG.info(String.format("Testing value: %s ... ", v));
                assertThatFieldIsMappedToExpectedValue(sourceField, targetField, v);
            }
        }

        return myself;
    }

    /**
     * Asserts that the tested value of the source field is  mapped to the target field correctly.
     *
     * @param sourceField the source field
     * @param targetField the target field
     * @param testedValue source value being tested
     * @return the MapperAssert
     * @throws SQLException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    @SuppressWarnings("unchecked")
    private <SOURCE_FIELD_TYPE, TARGET_FIELD_TYPE> MapperAssert<SOURCE, TARGET> assertThatFieldIsMappedToExpectedValue(Field sourceField, Field targetField, SOURCE_FIELD_TYPE testedValue) throws SQLException, IllegalAccessException, NoSuchFieldException {

        // get a new source reference and change the tested field's value
        SOURCE source = sourceSupplier.get();
        ReflectionUtil.setFieldValue(source, sourceField, testedValue);

        // apply the mapper to the source field and get the actualMappedValue value
        TARGET target = mapperUnderTest.apply(source);
        TARGET_FIELD_TYPE actualMappedValue = (TARGET_FIELD_TYPE) ReflectionUtil.getFieldValue(targetField, target);

        // guess correct mapping
        Class<Object> sourceElementType = collectionElementTypes.get(sourceField.getName());
        Class<Object> targetElementType = collectionElementTypes.get(targetField.getName());
        boolean isNonNullField = testData.NON_NULL_FIELDS.contains(sourceField.getName());
        TARGET_FIELD_TYPE expectedMappedValue = MappingOracle.guessTargetValue(testedValue, (Class<SOURCE_FIELD_TYPE>) sourceField.getType(), (Class<TARGET_FIELD_TYPE>) targetField.getType(), sourceElementType, targetElementType, testData.mappers, isNonNullField);
        // look for declared override for the mapping
        Optional<Object> override = getOverrideForExpectedFieldValue(sourceField.getName(), targetField.getName(), testedValue);

        // if there is an override, test with the override
        if (override!=null) {
            Assertions.assertThat(actualMappedValue)
                    .as(String.format("Error in mapping (with override) %s --> %s", sourceField.getName(), targetField.getName()))
                    .isEqualTo(override.orElse(null));
            return myself;
        }

        // else, test with the guessed value
        Assertions.assertThat(actualMappedValue)
                .as(String.format("Error in mapping %s --> %s", sourceField.getName(), targetField.getName()))
                .isEqualTo(expectedMappedValue);
        return myself;
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
                    && override.targetFieldName.equals(targetFieldName)) {
                return Optional.ofNullable((SOURCE_VALUE) override.map(sourceValue));
            }
        }
        return null;
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
    public <SOURCE_TYPE, TARGET_TYPE> MapperAssert<SOURCE, TARGET> withValueListMapper(final Class<SOURCE_TYPE> sourceClass, final Class<TARGET_TYPE> targetClass, final List<SOURCE_TYPE> sourceValues, final List<TARGET_TYPE> targetValues) {
        testData.mappers.put(new TypePair(sourceClass, targetClass),
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

    /**
     * Adds a mapper to the MapperAssert.
     *
     * @param mapper
     * @return
     */
    public <SOURCE_TYPE, TARGET_TYPE> MapperAssert<SOURCE, TARGET> withMapper(Mappers.Mapper mapper) {
        testData.mappers.put(mapper.typePair, mapper.mappingFunction);
        return myself;
    }

    /**
     * Learns the mapping by setting source field values and examining the target object for changed fields.
     *
     * @param sourceReference
     * @param targetReference
     * @param sourceFields
     * @param changedTargetFields
     * @param mapping
     * @param <SOURCE>
     * @param <TARGET>
     */
    private <SOURCE, TARGET> void learnMapping(SOURCE sourceReference, TARGET targetReference, ArrayList<Field> sourceFields, Set<Field> changedTargetFields, Map<Field, Field> mapping) {
        for (Field sourceField : sourceFields) {

            Set<Field> changedTargetFieldsByField = applyMapperToTestValues(sourceField, getTestValuesForField(sourceField));

            if (changedTargetFieldsByField.size() > 1) {
                String changedFieldsList = changedTargetFieldsByField.stream()
                        .map(f -> f.getName())
                        .collect(joining(", "));
                LOG.info(String.format("Mapping error: %s --> [%s]", sourceField.getName(), changedFieldsList));
                fail("Source field maps to more than one target fields. " + String.format("Mapping error: %s --> [%s]", sourceField.getName(), changedFieldsList));
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

    private <A, B> Set<Field> applyMapperToTestValues(Field field, List<?> testValues) {
        boolean isNonNullableField = testData.NON_NULL_FIELDS.contains(field.getName());
        return testValues.stream()
                .filter(testValue -> !(testValue == null && isNonNullableField))
                .flatMap(testValue -> setSourceFieldAndApplyMapper(field, testValue).stream())
                .collect(Collectors.toSet());
    }


    /**
     * Sets a source field to the testValue and applies the mapper.
     *
     * @param field
     * @param testValue
     * @return
     */
    private Set<Field> setSourceFieldAndApplyMapper(Field field, Object testValue) {
        SOURCE source = (SOURCE) sourceSupplier.get();
        TARGET target;

        try {
            ReflectionUtil.setFieldValue(source, field, testValue);
            target = mapperUnderTest.apply(source);
        } catch (Throwable e) {
            AssertionFailedError assertionFailedError = new AssertionFailedError("Exception while training the mapping using field " + field.getName() + " with value " + testValue);
            assertionFailedError.initCause(e);
            throw assertionFailedError;
        }

        Set<Field> changed = ReflectionUtil.getChangedFields(target, targetReference);
        return changed;
    }


    /**
     * Gets the list of test values for a field using the following strategies:
     * 1. Try to get test values by field name
     * 2. Try to get test values by type
     * 3. Try to generate test values from a spawning type
     *
     * @param field
     * @return
     */
    @SuppressWarnings("unchecked")
    public List getTestValuesForField(Field field) {
        // First, try to get test values by field name
        List testValues = testData.TEST_VALUES_BY_FIELDNAME.get(field.getName());
        if (testValues != null) return testValues;

        // Second, try to get test values by type
        Class<?> type = field.getType();
        testValues = testData.TEST_VALUES_BY_TYPE.get(type);
        if (testValues != null) return testValues;

        // Third, try to generate test values from a generating type
        for (Class generatingType : testData.TEST_VALUES_BY_TYPE.keySet()) {
            try {
                testValues = generateTestValuesFromGeneratingType(type, generatingType);
                return testValues;
            } catch (Exception e) {
                continue;
            }
        }

        // Test Collection Classes
        if (Collection.class.isAssignableFrom(type)) {
            try {
                Collection<Object> sourceReference = (Collection) field.get(sourceSupplier.get());

                // get source element type from declaration or from a element that
                // can be found in the source sourceReference.
                Class sourceCollectionElementType = collectionElementTypes.get(field.getName());
                if (sourceCollectionElementType == null) {
                    sourceCollectionElementType = inferElementTypeFromCollectionElements(field, sourceReference);
                }
                List<Object> testValuesForContainedType = testData.TEST_VALUES_BY_TYPE.get(sourceCollectionElementType);

                // test collections with one value each
                List<Collection> result = testValuesForContainedType.stream()
                        .map(tv -> {
                            Collection<Object> newInstance = instantiateType(sourceReference);
                            newInstance.add(tv);
                            return newInstance;
                        })
                        .collect(Collectors.toList());

                // test an empty collection
                result.add(instantiateType(sourceReference));

                // test a collection with all of the test values
                Collection<Object> newInstance = instantiateType(sourceReference);
                newInstance.addAll(testValuesForContainedType);
                result.add(newInstance);

                return result;

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }


        if (testValues == null)
            throw new IllegalArgumentException("Keine Testdaten fuer Typ " + type.getCanonicalName());

        return testValues;
    }

    private Class<?> inferElementTypeFromCollectionElements(Field field, Collection<Object> collection) {
        // find any object in the collection
        Class<?> elementTypeName = collection.stream()
                .findAny()
                .map(Object::getClass)
                .orElseThrow(() -> new IllegalArgumentException("Can't infer type parameter because collection " + field.getName() + " is empty."));
        return elementTypeName;
    }

    /**
     * Generates test values from a generating type.
     * <p>
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
        List<GENERATING_TYPE> generatingTestValues = testData.TEST_VALUES_BY_TYPE.get(generatingType);

        // Instantiate an object of type targetClass for each value of the generatingTestValues
        return generatingTestValues.stream()
                .map(v -> v == null ? null : instantiateType(targetClass, generatingType, v))
                .collect(Collectors.toList());

    }

    public MapperAssert<SOURCE, TARGET>   whenExcludingNullValuesInField(String fieldName) {
        this.testData.NON_NULL_FIELDS.add(fieldName);
        return myself;
    }


    public MapperAssert<SOURCE, TARGET> whenUsingMapper(OverrideMapping overrideMapping) {
        testData.overrideMappingValues.add(overrideMapping);
        return myself;
    }
}

