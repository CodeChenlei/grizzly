/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.http.util;

import org.glassfish.grizzly.Grizzly;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a String cache for ByteChunk and CharChunk.
 *
 * @author Remy Maucherat
 */
public final class StringCache {

    /**
     * Default Logger.
     */
    private final static Logger logger = Grizzly.logger(StringCache.class);
    
    
    // ------------------------------------------------------- Static Variables

    
    /**
     * Enabled ?
     */
    static boolean byteEnabled =
        ("true".equals(System.getProperty("tomcat.util.buf.StringCache.byte.enabled", "false")));

    
    static boolean charEnabled =
        ("true".equals(System.getProperty("tomcat.util.buf.StringCache.char.enabled", "false")));

    
    static int trainThreshold =
        Integer.parseInt(System.getProperty("tomcat.util.buf.StringCache.trainThreshold", "20000"));
    

    static int cacheSize =
        Integer.parseInt(System.getProperty("tomcat.util.buf.StringCache.cacheSize", "200"));
    

    /**
     * Statistics hash map for byte chunk.
     */
    static final HashMap<ByteEntry, int[]> bcStats =
        new HashMap<ByteEntry, int[]>(cacheSize);

    
    /**
     * toString count for byte chunk.
     */
    static int bcCount = 0;
    
    
    /**
     * Cache for byte chunk.
     */
    static ByteEntry[] bcCache = null;
    

    /**
     * Statistics hash map for char chunk.
     */
    static final HashMap<CharEntry, int[]> ccStats =
        new HashMap<CharEntry, int[]>(cacheSize);


    /**
     * toString count for char chunk.
     */
    static int ccCount = 0;
    

    /**
     * Cache for char chunk.
     */
    static CharEntry[] ccCache = null;

    
    /**
     * Access count.
     */
    static int accessCount = 0;
    

    /**
     * Hit count.
     */
    static int hitCount = 0;
    

    // ------------------------------------------------------------ Properties

    
    /**
     * @return Returns the cacheSize.
     */
    public static int getCacheSize() {
        return cacheSize;
    }
    
    
    /**
     * @param cacheSize The cacheSize to set.
     */
    public static void setCacheSize(int cacheSize) {
        StringCache.cacheSize = cacheSize;
    }

    
    /**
     * @return Returns the enabled.
     */
    public static boolean getByteEnabled() {
        return byteEnabled;
    }
    
    
    /**
     * @param byteEnabled The enabled to set.
     */
    public static void setByteEnabled(boolean byteEnabled) {
        StringCache.byteEnabled = byteEnabled;
    }
    
    
    /**
     * @return Returns the enabled.
     */
    public static boolean getCharEnabled() {
        return charEnabled;
    }
    
    
    /**
     * @param charEnabled The enabled to set.
     */
    public static void setCharEnabled(boolean charEnabled) {
        StringCache.charEnabled = charEnabled;
    }
    
    
    /**
     * @return Returns the trainThreshold.
     */
    public static int getTrainThreshold() {
        return trainThreshold;
    }
    
    
    /**
     * @param trainThreshold The trainThreshold to set.
     */
    public static void setTrainThreshold(int trainThreshold) {
        StringCache.trainThreshold = trainThreshold;
    }

    
    /**
     * @return Returns the accessCount.
     */
    public static int getAccessCount() {
        return accessCount;
    }
    
    
    /**
     * @return Returns the hitCount.
     */
    public static int getHitCount() {
        return hitCount;
    }

    
    // -------------------------------------------------- Public Static Methods

    
    public static void reset() {
        hitCount = 0;
        accessCount = 0;
        synchronized (bcStats) {
            bcCache = null;
            bcCount = 0;
        }
        synchronized (ccStats) {
            ccCache = null;
            ccCount = 0;
        }
    }
    
    
    public static String toString(ByteChunk bc) {

        // If the cache is null, then either caching is disabled, or we're
        // still training
        if (bcCache == null) {
            String value = bc.toStringInternal();
            if (byteEnabled) {
                // If training, everything is synced
                synchronized (bcStats) {
                    // If the cache has been generated on a previous invocation
                    // while waiting fot the lock, just return the toString value
                    // we just calculated
                    if (bcCache != null) {
                        return value;
                    }
                    // Two cases: either we just exceeded the train count, in which
                    // case the cache must be created, or we just update the count for
                    // the string
                    if (bcCount > trainThreshold) {
                        long t1 = System.currentTimeMillis();
                        // Sort the entries according to occurrence
                        TreeMap<Integer, ArrayList<ByteEntry>> tempMap =
                            new TreeMap<Integer, ArrayList<ByteEntry>>();
                        Iterator<ByteEntry> entries =
                            bcStats.keySet().iterator();
                        while (entries.hasNext()) {
                            ByteEntry entry = entries.next();
                            int[] countA = bcStats.get(entry);
                            Integer count = countA[0];
                            // Add to the list for that count
                            ArrayList<ByteEntry> list = tempMap.get(count);
                            if (list == null) {
                                // Create list
                                list = new ArrayList<ByteEntry>();
                                tempMap.put(count, list);
                            }
                            list.add(entry);
                        }
                        // Allocate array of the right size
                        int size = bcStats.size();
                        if (size > cacheSize) {
                            size = cacheSize;
                        }
                        ByteEntry[] tempbcCache = new ByteEntry[size];
                        // Fill it up using an alphabetical order
                        // and a dumb insert sort
                        ByteChunk tempChunk = new ByteChunk();
                        int n = 0;
                        while (n < size) {
                            Object key = tempMap.lastKey();
                            ArrayList<ByteEntry> list = tempMap.get(key);
                            for (int i = 0; i < list.size() && n < size; i++) {
                                ByteEntry entry = list.get(i);
                                tempChunk.setBytes(entry.name, 0, entry.name.length);
                                int insertPos = findClosest(tempChunk, tempbcCache, n);
                                if (insertPos == n) {
                                    tempbcCache[n + 1] = entry;
                                } else {
                                    System.arraycopy(tempbcCache, insertPos + 1, tempbcCache, 
                                            insertPos + 2, n - insertPos - 1);
                                    tempbcCache[insertPos + 1] = entry;
                                }
                                n++;
                            }
                            tempMap.remove(key);
                        }
                        bcCount = 0;
                        bcStats.clear();
                        bcCache = tempbcCache;
                        if (logger.isLoggable(Level.FINEST)) {
                            long t2 = System.currentTimeMillis();
                            logger.log(Level.FINEST,"ByteCache generation time: " + (t2 - t1) + "ms");
                        }
                    } else {
                        bcCount++;
                        // Allocate new ByteEntry for the lookup
                        ByteEntry entry = new ByteEntry();
                        entry.value = value;
                        int[] count = bcStats.get(entry);
                        if (count == null) {
                            int end = bc.getEnd();
                            int start = bc.getStart();
                            // Create byte array and copy bytes
                            entry.name = new byte[bc.getLength()];
                            System.arraycopy(bc.getBuffer(), start, entry.name, 0, end - start);
                            // Set encoding
                            entry.charset = bc.getCharset();
                            // Initialize occurrence count to one 
                            count = new int[1];
                            count[0] = 1;
                            // Set in the stats hash map
                            bcStats.put(entry, count);
                        } else {
                            count[0] = count[0] + 1;
                        }
                    }
                }
            }
            return value;
        } else {
            accessCount++;
            // Find the corresponding String
            String result = find(bc);
            if (result == null) {
                return bc.toStringInternal();
            }
            // Note: We don't care about safety for the stats
            hitCount++;
            return result;
        }
        
    }


    public static String toString(CharChunk cc) {
        
        // If the cache is null, then either caching is disabled, or we're
        // still training
        if (ccCache == null) {
            String value = cc.toStringInternal();
            if (charEnabled) {
                // If training, everything is synced
                synchronized (ccStats) {
                    // If the cache has been generated on a previous invocation
                    // while waiting fot the lock, just return the toString value
                    // we just calculated
                    if (ccCache != null) {
                        return value;
                    }
                    // Two cases: either we just exceeded the train count, in which
                    // case the cache must be created, or we just update the count for
                    // the string
                    if (ccCount > trainThreshold) {
                        long t1 = System.currentTimeMillis();
                        // Sort the entries according to occurrence
                        TreeMap<Integer, ArrayList<CharEntry>> tempMap =
                            new TreeMap<Integer, ArrayList<CharEntry>>();
                        Iterator<CharEntry> entries = ccStats.keySet().iterator();
                        while (entries.hasNext()) {
                            CharEntry entry = entries.next();
                            int[] countA = ccStats.get(entry);
                            Integer count = countA[0];
                            // Add to the list for that count
                            ArrayList<CharEntry> list = tempMap.get(count);
                            if (list == null) {
                                // Create list
                                list = new ArrayList<CharEntry>();
                                tempMap.put(count, list);
                            }
                            list.add(entry);
                        }
                        // Allocate array of the right size
                        int size = ccStats.size();
                        if (size > cacheSize) {
                            size = cacheSize;
                        }
                        CharEntry[] tempccCache = new CharEntry[size];
                        // Fill it up using an alphabetical order
                        // and a dumb insert sort
                        CharChunk tempChunk = new CharChunk();
                        int n = 0;
                        while (n < size) {
                            Object key = tempMap.lastKey();
                            ArrayList<CharEntry> list = tempMap.get(key);
                            for (int i = 0; i < list.size() && n < size; i++) {
                                CharEntry entry = list.get(i);
                                tempChunk.setChars(entry.name, 0, entry.name.length);
                                int insertPos = findClosest(tempChunk, tempccCache, n);
                                if (insertPos == n) {
                                    tempccCache[n + 1] = entry;
                                } else {
                                    System.arraycopy(tempccCache, insertPos + 1, tempccCache, 
                                            insertPos + 2, n - insertPos - 1);
                                    tempccCache[insertPos + 1] = entry;
                                }
                                n++;
                            }
                            tempMap.remove(key);
                        }
                        ccCount = 0;
                        ccStats.clear();
                        ccCache = tempccCache;
                        if (logger.isLoggable(Level.FINEST)) {
                            long t2 = System.currentTimeMillis();
                            logger.log(Level.FINEST,"CharCache generation time: " + (t2 - t1) + "ms");
                        }
                    } else {
                        ccCount++;
                        // Allocate new CharEntry for the lookup
                        CharEntry entry = new CharEntry();
                        entry.value = value;
                        int[] count = ccStats.get(entry);
                        if (count == null) {
                            int end = cc.getEnd();
                            int start = cc.getStart();
                            // Create char array and copy chars
                            entry.name = new char[cc.getLength()];
                            System.arraycopy(cc.getBuffer(), start, entry.name, 0, end - start);
                            // Initialize occurrence count to one 
                            count = new int[1];
                            count[0] = 1;
                            // Set in the stats hash map
                            ccStats.put(entry, count);
                        } else {
                            count[0] = count[0] + 1;
                        }
                    }
                }
            }
            return value;
        } else {
            accessCount++;
            // Find the corresponding String
            String result = find(cc);
            if (result == null) {
                return cc.toStringInternal();
            }
            // Note: We don't care about safety for the stats
            hitCount++;
            return result;
        }
        
    }
    
    
    // ----------------------------------------------------- Protected Methods


    /**
     * Compare given byte chunk with byte array.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    protected static int compare(ByteChunk name, byte[] compareTo) {
        int result = 0;

        byte[] b = name.getBuffer();
        int start = name.getStart();
        int end = name.getEnd();
        int len = compareTo.length;

        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (b[i + start] > compareTo[i]) {
                result = 1;
            } else if (b[i + start] < compareTo[i]) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length > (end - start)) {
                result = -1;
            } else if (compareTo.length < (end - start)) {
                result = 1;
            }
        }
        return result;
    }

    
    /**
     * Find an entry given its name in the cache and return the associated String.
     */
    protected static String find(ByteChunk name) {
        int pos = findClosest(name, bcCache, bcCache.length);
        if ((pos < 0) || (compare(name, bcCache[pos].name) != 0)
                || !(name.getCharset().equals(bcCache[pos].charset))) {
            return null;
        } else {
            return bcCache[pos].value;
        }
    }

    
    /**
     * Find an entry given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    protected static int findClosest(ByteChunk name, ByteEntry[] array, int len) {

        int a = 0;
        int b = len - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        
        if (compare(name, array[0].name) < 0) {
            return -1;
        }         
        if (b == 0) {
            return 0;
        }

        int i;
        while (true) {
            i = (b + a) >>> 1;
            int result = compare(name, array[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, array[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * Compare given char chunk with char array.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    protected static int compare(CharChunk name, char[] compareTo) {
        int result = 0;

        char[] c = name.getBuffer();
        int start = name.getStart();
        int end = name.getEnd();
        int len = compareTo.length;

        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo[i]) {
                result = 1;
            } else if (c[i + start] < compareTo[i]) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length > (end - start)) {
                result = -1;
            } else if (compareTo.length < (end - start)) {
                result = 1;
            }
        }
        return result;
    }

    
    /**
     * Find an entry given its name in the cache and return the associated String.
     */
    protected static String find(CharChunk name) {
        int pos = findClosest(name, ccCache, ccCache.length);
        if ((pos < 0) || (compare(name, ccCache[pos].name) != 0)) {
            return null;
        } else {
            return ccCache[pos].value;
        }
    }

    
    /**
     * Find an entry given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    protected static int findClosest(CharChunk name, CharEntry[] array, int len) {

        int a = 0;
        int b = len - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        
        if (compare(name, array[0].name) < 0 ) {
            return -1;
        }         
        if (b == 0) {
            return 0;
        }

        int i;
        while (true) {
            i = (b + a) >>> 1;
            int result = compare(name, array[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, array[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    // -------------------------------------------------- ByteEntry Inner Class


    protected static class ByteEntry {

        public byte[] name = null;
        public Charset charset = null;
        public String value = null;

        public String toString() {
            return value;
        }
        public int hashCode() {
            return value.hashCode();
        }
        public boolean equals(Object obj) {
            return obj instanceof ByteEntry && value.equals(((ByteEntry) obj).value);
        }
        
    }


    // -------------------------------------------------- CharEntry Inner Class


    protected static class CharEntry {

        public char[] name = null;
        public String value = null;

        public String toString() {
            return value;
        }
        public int hashCode() {
            return value.hashCode();
        }
        public boolean equals(Object obj) {
            return obj instanceof CharEntry && value.equals(((CharEntry) obj).value);
        }
        
    }


}
