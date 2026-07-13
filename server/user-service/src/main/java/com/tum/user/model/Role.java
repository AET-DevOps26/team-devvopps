package com.tum.user.model;

/**
 * Authorization role for a user.
 *
 * USER  — standard account, may use the AI/roadmap features.
 * ADMIN — may additionally access the admin panel and user management.
 */
public enum Role {
    USER,
    ADMIN
}
