package org.plausing.asserts;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Florian on 16.03.2015.
 */
public class GenericsTest {



    @Test
    public void testGenerics() throws NoSuchMethodException {

        List<String> ls = new ArrayList<String>();

        Method[] add = ls.getClass().getMethods();
        Arrays.stream(add).forEach(System.out::println);


    }

}
