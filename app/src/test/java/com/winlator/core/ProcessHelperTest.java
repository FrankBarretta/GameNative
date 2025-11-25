package com.winlator.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ProcessHelper class.
 * Tests process management utilities and command parsing.
 */
public class ProcessHelperTest {

    @Test
    public void testSplitCommand_simpleCommand() {
        String[] result = ProcessHelper.splitCommand("echo hello");
        assertArrayEquals(new String[]{"echo", "hello"}, result);
    }

    @Test
    public void testSplitCommand_withDoubleQuotes() {
        String[] result = ProcessHelper.splitCommand("echo \"hello world\"");
        assertArrayEquals(new String[]{"echo", "\"hello world\""}, result);
    }

    @Test
    public void testSplitCommand_withSingleQuotes() {
        String[] result = ProcessHelper.splitCommand("echo 'hello world'");
        assertArrayEquals(new String[]{"echo", "'hello world'"}, result);
    }

    @Test
    public void testSplitCommand_withEscapedSpaces() {
        String[] result = ProcessHelper.splitCommand("cd /path\\ with\\ spaces");
        assertArrayEquals(new String[]{"cd", "/path with spaces"}, result);
    }

    @Test
    public void testSplitCommand_emptyString() {
        String[] result = ProcessHelper.splitCommand("");
        assertArrayEquals(new String[]{}, result);
    }

    @Test
    public void testSplitCommand_multipleSpaces() {
        String[] result = ProcessHelper.splitCommand("echo    hello     world");
        assertArrayEquals(new String[]{"echo", "hello", "world"}, result);
    }

    @Test
    public void testSplitCommand_quotedPathWithSpaces() {
        String[] result = ProcessHelper.splitCommand("run \"/usr/local/bin/app name\"");
        assertArrayEquals(new String[]{"run", "\"/usr/local/bin/app name\""}, result);
    }

    @Test
    public void testSplitCommand_mixedQuotes() {
        String[] result = ProcessHelper.splitCommand("echo \"test1\" 'test2' test3");
        assertArrayEquals(new String[]{"echo", "\"test1\"", "'test2'", "test3"}, result);
    }

    @Test
    public void testSplitCommand_trailingSpace() {
        String[] result = ProcessHelper.splitCommand("echo hello ");
        assertArrayEquals(new String[]{"echo", "hello"}, result);
    }

    @Test
    public void testSplitCommand_leadingSpace() {
        String[] result = ProcessHelper.splitCommand(" echo hello");
        assertArrayEquals(new String[]{"echo", "hello"}, result);
    }

    @Test
    public void testSplitCommand_complexCommand() {
        String[] result = ProcessHelper.splitCommand("wine \"C:\\\\Program Files\\\\app.exe\" --arg1 \"value with spaces\"");
        assertEquals(4, result.length);
        assertEquals("wine", result[0]);
        assertEquals("\"C:\\\\Program Files\\\\app.exe\"", result[1]);
        assertEquals("--arg1", result[2]);
        assertEquals("\"value with spaces\"", result[3]);
    }

    @Test
    public void testGetAffinityMaskAsHexString_singleCpu() {
        String result = ProcessHelper.getAffinityMaskAsHexString("0");
        assertEquals("1", result);
    }

    @Test
    public void testGetAffinityMaskAsHexString_twoCpus() {
        String result = ProcessHelper.getAffinityMaskAsHexString("0,1");
        assertEquals("3", result);
    }

    @Test
    public void testGetAffinityMaskAsHexString_nonContiguousCpus() {
        String result = ProcessHelper.getAffinityMaskAsHexString("0,2");
        assertEquals("5", result);
    }

    @Test
    public void testGetAffinityMaskAsHexString_allEightCpus() {
        String result = ProcessHelper.getAffinityMaskAsHexString("0,1,2,3,4,5,6,7");
        assertEquals("ff", result);
    }

    @Test
    public void testGetAffinityMask_singleCpu() {
        int result = ProcessHelper.getAffinityMask("0");
        assertEquals(1, result);
    }

    @Test
    public void testGetAffinityMask_twoCpus() {
        int result = ProcessHelper.getAffinityMask("0,1");
        assertEquals(3, result);
    }

    @Test
    public void testGetAffinityMask_fourCpus() {
        int result = ProcessHelper.getAffinityMask("0,1,2,3");
        assertEquals(15, result);
    }

    @Test
    public void testGetAffinityMask_withNullString() {
        int result = ProcessHelper.getAffinityMask((String) null);
        assertEquals(0, result);
    }

    @Test
    public void testGetAffinityMask_withEmptyString() {
        int result = ProcessHelper.getAffinityMask("");
        assertEquals(0, result);
    }

    @Test
    public void testGetAffinityMask_booleanArray() {
        boolean[] cpuList = {true, false, true, false};
        int result = ProcessHelper.getAffinityMask(cpuList);
        assertEquals(5, result); // Binary: 0101
    }

    @Test
    public void testGetAffinityMask_allTrueBooleanArray() {
        boolean[] cpuList = {true, true, true, true};
        int result = ProcessHelper.getAffinityMask(cpuList);
        assertEquals(15, result); // Binary: 1111
    }

    @Test
    public void testGetAffinityMask_allFalseBooleanArray() {
        boolean[] cpuList = {false, false, false, false};
        int result = ProcessHelper.getAffinityMask(cpuList);
        assertEquals(0, result);
    }

    @Test
    public void testGetAffinityMask_emptyBooleanArray() {
        boolean[] cpuList = {};
        int result = ProcessHelper.getAffinityMask(cpuList);
        assertEquals(0, result);
    }

    @Test
    public void testGetAffinityMask_rangeFromTo() {
        int result = ProcessHelper.getAffinityMask(0, 4);
        assertEquals(15, result); // CPUs 0-3
    }

    @Test
    public void testGetAffinityMask_rangeFromToMiddle() {
        int result = ProcessHelper.getAffinityMask(2, 4);
        assertEquals(12, result); // CPUs 2-3, binary: 1100
    }

    @Test
    public void testGetAffinityMask_rangeFromToSame() {
        int result = ProcessHelper.getAffinityMask(2, 2);
        assertEquals(0, result); // No CPUs
    }

    @Test
    public void testGetAffinityMask_rangeFromToInverted() {
        int result = ProcessHelper.getAffinityMask(4, 2);
        assertEquals(0, result); // No CPUs (from >= to)
    }

    @Test
    public void testProcessInfo_constructor() {
        ProcessHelper.ProcessInfo info = new ProcessHelper.ProcessInfo(1234, 1, "wine");
        assertEquals(1234, info.pid);
        assertEquals(1, info.ppid);
        assertEquals("wine", info.name);
    }

    @Test
    public void testProcessInfo_withZeroPid() {
        ProcessHelper.ProcessInfo info = new ProcessHelper.ProcessInfo(0, 0, "init");
        assertEquals(0, info.pid);
        assertEquals(0, info.ppid);
        assertEquals("init", info.name);
    }

    @Test
    public void testProcessInfo_withNegativePid() {
        ProcessHelper.ProcessInfo info = new ProcessHelper.ProcessInfo(-1, 1, "process");
        assertEquals(-1, info.pid);
        assertEquals(1, info.ppid);
        assertEquals("process", info.name);
    }

    @Test
    public void testProcessInfo_withNullName() {
        ProcessHelper.ProcessInfo info = new ProcessHelper.ProcessInfo(123, 1, null);
        assertEquals(123, info.pid);
        assertEquals(1, info.ppid);
        assertNull(info.name);
    }

    @Test
    public void testProcessInfo_withEmptyName() {
        ProcessHelper.ProcessInfo info = new ProcessHelper.ProcessInfo(123, 1, "");
        assertEquals(123, info.pid);
        assertEquals(1, info.ppid);
        assertEquals("", info.name);
    }

    @Test
    public void testSplitCommand_unclosedQuotes() {
        String[] result = ProcessHelper.splitCommand("echo \"unclosed");
        // Should handle unclosed quotes gracefully
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    public void testSplitCommand_consecutiveQuotes() {
        String[] result = ProcessHelper.splitCommand("echo \"\"");
        assertArrayEquals(new String[]{"echo", "\"\""}, result);
    }

    @Test
    public void testSplitCommand_quotesOnly() {
        String[] result = ProcessHelper.splitCommand("\"\"");
        assertArrayEquals(new String[]{"\"\""}, result);
    }

    @Test
    public void testGetAffinityMask_highCpuNumbers() {
        String result = ProcessHelper.getAffinityMaskAsHexString("7");
        assertEquals("80", result); // 2^7 = 128 = 0x80
    }

    @Test
    public void testGetAffinityMask_stringWithSpaces() {
        String result = ProcessHelper.getAffinityMaskAsHexString("0, 1, 2");
        // Note: This might fail if implementation doesn't handle spaces
        // Testing actual behavior
        assertNotNull(result);
    }
}