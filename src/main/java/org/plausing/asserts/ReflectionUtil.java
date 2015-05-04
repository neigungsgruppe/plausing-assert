package org.plausing.asserts;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Utility methods.
 *
 *
 * Created by Florian on 15.02.2015.
 */
public class ReflectionUtil {


    /**
     * Ermittelt alle Felder eines Objekts.
     *
     * @param sourceReference
     * @return
     */
    static ArrayList<Field> getFields(Object sourceReference) {
        List<Field> fields = new ArrayList<Field>();
        Class c = sourceReference.getClass();
        do {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        } while (null != (c = c.getSuperclass()));

        ArrayList<Field> filteredFields = new ArrayList<Field>();
        for (Field field : fields) {
            if (!(field.getName().startsWith("COL_")
                    || field.getName().startsWith("ATT_")
                    || field.getName().startsWith("ENTITY_")
                    || field.getName().startsWith("TABLE_")
            ) && !isStatic(field)) {
                filteredFields.add(field);
            }
        }
        return filteredFields;
    }

    private static boolean isStatic(Field field) {
        return java.lang.reflect.Modifier.isStatic(field.getModifiers());
    }

    public static boolean isStatic(Method method) {
        return java.lang.reflect.Modifier.isStatic(method.getModifiers());
    }

    /**
     * Creates a new instance of type targetClass with the constructor that has type generatingType as its only parameter.
     *
     * @param targetClass
     * @param generatingType
     * @param generatingValue
     * @param <GENERATING_TYPE>
     * @param <TARGET>
     * @return
     */
    static  <GENERATING_TYPE, TARGET> TARGET instantiateType(Class<TARGET> targetClass, Class<GENERATING_TYPE> generatingType, GENERATING_TYPE generatingValue) {
        try {
            Constructor<TARGET> constructor = targetClass.getConstructor(generatingType);
            return constructor.newInstance(generatingValue);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Kein passender Constructor.");
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Kein passender Constructor.");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Kein passender Constructor.");
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Kein passender Constructor.");
        }
    }


    /**
     * Creates a supplier that produces new instances of sourceClass.
     *
     * @param sourceClass class of the object to be produced by the supplier
     * @param <SOURCE> class
     * @return Supplier that produces new instances of sourceClass
     */
    public static <SOURCE> Supplier<SOURCE> createSupplierFromClass(Class<SOURCE> sourceClass) {
        return () -> {
            try {
                return (SOURCE) sourceClass.newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Can't instantiate class " + sourceClass.getCanonicalName());
            }
        };
    }

    /**
     * Sets the value of a field in an object.
     *
     * @param object object to be used
     * @param field field to be used
     * @param fieldValue value to be set
     * @throws IllegalAccessException
     */
    public static void setFieldValue(Object object, Field field, Object fieldValue) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(object, fieldValue);
    }
}
