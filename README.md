Plausing: Plausibility Testing for Java based on AssertJ
========

Plausibility testing provides a way to test code in a details-agnostic way.

Plausibility testing for PojoMappers
=========

Plausing assumes:
- There is a mapping method that takes a SOURCE pojo and transforms it to a TARGET pojo.
- Every field in the TARGET pojo has exactly one corresponding field in SOURCE pojo.
- Every field in the SOURCE pojo is mapped to zero or one fields in the TARGET pojo.
- The mapping method accepts the whole range of input values.
