package org.plausing.asserts;

import java.util.function.Supplier;

/**
 * Created by Florian on 04.05.2015.
 */
public class UtilException {

    @FunctionalInterface
    public interface Supplier_WithExceptions<T> {
        T get() throws Exception;
    }

    /**
     * rethrowSupplier(() -> new StringJoiner(new String(new byte[]{77, 97, 114, 107}, "UTF-8"))),
     */
    public static <T> Supplier<T> rethrowSupplier(Supplier_WithExceptions<T> function) {
        return () -> {
            try {
                return function.get();
            } catch (Exception exception) {
                throw (RuntimeException) exception;
            }
        };
    }

}
