package com.example.robotoperator.state

import org.junit.Assert.*
import org.junit.Test

class LoginRoleTest {
    @Test fun onlyMatchingServerRolesCanEnterEachPortal() {
        assertTrue(matchesLoginRole("ADMIN", "ADMIN"))
        assertTrue(matchesLoginRole("USER", "USER"))
        assertFalse(matchesLoginRole("ADMIN", "USER"))
        assertFalse(matchesLoginRole("USER", "ADMIN"))
        assertFalse(matchesLoginRole("ADMIN", null))
        assertFalse(matchesLoginRole("USER", "UNKNOWN"))
        assertFalse(matchesLoginRole("UNKNOWN", "UNKNOWN"))
    }
}
