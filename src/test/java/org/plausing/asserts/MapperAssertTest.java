package org.plausing.asserts;

import junit.framework.AssertionFailedError;
import org.junit.ComparisonFailure;
import org.junit.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.plausing.asserts.PlausingAssertions.assertThat;

/**
 * Tests for {@link MapperAssert}.
 *
 * @author Florian Rodler
 */
public class MapperAssertTest {


    @Test
    public void should_pass_if_string_field_is_mapped_to_string_field() {
        // given a mapper that maps from String to String
        Function<A, B> mapper = (A a) -> {
            B b = new B();
            b.att1 = a.att1;
            return b;
        };

        // then
        assertThat(mapper).hasPlausibleMappingFor(A::new);
    }


    @Test
    public void should_fail_if_unmapped_fields_exist() {
        // given a mapper that doesn't map any field
        Function<A, B> mapper = a -> new B();

        try {
            // when testing the mapper
            assertThat(mapper).hasPlausibleMappingFor(A::new);
            fail("Wrong mapping hasn't been detected.");
        } catch (AssertionError e) {
            // then expect an AssertionFailedError
            assertThat(e).hasMessage("Unchanged target fields: att1");

        }
    }

    @Test
    public void should_pass_if_unmapped_field_is_contained_in_excluded_target_fields() throws Exception {
        // given a mapper that maps one source field to two target fields
        Function<B, A> mapper = (b) -> {
            A a = new A();
            a.att1 = b.att1;
            return a;
        };
        // when testing the mapper
        assertThat(mapper)
                .withExcludedTargetFields("att2")
                .hasPlausibleMappingFor(B::new);
    }


    @Test
    public void should_fail_when_value_is_not_mapped_exactly() {
        // given a mapper that modifies a field value
        Function<A, B> mapper = (A a) -> {
            B b = new B();
            b.att1 = a.att1 + "mod";
            return b;
        };

        try {
            // when testing the mapper
            assertThat(mapper).hasPlausibleMappingFor(A::new);
            fail("Wrong mapping hasn't been detected.");
        } catch (ComparisonFailure e) {
            // then expect an AssertionFailedError
            org.assertj.core.api.Assertions.assertThat(e)
                    .hasMessageContaining("Error in mapping")
                    .hasMessageContaining("att1")
                    .hasMessageContaining("but was:<\"A test string.[mod]\">");
        }
    }

    @Test
    public void should_fail_if_one_source_field_maps_to_two_target_fields() throws Exception {
        // given a mapper that maps one source field to two target fields
        Function<B, A> mapper = (b) -> {
            A a = new A();
            a.att1 = b.att1;
            a.att2 = b.att1;
            return a;
        };
        try {
            // when testing the mapper
            assertThat(mapper).hasPlausibleMappingFor(B::new);
            fail("Wrong mapping hasn't been detected.");
        } catch (AssertionError e) {
            // then expect an Assertion Error
            assertThat(e).hasMessageContaining("Source field maps to more than one target fields. Mapping error: att1 --> [att2, att1]");
        }
    }


    @Test
    public void should_pass_if_string_maps_to_enum_by_name() throws Exception {

        Function<SE, TE> mapper = source -> {
            TE target = new TE();
            if (source.stringValue == null) {
                target.enumValue = null;
            } else {
                target.enumValue = E.valueOf(source.stringValue);
            }
            return target;
        };

        assertThat(mapper)
                .withEnumNamesAsTestValuesForField("stringValue", E.class)
                .hasPlausibleMappingFor(SE::new);
    }


    @Test
    public void should_pass_if_enum_maps_to_enum_by_name() throws Exception {

        Function<TE, TE2> mapper = source -> {
            TE2 target = new TE2();
            if (source.enumValue == null) {
                target.enumValue = null;
            } else {
                target.enumValue = E2.valueOf(source.enumValue.name());
            }
            return target;
        };

        assertThat(mapper).hasPlausibleMappingFor(TE::new);
    }


    @Test
    public void should_fail_if_enum_maps_to_enum_with_errors() {

        Function<TE, TE2> mapper = source -> {
            TE2 target = new TE2();
            if (source.enumValue == null) {
                target.enumValue = null;
            } else {
                target.enumValue = E2.ec1; // Not a good mapping
            }
            return target;
        };

        try {
            assertThat(mapper)
                    .hasPlausibleMappingFor(TE::new);
        } catch (ComparisonFailure e) {
            assertThat(e).hasMessageContaining("expected:<ec[2]> but was:<ec[1]>");
        }
    }


    @Test
    public void should_pass_if_maps_from_unboxed_to_boxed_value() {
        Function<CInt, CInteger> boxingMapper = source -> {
            CInteger target = new CInteger();
            target.integerValue = source.intValue;
            return target;
        };

        assertThat(boxingMapper).hasPlausibleMappingFor(CInt::new);
    }

    @Test
    public void should_fail_if_maps_from_boxed_to_unboxed_value() {
        // given
        Function<CInteger, CInt> unboxingMapper = source -> {
            CInt target = new CInt();
            target.intValue = source.integerValue;
            return target;
        };

        try {
            // when
            assertThat(unboxingMapper).hasPlausibleMappingFor(CInteger::new);
            fail("Should throw AssertionFailedError");
        } catch (AssertionFailedError e) {
            // then expect AssertionFaildError
            assertThat(e)
                    .isInstanceOf(AssertionFailedError.class)
                    .hasMessageContaining("Exception while learning the mapping using field integerValue with value null")
                    .hasCauseInstanceOf(NullPointerException.class);
        }


    }


    /*---------------------------------------------------------------------------------------------------------------
        TEST DATA
      ---------------------------------------------------------------------------------------------------------------*/

    /**
     * Class with two String fields.
     */
    public static class A {
        public String att1;
        public String att2;
    }

    /**
     * Class with one String field.
     */
    public static class B {
        public String att1;
    }

    @SuppressWarnings("unused")
    public static class SE {
        private String stringValue;
    }

    public static class TE {
        private E enumValue;
    }

    @SuppressWarnings("unused")
    public static class TE2 {
        private E2 enumValue;
    }

    @SuppressWarnings("unused")
    public static enum E {
        ec1, ec2
    }

    @SuppressWarnings("unused")
    public static enum E2 {
        ec1, ec2
    }

    public static class CInteger {
        Integer integerValue = 0;
    }

    public static class CInt {
        int intValue;
    }

}
