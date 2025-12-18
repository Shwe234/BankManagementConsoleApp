package util;

import exceptions.validationException;

@FunctionalInterface
public interface Validations<T> {
    void validate(T value) throws validationException;
}
