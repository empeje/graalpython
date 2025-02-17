/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.asyncio;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class GetAwaitableNode extends Node {
    public abstract Object execute(Frame frame, Object arg);

    @Specialization
    public Object doGenerator(PGenerator generator,
                    @Cached.Shared("notInAwait") @Cached PRaiseNode raise) {
        if (generator.isCoroutine()) {
            return generator;
        } else {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_BE_USED_AWAIT, "generator");
        }
    }

    @Specialization
    public Object doGeneric(Frame frame, Object awaitable,
                    @Cached.Shared("notInAwait") @Cached PRaiseNode raiseNoAwait,
                    @Cached.Exclusive @Cached PRaiseNode raiseNotIter,
                    @Cached(parameters = "Await") LookupSpecialMethodSlotNode findAwait,
                    @Cached TypeNodes.GetNameNode getName,
                    @Cached GetClassNode getAwaitableType,
                    @Cached GetClassNode getIteratorType,
                    @Cached CallUnaryMethodNode callAwait,
                    @Cached IteratorNodes.IsIteratorObjectNode isIterator) {
        Object type = getAwaitableType.execute(awaitable);
        Object getter = findAwait.execute(frame, type, awaitable);
        if (getter == PNone.NO_VALUE) {
            throw raiseNoAwait.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_BE_USED_AWAIT, getName.execute(type));
        }
        Object iterator = callAwait.executeObject(getter, awaitable);
        if (isIterator.execute(iterator)) {
            return iterator;
        }
        Object itType = getIteratorType.execute(iterator);
        if (itType == PythonBuiltinClassType.PCoroutine) {
            throw raiseNotIter.raise(PythonBuiltinClassType.TypeError, ErrorMessages.AWAIT_RETURN_COROUTINE);
        } else {
            throw raiseNotIter.raise(PythonBuiltinClassType.TypeError, ErrorMessages.AWAIT_RETURN_NON_ITER, getName.execute(itType));
        }
    }

    public static GetAwaitableNode create() {
        return GetAwaitableNodeGen.create();
    }

    public static GetAwaitableNode getUncached() {
        return GetAwaitableNodeGen.getUncached();
    }
}
