/*******************************************************************************
 * Copyright (c) 2014 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    EclipseSource - initial API and implementation
 ******************************************************************************/
package com.eclipsesource.v8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eclipsesource.v8.utils.V8Map;

public class V8Test {

    private V8 v8;

    @Before
    public void seutp() {
        v8 = V8.createV8Runtime();
    }

    @After
    public void tearDown() {
        try {
            v8.release();
            if (V8.getActiveRuntimes() != 0) {
                throw new IllegalStateException("V8Runtimes not properly released.");
            }
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testGetVersion() {
        String v8version = V8.getV8Version();

        assertNotNull(v8version);
    }

    @Test
    public void testGetVersion_StartsWith4() {
        String v8version = V8.getV8Version();

        assertTrue(v8version.startsWith("4"));
    }

    @Test
    public void testV8Setup() {
        assertNotNull(v8);
    }

    @Test
    public void testObjectReferenceZero() {
        long objectReferenceCount = v8.getObjectReferenceCount();

        assertEquals(0, objectReferenceCount);
    }

    @Test
    public void testObjectReferenceCountOne() {
        V8Object object = new V8Object(v8);

        long objectReferenceCount = v8.getObjectReferenceCount();

        assertEquals(1, objectReferenceCount);
        object.release();
    }

    @Test
    public void testObjectReferenceCountReleased() {
        V8Object object = new V8Object(v8);
        object.release();

        long objectReferenceCount = v8.getObjectReferenceCount();

        assertEquals(0, objectReferenceCount);
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateVoid() {
        v8.release();
        v8.executeVoidScript("");
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateInt() {
        v8.release();
        v8.executeIntegerScript("7");
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateString() {
        v8.release();
        v8.executeStringScript("'foo'");
    }

    @Test(expected = Error.class)
    public void testCannotAccessDisposedIsolateBoolean() {
        v8.release();
        v8.executeBooleanScript("true");
    }

    @Test
    public void testSingleThreadAccess() throws InterruptedException {
        final boolean[] result = new boolean[] { false };
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    v8.executeVoidScript("");
                } catch (Error e) {
                    result[0] = e.getMessage().contains("Invalid V8 thread access.");
                }
            }
        });
        t.start();
        t.join();

        assertTrue(result[0]);
    }

    @Test
    public void testMultiThreadAccess() throws InterruptedException {
        v8.add("foo", "bar");
        v8.getLocker().release();
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                v8.getLocker().acquire();
                v8.add("foo", "baz");
                v8.getLocker().release();
            }
        });
        t.start();
        t.join();
        v8.getLocker().acquire();

        assertEquals("baz", v8.getString("foo"));
    }

    @Test
    public void testIAENotThrownOnShutdown() {
        V8 v8_ = V8.createV8Runtime();

        new V8Object(v8_);
        v8_.release(false);
    }

    @Test(expected = IllegalStateException.class)
    public void testISEThrownOnShutdown() {
        V8 v8_ = V8.createV8Runtime();

        new V8Object(v8_);
        v8_.release(true);
    }

    @Test
    public void testReleaseAttachedObjects() {
        V8 runtime = V8.createV8Runtime();
        V8Object v8Object = new V8Object(v8);
        runtime.registerResource(v8Object);

        runtime.release(true);
    }

    @Test
    public void testReleaseSeveralAttachedObjects() {
        V8 runtime = V8.createV8Runtime();
        runtime.registerResource(new V8Object(runtime));
        runtime.registerResource(new V8Object(runtime));
        runtime.registerResource(new V8Object(runtime));

        runtime.release(true);
    }

    @Test
    public void testReleaseAttachedMap() {
        V8 runtime = V8.createV8Runtime();
        V8Map<String> v8Map = new V8Map<String>();
        V8Object v8Object = new V8Object(runtime);
        v8Map.put(v8Object, "foo");
        v8Object.release();
        runtime.registerResource(v8Map);

        runtime.release(true);
    }

    /*** Void Script ***/
    @Test
    public void testSimpleVoidScript() {
        v8.executeVoidScript("function foo() {return 1+1}");

        int result = v8.executeIntegerFunction("foo", null);

        assertEquals(2, result);
    }

    @Test
    public void testMultipleScriptCallsPermitted() {
        v8.executeVoidScript("function foo() {return 1+1}");
        v8.executeVoidScript("function bar() {return foo() + 1}");

        int foo = v8.executeIntegerFunction("foo", null);
        int bar = v8.executeIntegerFunction("bar", null);

        assertEquals(2, foo);
        assertEquals(3, bar);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSyntaxErrorInVoidScript() {
        v8.executeVoidScript("'a");
    }

    @Test
    public void testSyntaxErrorMissingParam() {
        try {
            v8.executeScript("foo());");
        } catch (V8ScriptCompilationException e) {
            String string = e.toString();
            assertNotNull(string);
            return;
        }
        fail("Exception expected.");
    }

    @Test
    public void testVoidScriptWithName() {
        v8.executeVoidScript("function foo() {return 1+1}", "name", 1);

        int result = v8.executeIntegerFunction("foo", null);

        assertEquals(2, result);
    }

    /*** Int Script ***/
    @Test
    public void testSimpleIntScript() {
        int result = v8.executeIntegerScript("1+2;");

        assertEquals(3, result);
    }

    @Test
    public void testIntScriptWithDouble() {
        int result = v8.executeIntegerScript("1.9+2.9;");

        assertEquals(4, result);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxError() {
        v8.executeIntegerScript("return 1+2");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionIntScript() {
        v8.executeIntegerScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeIntScript() {
        v8.executeIntegerScript("'test'");
    }

    @Test
    public void testIntScriptWithName() {
        int result = v8.executeIntegerScript("1+2;", "name", 2);

        assertEquals(3, result);
    }

    /*** Double Script ***/
    @Test
    public void testSimpleDoubleScript() {
        double result = v8.executeDoubleScript("3.14159;");

        assertEquals(3.14159, result, 0.00001);
    }

    @Test
    public void testDoubleScriptWithInt() {
        double result = v8.executeDoubleScript("1");

        assertEquals(1.0, result, 0.00001);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorInDoubleScript() {
        v8.executeDoubleScript("return 1+2");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionDoubleScript() {
        v8.executeDoubleScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeDoubleScript() {
        v8.executeDoubleScript("'test'");
    }

    @Test
    public void testDoubleScriptHandlesInts() {
        int result = (int) v8.executeDoubleScript("1");

        assertEquals(1, result);
    }

    @Test
    public void testDoubleScriptWithName() {
        double result = v8.executeDoubleScript("3.14159;", "name", 3);

        assertEquals(3.14159, result, 0.00001);
    }

    /*** Boolean Script ***/
    @Test
    public void testSimpleBooleanScript() {
        boolean result = v8.executeBooleanScript("true");

        assertTrue(result);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorInBooleanScript() {
        v8.executeBooleanScript("return 1+2");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionBooleanScript() {
        v8.executeBooleanScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeBooleanScript() {
        v8.executeBooleanScript("'test'");
    }

    @Test
    public void testBooleanScriptWithName() {
        boolean result = v8.executeBooleanScript("true", "name", 4);

        assertTrue(result);
    }

    /*** String Script ***/
    @Test
    public void testSimpleStringScript() {
        String result = v8.executeStringScript("'hello, world'");

        assertEquals("hello, world", result);
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorStringScript() {
        v8.executeStringScript("'a");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionStringScript() {
        v8.executeIntegerScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeStringScript() {
        v8.executeStringScript("42");
    }

    @Test
    public void testStringScriptWithName() {
        String result = v8.executeStringScript("'hello, world'", "name", 5);

        assertEquals("hello, world", result);
    }

    /*** Unknown Script ***/
    @Test
    public void testAnyScriptReturnedNothing() {
        V8Value result = (V8Value) v8.executeScript("");

        assertTrue(result.isUndefined());
    }

    @Test
    public void testAnyScriptReturnedNull() {
        Object result = v8.executeScript("null;");

        assertNull(result);
    }

    @Test
    public void testAnyScriptReturnedUndefined() {
        V8Value result = (V8Value) v8.executeScript("undefined;");

        assertTrue(result.isUndefined());
    }

    @Test
    public void testAnyScriptReturnInt() {
        Object result = v8.executeScript("1;");

        assertEquals(1, result);
    }

    @Test
    public void testAnyScriptReturnDouble() {
        Object result = v8.executeScript("1.1;");

        assertEquals(1.1, (Double) result, 0.000001);
    }

    @Test
    public void testAnyScriptReturnString() {
        Object result = v8.executeScript("'foo';");

        assertEquals("foo", result);
    }

    @Test
    public void testAnyScriptReturnBoolean() {
        Object result = v8.executeScript("false;");

        assertFalse((Boolean) result);
    }

    @Test
    public void testAnyScriptReturnsV8Object() {
        V8Object result = (V8Object) v8.executeScript("foo = {hello:'world'}; foo;");

        assertEquals("world", result.getString("hello"));
        result.release();
    }

    @Test
    public void testAnyScriptReturnsV8Array() {
        V8Array result = (V8Array) v8.executeScript("[1,2,3];");

        assertEquals(3, result.length());
        assertEquals(1, result.get(0));
        assertEquals(2, result.get(1));
        assertEquals(3, result.get(2));
        result.release();
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorAnytScript() {
        v8.executeScript("'a");
    }

    @Test
    public void testAnyScriptWithName() {
        V8Object result = (V8Object) v8.executeScript("foo = {hello:'world'}; foo;", "name", 6);

        assertEquals("world", result.getString("hello"));
        result.release();
    }

    /*** Object Script ***/
    @Test
    public void testSimpleObjectScript() {
        V8Object result = v8.executeObjectScript("foo = {hello:'world'}; foo;");

        assertEquals("world", result.getString("hello"));
        result.release();
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorObjectScript() {
        v8.executeObjectScript("'a");
    }

    @Test
    public void testResultUndefinedExceptionObjectScript() {
        V8Object result = v8.executeObjectScript("");

        assertTrue(result.isUndefined());
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeObjectScript() {
        v8.executeObjectScript("42");
    }

    @Test
    public void testNestedObjectScript() {
        V8Object result = v8.executeObjectScript("person = {name : {first : 'john', last:'smith'} }; person;");

        V8Object name = result.getObject("name");
        assertEquals("john", name.getString("first"));
        assertEquals("smith", name.getString("last"));
        result.release();
        name.release();
    }

    @Test
    public void testObjectScriptWithName() {
        V8Object result = v8.executeObjectScript("foo = {hello:'world'}; foo;", "name", 6);

        assertEquals("world", result.getString("hello"));
        result.release();
    }

    /*** Array Script ***/
    @Test
    public void testSimpleArrayScript() {
        V8Array result = v8.executeArrayScript("foo = [1,2,3]; foo;");

        assertNotNull(result);
        result.release();
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testSimpleSyntaxErrorArrayScript() {
        v8.executeArrayScript("'a");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionArrayScript() {
        v8.executeArrayScript("");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedExceptionForWrongReturnTypeArrayScript() {
        v8.executeArrayScript("42");
    }

    @Test
    public void testArrayScriptWithName() {
        V8Array result = v8.executeArrayScript("foo = [1,2,3]; foo;", "name", 7);

        assertNotNull(result);
        result.release();
    }

    /*** Int Function ***/
    @Test
    public void testSimpleIntFunction() {
        v8.executeIntegerScript("function foo() {return 1+2;}; 42");

        int result = v8.executeIntegerFunction("foo", null);

        assertEquals(3, result);
    }

    @Test
    public void testSimpleIntFunctionWithDouble() {
        v8.executeVoidScript("function foo() {return 1.2+2.9;};");

        int result = v8.executeIntegerFunction("foo", null);

        assertEquals(4, result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfIntFunction() {
        v8.executeIntegerScript("function foo() {return 'test';}; 42");

        int result = v8.executeIntegerFunction("foo", null);

        assertEquals(3, result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInIntFunction() {
        v8.executeIntegerScript("function foo() {}; 42");

        int result = v8.executeIntegerFunction("foo", null);

        assertEquals(3, result);
    }

    /*** String Function ***/
    @Test
    public void testSimpleStringFunction() {
        v8.executeVoidScript("function foo() {return 'hello';}");

        String result = v8.executeStringFunction("foo", null);

        assertEquals("hello", result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfStringFunction() {
        v8.executeVoidScript("function foo() {return 42;}");

        v8.executeStringFunction("foo", null);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInStringFunction() {
        v8.executeVoidScript("function foo() {};");

        v8.executeStringFunction("foo", null);
    }

    /*** Double Function ***/
    @Test
    public void testSimpleDoubleFunction() {
        v8.executeVoidScript("function foo() {return 3.14 + 1;}");

        double result = v8.executeDoubleFunction("foo", null);

        assertEquals(4.14, result, 0.000001);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfDoubleFunction() {
        v8.executeVoidScript("function foo() {return 'foo';}");

        v8.executeDoubleFunction("foo", null);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInDoubleFunction() {
        v8.executeVoidScript("function foo() {};");

        v8.executeDoubleFunction("foo", null);
    }

    /*** Boolean Function ***/
    @Test
    public void testSimpleBooleanFunction() {
        v8.executeVoidScript("function foo() {return true;}");

        boolean result = v8.executeBooleanFunction("foo", null);

        assertTrue(result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfBooleanFunction() {
        v8.executeVoidScript("function foo() {return 'foo';}");

        v8.executeBooleanFunction("foo", null);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForNoReturnInBooleanFunction() {
        v8.executeVoidScript("function foo() {};");

        v8.executeBooleanFunction("foo", null);
    }

    /*** Object Function ***/
    @Test
    public void testSimpleObjectFunction() {
        v8.executeVoidScript("function foo() {return {foo:true};}");

        V8Object result = v8.executeObjectFunction("foo", null);

        assertTrue(result.getBoolean("foo"));
        result.release();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfObjectFunction() {
        v8.executeVoidScript("function foo() {return 'foo';}");

        v8.executeObjectFunction("foo", null);
    }

    @Test
    public void testResultUndefinedForNoReturnInobjectFunction() {
        v8.executeVoidScript("function foo() {};");

        V8Object result = v8.executeObjectFunction("foo", null);

        assertTrue(result.isUndefined());
    }

    /*** Array Function ***/
    @Test
    public void testSimpleArrayFunction() {
        v8.executeVoidScript("function foo() {return [1,2,3];}");

        V8Array result = v8.executeArrayFunction("foo", null);

        assertEquals(3, result.length());
        result.release();
    }

    @Test(expected = V8ResultUndefined.class)
    public void testResultUndefinedForWrongReturnTypeOfArrayFunction() {
        v8.executeVoidScript("function foo() {return 'foo';}");

        v8.executeArrayFunction("foo", null);
    }

    @Test
    public void testResultUndefinedForNoReturnInArrayFunction() {
        v8.executeVoidScript("function foo() {};");

        V8Array result = v8.executeArrayFunction("foo", null);

        assertTrue(result.isUndefined());
    }

    /*** Void Function ***/
    @Test
    public void testSimpleVoidFunction() {
        v8.executeVoidScript("function foo() {x=1}");

        v8.executeVoidFunction("foo", null);

        assertEquals(1, v8.getInteger("x"));
    }

    /*** Add Int ***/
    @Test
    public void testAddInt() {
        v8.add("foo", 42);

        int result = v8.executeIntegerScript("foo");

        assertEquals(42, result);
    }

    @Test
    public void testAddIntReplaceValue() {
        v8.add("foo", 42);
        v8.add("foo", 43);

        int result = v8.executeIntegerScript("foo");

        assertEquals(43, result);
    }

    /*** Add Double ***/
    @Test
    public void testAddDouble() {
        v8.add("foo", 3.14159);

        double result = v8.executeDoubleScript("foo");

        assertEquals(3.14159, result, 0.000001);
    }

    @Test
    public void testAddDoubleReplaceValue() {
        v8.add("foo", 42.1);
        v8.add("foo", 43.1);

        double result = v8.executeDoubleScript("foo");

        assertEquals(43.1, result, 0.000001);
    }

    /*** Add String ***/
    @Test
    public void testAddString() {
        v8.add("foo", "hello, world!");

        String result = v8.executeStringScript("foo");

        assertEquals("hello, world!", result);
    }

    @Test
    public void testAddStringReplaceValue() {
        v8.add("foo", "hello");
        v8.add("foo", "world");

        String result = v8.executeStringScript("foo");

        assertEquals("world", result);
    }

    /*** Add Boolean ***/
    @Test
    public void testAddBoolean() {
        v8.add("foo", true);

        boolean result = v8.executeBooleanScript("foo");

        assertTrue(result);
    }

    @Test
    public void testAddBooleanReplaceValue() {
        v8.add("foo", true);
        v8.add("foo", false);

        boolean result = v8.executeBooleanScript("foo");

        assertFalse(result);
    }

    @Test
    public void testAddReplaceValue() {
        v8.add("foo", true);
        v8.add("foo", "test");

        String result = v8.executeStringScript("foo");

        assertEquals("test", result);
    }

    /*** Add Object ***/
    @Test
    public void testAddObject() {
        V8Object v8Object = new V8Object(v8);
        v8.add("foo", v8Object);

        V8Object result = v8.executeObjectScript("foo");

        assertNotNull(result);
        result.release();
        v8Object.release();
    }

    @Test
    public void testAddObjectReplaceValue() {
        V8Object v8ObjectFoo1 = new V8Object(v8);
        v8ObjectFoo1.add("test", true);
        V8Object v8ObjectFoo2 = new V8Object(v8);
        v8ObjectFoo2.add("test", false);

        v8.add("foo", v8ObjectFoo1);
        v8.add("foo", v8ObjectFoo2);

        boolean result = v8.executeBooleanScript("foo.test");

        assertFalse(result);
        v8ObjectFoo1.release();
        v8ObjectFoo2.release();
    }

    /*** Add Array ***/
    @Test
    public void testAddArray() {
        V8Array array = new V8Array(v8);
        v8.add("foo", array);

        V8Array result = v8.executeArrayScript("foo");

        assertNotNull(result);
        array.release();
        result.release();
    }

    /*** Get Int ***/
    @Test
    public void testGetInt() {
        v8.executeVoidScript("x = 7");

        int result = v8.getInteger("x");

        assertEquals(7, result);
    }

    @Test
    public void testGetIntFromDouble() {
        v8.executeVoidScript("x = 7.7");

        int result = v8.getInteger("x");

        assertEquals(7, result);
    }

    @Test
    public void testGetIntReplaceValue() {
        v8.executeVoidScript("x = 7; x = 8");

        int result = v8.getInteger("x");

        assertEquals(8, result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetIntWrongType() {
        v8.executeVoidScript("x = 'foo'");

        v8.getInteger("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetIntDoesNotExist() {
        v8.executeVoidScript("");

        v8.getInteger("x");
    }

    /*** Get Double ***/
    @Test
    public void testGetDouble() {
        v8.executeVoidScript("x = 3.14159");

        double result = v8.getDouble("x");

        assertEquals(3.14159, result, 0.00001);
    }

    @Test
    public void testGetDoubleReplaceValue() {
        v8.executeVoidScript("x = 7.1; x = 8.1");

        double result = v8.getDouble("x");

        assertEquals(8.1, result, 0.00001);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetDoubleWrongType() {
        v8.executeVoidScript("x = 'foo'");

        v8.getDouble("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetDoubleDoesNotExist() {
        v8.executeVoidScript("");

        v8.getDouble("x");
    }

    /*** Get String ***/
    @Test
    public void testGetString() {
        v8.executeVoidScript("x = 'hello'");

        String result = v8.getString("x");

        assertEquals("hello", result);
    }

    @Test
    public void testGetStringReplaceValue() {
        v8.executeVoidScript("x = 'hello'; x = 'world'");

        String result = v8.getString("x");

        assertEquals("world", result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetStringeWrongType() {
        v8.executeVoidScript("x = 42");

        v8.getString("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetStringDoesNotExist() {
        v8.executeVoidScript("");

        v8.getString("x");
    }

    /*** Get Boolean ***/
    @Test
    public void testGetBoolean() {
        v8.executeVoidScript("x = true");

        boolean result = v8.getBoolean("x");

        assertTrue(result);
    }

    @Test
    public void testGetBooleanReplaceValue() {
        v8.executeVoidScript("x = true; x = false");

        boolean result = v8.getBoolean("x");

        assertFalse(result);
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetBooleanWrongType() {
        v8.executeVoidScript("x = 42");

        v8.getBoolean("x");
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetBooleanDoesNotExist() {
        v8.executeVoidScript("");

        v8.getBoolean("x");
    }

    @Test
    public void testAddGet() {
        v8.add("string", "string");
        v8.add("int", 7);
        v8.add("double", 3.1);
        v8.add("boolean", true);

        assertEquals("string", v8.getString("string"));
        assertEquals(7, v8.getInteger("int"));
        assertEquals(3.1, v8.getDouble("double"), 0.00001);
        assertTrue(v8.getBoolean("boolean"));
    }

    /*** Get Array ***/
    @Test
    public void testGetV8Array() {
        v8.executeVoidScript("foo = [1,2,3]");

        V8Array array = v8.getArray("foo");

        assertEquals(3, array.length());
        assertEquals(1, array.getInteger(0));
        assertEquals(2, array.getInteger(1));
        assertEquals(3, array.getInteger(2));
        array.release();
    }

    @Test
    public void testGetMultipleV8Arrays() {
        v8.executeVoidScript("foo = [1,2,3]; " + "bar=['first', 'second']");

        V8Array fooArray = v8.getArray("foo");
        V8Array barArray = v8.getArray("bar");

        assertEquals(3, fooArray.length());
        assertEquals(2, barArray.length());

        fooArray.release();
        barArray.release();
    }

    @Test
    public void testGetNestedV8Array() {
        v8.executeVoidScript("foo = [[1,2]]");

        for (int i = 0; i < 1000; i++) {
            V8Array fooArray = v8.getArray("foo");
            V8Array nested = fooArray.getArray(0);

            assertEquals(1, fooArray.length());
            assertEquals(2, nested.length());

            fooArray.release();
            nested.release();
        }
    }

    @Test(expected = V8ResultUndefined.class)
    public void testGetArrayWrongType() {
        v8.executeVoidScript("foo = 42");

        v8.getArray("foo");
    }

    @Test()
    public void testGetArrayDoesNotExist() {
        v8.executeVoidScript("foo = 42");

        V8Array result = v8.getArray("bar");

        assertTrue(result.isUndefined());
    }

    /*** Contains ***/
    @Test
    public void testContainsKey() {
        v8.add("foo", true);

        boolean result = v8.contains("foo");

        assertTrue(result);
    }

    @Test
    public void testContainsKeyFromScript() {
        v8.executeVoidScript("bar = 3");

        assertTrue(v8.contains("bar"));
    }

    @Test
    public void testContainsMultipleKeys() {
        v8.add("true", true);
        v8.add("test", "test");
        v8.add("one", 1);
        v8.add("pi", 3.14);

        assertTrue(v8.contains("true"));
        assertTrue(v8.contains("test"));
        assertTrue(v8.contains("one"));
        assertTrue(v8.contains("pi"));
        assertFalse(v8.contains("bar"));
    }

    @Test
    public void testDoesNotContainsKey() {
        v8.add("foo", true);

        boolean result = v8.contains("bar");

        assertFalse(result);
    }

    /*** GetKeys ***/
    @Test
    public void testZeroKeys() {
        assertEquals(0, v8.getKeys().length);
    }

    @Test
    public void testGetKeys() {
        v8.add("true", true);
        v8.add("test", "test");
        v8.add("one", 1);
        v8.add("pi", 3.14);

        assertEquals(4, v8.getKeys().length);
        assertTrue(arrayContains(v8.getKeys(), "true", "test", "one", "pi"));
    }

    static boolean arrayContains(final String[] keys, final String... strings) {
        List<String> keyList = Arrays.asList(keys);
        for (String s : strings) {
            if (!keyList.contains(s)) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testReplacedKey() {
        v8.add("test", true);
        v8.add("test", "test");
        v8.add("test", 1);
        v8.add("test", 3.14);

        assertEquals(1, v8.getKeys().length);
        assertEquals("test", v8.getKeys()[0]);
    }

    @Test
    public void testGetKeysSetFromScript() {
        v8.executeVoidScript("var foo=37");

        assertEquals(1, v8.getKeys().length);
        assertEquals("foo", v8.getKeys()[0]);
    }

    /*** Global Object Prototype Manipulation ***/
    private void setupWindowAlias() {
        v8.release();
        v8 = V8.createV8Runtime("window");
        v8.executeVoidScript("function Window(){};");
        V8Object prototype = v8.executeObjectScript("Window.prototype");
        v8.setPrototype(prototype);
        prototype.release();
    }

    @Test
    public void testAccessWindowObjectInStrictMode() {
        setupWindowAlias();
        String script = "'use strict';\n"
                + "window.foo = 7;\n"
                + "true\n";

        boolean result = v8.executeBooleanScript(script);

        assertTrue(result);
        assertEquals(7, v8.executeIntegerScript("window.foo"));
    }

    @Test
    public void testWindowWindowWindowWindow() {
        setupWindowAlias();

        assertTrue(v8.executeBooleanScript("window.window.window === window"));
    }

    @Test
    public void testGlobalIsWindow() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8.executeBooleanScript("global === window"));
    }

    @Test
    public void testWindowIsGlobal() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8.executeBooleanScript("window === global"));
    }

    @Test
    public void testV8IsGlobalStrictEquals() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        V8Object global = v8.executeObjectScript("global");

        assertTrue(v8.strictEquals(global));
        assertTrue(global.strictEquals(v8));
        global.release();
    }

    @Test
    public void testV8IsGlobalEquals() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        V8Object global = v8.executeObjectScript("global");

        assertTrue(v8.equals(global));
        assertTrue(global.equals(v8));
        global.release();
    }

    @Test
    public void testV8EqualsGlobalHash() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        V8Object global = v8.executeObjectScript("global");

        assertEquals(v8.hashCode(), global.hashCode());
        global.release();
    }

    @Test
    public void testV8IsThis() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        V8Object _this = v8.executeObjectScript("this;");

        assertEquals(v8, _this);
        assertEquals(_this, v8);
        _this.release();
    }

    @Test
    public void testWindowIsGlobal2() {
        setupWindowAlias();
        v8.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8.executeBooleanScript("window === global"));
    }

    @Test
    public void testAlternateGlobalAlias() {
        v8.release();
        v8 = V8.createV8Runtime("document");
        v8.executeVoidScript("var global = Function('return this')();");

        assertTrue(v8.executeBooleanScript("global === document"));
    }

    @Test
    public void testAccessGlobalViaWindow() {
        setupWindowAlias();
        String script = "var global = {data: 0};\n" + "global === window.global";

        assertTrue(v8.executeBooleanScript(script));
    }

    @Test
    public void testwindowIsInstanceOfWindow() {
        setupWindowAlias();

        assertTrue(v8.executeBooleanScript("window instanceof Window"));
    }

    @Test
    public void testChangeToWindowPrototypeAppearsInGlobalScope() {
        setupWindowAlias();
        V8Object prototype = v8.executeObjectScript("Window.prototype");

        prototype.add("foo", "bar");
        v8.executeVoidScript("delete window.foo");

        assertEquals("bar", v8.getString("foo"));
        assertEquals("bar", v8.executeStringScript("window.foo;"));
        prototype.release();
    }

    @Test
    public void testWindowAliasForGlobalScope() {
        setupWindowAlias();

        v8.executeVoidScript("a = 1; window.b = 2;");

        assertEquals(1, v8.executeIntegerScript("window.a;"));
        assertEquals(2, v8.executeIntegerScript("b;"));
        assertTrue(v8.executeBooleanScript("window.hasOwnProperty( \"Object\" )"));
    }

    @Test
    public void testExecuteUnicodeScript() {
        String result = v8.executeStringScript("var ಠ_ಠ = function() { return '🌞' + '💐'; }; ಠ_ಠ();");

        assertEquals("🌞💐", result);
    }

    @Test
    public void testExecuteUnicodeFunction() {
        v8.executeVoidScript("var ಠ_ಠ = function() { return '🌞' + '💐'; }; ");

        assertEquals("🌞💐", v8.executeStringFunction("ಠ_ಠ", null));
    }

    @Test
    public void testCompileErrowWithUnicode() {
        try {
            v8.executeVoidScript("🌞");
        } catch (V8ScriptCompilationException e) {
            assertTrue(e.toString().contains("🌞"));
            return;
        }

        fail("Exception should have been thrown.");
    }

    @Test
    public void testExecutionExceptionWithUnicode() {
        try {
            v8.executeVoidScript("throw('🌞')");
        } catch (V8RuntimeException e) {
            assertTrue(e.toString().contains("throw('🌞"));
        }
    }

    @Test(expected = V8ScriptCompilationException.class)
    public void testInvalidJSScript() {
        String script = "x = [1,2,3];\n"
                + "y = 0;\n"
                + "\n"
                + "//A JS Script that has a compile error, int should be var\n"
                + "for (int i = 0; i < x.length; i++) {\n"
                + "  y = y + x[i];\n"
                + "}";

        v8.executeVoidScript(script, "example.js", 0);
    }

}
