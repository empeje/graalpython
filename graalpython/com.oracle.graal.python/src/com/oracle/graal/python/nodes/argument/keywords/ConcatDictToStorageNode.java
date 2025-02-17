/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.argument.keywords;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;

import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptors;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
public abstract class ConcatDictToStorageNode extends PNodeWithContext {
    public abstract HashingStorage execute(Frame frame, HashingStorage dest, Object other) throws SameDictKeyException, NonMappingException;

    @Specialization(guards = "hasBuiltinIter(other, getClassNode, lookupIter)", limit = "1")
    static HashingStorage doBuiltinDictEmptyDest(@SuppressWarnings("unused") EmptyStorage dest, PDict other,
                    @SuppressWarnings("unused") @Cached.Shared("getClassNode") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached.Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                    @Cached HashingStorageNodes.HashingStorageCopy copyNode) {
        return copyNode.execute(other.getDictStorage());
    }

    @Specialization(guards = "hasBuiltinIter(other, getClassNode, lookupIter)", limit = "1")
    static HashingStorage doBuiltinDict(VirtualFrame frame, HashingStorage dest, PDict other,
                    @SuppressWarnings("unused") @Cached.Shared("getClassNode") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached.Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                    @Cached HashingStorageNodes.HashingStorageGetItem resultGetItem,
                    @Cached HashingStorageNodes.HashingStorageSetItem resultSetItem,
                    @Cached HashingStorageNodes.HashingStorageGetIterator getIterator,
                    @Cached HashingStorageNodes.HashingStorageIteratorNext iterNext,
                    @Cached HashingStorageNodes.HashingStorageIteratorKey iterKey,
                    @Cached HashingStorageNodes.HashingStorageIteratorValue iterValue,
                    @Cached LoopConditionProfile loopProfile,
                    @Cached.Shared("cast") @Cached StringNodes.CastToTruffleStringCheckedNode castToStringNode,
                    @Cached.Shared("sameKeyProfile") @Cached BranchProfile sameKeyProfile) throws SameDictKeyException {
        HashingStorage result = dest;
        HashingStorage otherStorage = other.getDictStorage();
        HashingStorageNodes.HashingStorageIterator it = getIterator.execute(otherStorage);
        while (loopProfile.profile(iterNext.execute(otherStorage, it))) {
            Object key = iterKey.execute(otherStorage, it);
            Object value = iterValue.execute(otherStorage, it);
            if (resultGetItem.hasKey(frame, result, key)) {
                sameKeyProfile.enter();
                TruffleString keyName = castToStringNode.cast(key, ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS);
                throw new SameDictKeyException(keyName);
            }
            result = resultSetItem.execute(frame, result, key, value);
        }
        return result;
    }

    @Fallback
    static HashingStorage doMapping(VirtualFrame frame, HashingStorage dest, Object other,
                    @Cached.Shared("sameKeyProfile") @Cached BranchProfile sameKeyProfile,
                    @Cached.Shared("cast") @Cached StringNodes.CastToTruffleStringCheckedNode castToStringNode,
                    @Cached PyObjectCallMethodObjArgs callKeys,
                    @Cached IsBuiltinClassProfile errorProfile,
                    @Cached ListNodes.FastConstructListNode asList,
                    @Cached HashingStorageNodes.HashingStorageGetItem resultGetItem,
                    @Cached HashingStorageNodes.HashingStorageSetItem resultSetItem,
                    @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorage,
                    @Cached SequenceStorageNodes.GetItemScalarNode sequenceGetItem,
                    @Cached LoopConditionProfile loopProfile,
                    @Cached PyObjectGetItem getItem) throws SameDictKeyException, NonMappingException {
        HashingStorage result = dest;
        try {
            PSequence keys = asList.execute(frame, callKeys.execute(frame, other, T_KEYS));
            SequenceStorage keysStorage = getSequenceStorage.execute(keys);
            int keysLen = keysStorage.length();
            loopProfile.profileCounted(keysLen);
            for (int i = 0; loopProfile.inject(i < keysLen); i++) {
                Object key = sequenceGetItem.execute(keysStorage, i);
                if (resultGetItem.hasKey(frame, result, key)) {
                    sameKeyProfile.enter();
                    TruffleString keyName = castToStringNode.cast(key, ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS);
                    throw new SameDictKeyException(keyName);
                }
                Object value = getItem.execute(frame, other, key);
                result = resultSetItem.execute(frame, result, key, value);
            }
            return result;
        } catch (PException e) {
            e.expectAttributeError(errorProfile);
            throw new NonMappingException(other);
        }
    }

    /* CPython tests that tp_iter is dict_iter */
    protected static boolean hasBuiltinIter(PDict dict, GetClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
        return PGuards.isBuiltinDict(dict) || lookupIter.execute(getClassNode.execute(dict)) == BuiltinMethodDescriptors.DICT_ITER;
    }
}
