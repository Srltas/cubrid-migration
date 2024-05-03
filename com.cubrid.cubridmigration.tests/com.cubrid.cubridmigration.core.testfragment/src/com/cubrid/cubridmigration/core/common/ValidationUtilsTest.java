/*
 * Copyright (C) 2008 Search Solution Corporation.
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ValidationUtilsTest {

    @Test
    public void test_ValidationUtils() {
        assertTrue(ValidationUtils.isDouble("1.1"));
        assertTrue(ValidationUtils.isDouble("1.111111111111111"));
        assertFalse(ValidationUtils.isDouble("1.1111111.11111111"));
        assertFalse(ValidationUtils.isDouble("1.1111111.111111a11"));
        assertFalse(ValidationUtils.isDouble("a"));
        assertFalse(ValidationUtils.isDouble(" "));
        assertFalse(ValidationUtils.isDouble(null));

        assertTrue(ValidationUtils.isInteger("1"));
        assertTrue(ValidationUtils.isInteger("1121"));
        assertFalse(ValidationUtils.isInteger("1.1111111.11111111"));
        assertFalse(ValidationUtils.isInteger("1.1111111.111111a11"));
        assertFalse(ValidationUtils.isInteger("a"));
        assertFalse(ValidationUtils.isInteger(" "));
        assertFalse(ValidationUtils.isInteger(null));

        assertTrue(ValidationUtils.isIP("1.1.1.1"));
        assertTrue(ValidationUtils.isIP("223.255.1.11"));
        assertFalse(ValidationUtils.isIP("1.1.a.1"));
        assertFalse(ValidationUtils.isIP("1.266.1.1"));
        assertFalse(ValidationUtils.isIP(" "));
        assertFalse(ValidationUtils.isIP(null));

        assertTrue(ValidationUtils.isPositiveDouble("1.1"));
        assertTrue(ValidationUtils.isPositiveDouble("1.111111111111111"));
        assertFalse(ValidationUtils.isPositiveDouble("-1.111111111111111"));
        assertFalse(ValidationUtils.isPositiveDouble("1.1111111.11111111"));
        assertFalse(ValidationUtils.isPositiveDouble("1.1111111.111111a11"));
        assertFalse(ValidationUtils.isPositiveDouble("a"));
        assertFalse(ValidationUtils.isPositiveDouble(" "));
        assertFalse(ValidationUtils.isPositiveDouble(null));

        assertTrue(ValidationUtils.isSciDouble("1.1"));
        assertTrue(ValidationUtils.isSciDouble("1.111111111111111"));
        assertTrue(ValidationUtils.isSciDouble("-1.111111111111111"));
        assertTrue(ValidationUtils.isSciDouble("1.111111111111111e+10"));
        assertFalse(ValidationUtils.isSciDouble("1.1111111.111111a11"));
        assertFalse(ValidationUtils.isSciDouble("a"));
        assertFalse(ValidationUtils.isSciDouble(" "));
        assertFalse(ValidationUtils.isSciDouble(null));

        assertFalse(ValidationUtils.isValidDBName(""));
        assertFalse(ValidationUtils.isValidDBName("a a"));
        assertFalse(ValidationUtils.isValidDBName("#"));
        assertFalse(ValidationUtils.isValidDBName("-"));
        assertFalse(ValidationUtils.isValidDBName("."));
        assertFalse(ValidationUtils.isValidDBName(".."));
        assertTrue(ValidationUtils.isValidDBName("a-a"));
        assertTrue(ValidationUtils.isValidDBName("A-Z-"));

        assertTrue(ValidationUtils.isValidDbNameLength("aaaaaaaaaaaaaaaaa"));
        assertFalse(ValidationUtils.isValidDbNameLength("aaaaaaaaaaaaaaaaaa"));

        assertFalse(ValidationUtils.isValidPort("1023"));
        assertTrue(ValidationUtils.isValidPort("1025"));
        assertFalse(ValidationUtils.isValidPort("65536"));
        assertTrue(ValidationUtils.isValidPort("65535"));
        assertFalse(ValidationUtils.isValidPort("aaa"));
    }
}
