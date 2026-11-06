package com.sharebite.app.domain.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InputValidatorTest {

    @Test
    public void emails() {
        assertNull(InputValidator.validateEmail("alice@example.com"));
        assertNull(InputValidator.validateEmail("  a.b+tag@sub.example.co.uk "));
        assertNotNull(InputValidator.validateEmail(""));
        assertNotNull(InputValidator.validateEmail("alice@"));
        assertNotNull(InputValidator.validateEmail("alice@example"));
        assertNotNull(InputValidator.validateEmail("alice example.com"));
    }

    @Test
    public void passwords() {
        assertNull(InputValidator.validatePassword("secret123"));
        assertNotNull(InputValidator.validatePassword("short1"));
        assertNotNull(InputValidator.validatePassword("onlyletters"));
        assertNotNull(InputValidator.validatePassword("12345678"));
        assertNotNull(InputValidator.validateConfirmation("secret123", "secret124"));
        assertNull(InputValidator.validateConfirmation("secret123", "secret123"));
    }

    @Test
    public void phones() {
        assertNull(InputValidator.validatePhone("+880 1712-345678"));
        assertNull(InputValidator.validatePhone("(020) 7946 0958"));
        assertNotNull(InputValidator.validatePhone("12345"));
        assertNotNull(InputValidator.validatePhone("call me"));
        assertNotNull(InputValidator.validatePhone("+1234567890123456"));
    }

    @Test
    public void names() {
        assertNull(InputValidator.validateFullName("Ada Lovelace"));
        assertNotNull(InputValidator.validateFullName(" "));
        assertNotNull(InputValidator.validateOrganizationName("", true));
        assertNull(InputValidator.validateOrganizationName("", false));
    }
}
