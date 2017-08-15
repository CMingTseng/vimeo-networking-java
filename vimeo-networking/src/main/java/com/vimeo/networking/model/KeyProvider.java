package com.vimeo.networking.model;

import org.jetbrains.annotations.NotNull;

/**
 * An interface definition for a object that can be uniquely identified by a string key. This should be used by classes
 * that can have multiple instances which point to the same logical entity. The key will be used to determine if two
 * instances of a class (or different classes) represent the same entity.
 * <p>
 * Created by restainoa on 8/15/17.
 */
public interface KeyProvider {

    /**
     * Returns the unique key.
     *
     * @return the unique key, must not be null.
     */
    @NotNull
    String provideUniqueKey();

}
