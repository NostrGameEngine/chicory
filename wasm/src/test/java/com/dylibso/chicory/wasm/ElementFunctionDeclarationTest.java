package com.dylibso.chicory.wasm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/**
 * All element modes declare their function references; absent/invalid ones remain invalid.
 */
public class ElementFunctionDeclarationTest {
    @Test
    public void activeElementDeclaresSelfReference() {
        assertDoesNotThrow(() -> Parser.parse(module(0, 0)));
    }

    @Test
    public void passiveElementDeclaresSelfReference() {
        assertDoesNotThrow(() -> Parser.parse(module(1, 0)));
    }

    @Test
    public void declarativeElementStillDeclaresSelfReference() {
        assertDoesNotThrow(() -> Parser.parse(module(3, 0)));
    }

    @Test
    public void undeclaredSelfReferenceRemainsInvalid() {
        assertThrows(InvalidException.class, () -> Parser.parse(module(-1, 0)));
    }

    @Test
    public void outOfRangeReferenceRemainsInvalid() {
        assertThrows(InvalidException.class, () -> Parser.parse(module(0, 1)));
    }

    private static byte[] module(int elementMode, int reference) {
        // One function: (func (result funcref) (ref.func <reference>)). Its own index
        // is 0, so the pre-fix self-reference validation cannot accidentally be bypassed.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {0, 0x61, 0x73, 0x6d, 1, 0, 0, 0});
        section(bytes, 1, 1, 0x60, 0, 1, 0x70);
        section(bytes, 3, 1, 0);
        if (elementMode == 0) {
            section(bytes, 4, 1, 0x70, 0, 1);
            section(bytes, 9, 1, 0, 0x41, 0, 0x0b, 1, 0);
        } else if (elementMode >= 0) {
            section(bytes, 9, 1, elementMode, 0, 1, 0);
        }
        section(bytes, 10, 1, 4, 0, 0xd2, reference, 0x0b);
        return bytes.toByteArray();
    }

    private static void section(ByteArrayOutputStream bytes, int id, int... body) {
        bytes.write(id);
        bytes.write(body.length); // These fixed test sections fit one canonical ULEB byte.
        for (int value : body) {
            bytes.write(value);
        }
    }
}
