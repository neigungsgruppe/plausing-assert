package org.plausing.asserts;

import java.util.function.Function;

/**
 * Entry Point for assertions in plausing-assert.
 *
 * @author Florian Rodler
 * @copyright Florian Rodler, 2015
 */
public class PlausingAssertions {

    /**
     * Creates a new instance of <code>{@link org.plausing.asserts.MapperAssert}</code>.
     * @param mapperFunction the mapper function to test
     * @param <SOURCE> source type of the mapper function
     * @param <TARGET> target type of the mapper function
     * @return new instance of <code>{@link org.plausing.asserts.MapperAssert}</code>.
     */
    public static <SOURCE, TARGET> MapperAssert<SOURCE, TARGET> assertThat(Function<SOURCE, TARGET> mapperFunction) {
        return new MapperAssert<SOURCE, TARGET>(mapperFunction, MapperAssert.class);
    }
}
