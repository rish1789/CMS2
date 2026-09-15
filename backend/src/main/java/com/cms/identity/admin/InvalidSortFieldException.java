package com.cms.identity.admin;

/** Super Admin console redesign: the {@code sort} list query param isn't one of the entity's allow-listed sortable fields - never passed straight into a JPQL property path unvalidated. */
public class InvalidSortFieldException extends RuntimeException {

    public InvalidSortFieldException(String field, String allowed) {
        super("sort must be one of " + allowed + " (got '" + field + "')");
    }
}
