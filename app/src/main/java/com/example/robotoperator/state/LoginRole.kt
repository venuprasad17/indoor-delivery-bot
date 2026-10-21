package com.example.robotoperator.state

/** A portal choice never grants a role; only the authenticated server role counts. */
internal fun matchesLoginRole(selected: String, actual: String?): Boolean =
    selected in setOf("ADMIN", "USER") && selected.equals(actual, ignoreCase = true)
