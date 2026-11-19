package com.familymap.domain;

/**
 * A member's role in a circle. The creator starts as {@code ADMIN}; admins will manage
 * saved places (step 6). Every other member is a {@code MEMBER}.
 */
public enum FamilyRole {
    ADMIN,
    MEMBER
}
