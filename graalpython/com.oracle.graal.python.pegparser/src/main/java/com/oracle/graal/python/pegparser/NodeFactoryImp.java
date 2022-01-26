/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser;

// TODO this class has to be moved to impl package and from this package we need to do api.

import com.oracle.graal.python.pegparser.AbstractParser.NameDefaultPair;
import com.oracle.graal.python.pegparser.AbstractParser.SlashWithDefault;
import com.oracle.graal.python.pegparser.AbstractParser.StarEtc;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.StringLiteralUtils;
import java.math.BigInteger;
import java.util.Arrays;


public class NodeFactoryImp implements NodeFactory{
    @Override
    public StmtTy createAnnAssignment(ExprTy target, ExprTy annotation, ExprTy rhs, boolean isSimple, int startOffset, int endOffset) {
        return new StmtTy.AnnAssign(target, annotation, rhs, isSimple, startOffset, endOffset);
    }

    @Override
    public StmtTy createAssignment(ExprTy[] lhs, ExprTy rhs, String typeComment, int startOffset, int endOffset) {
        return new StmtTy.Assign(lhs, rhs, typeComment, startOffset, endOffset);
    }

    @Override
    public StmtTy createAugAssignment(ExprTy lhs, ExprTy.BinOp.Operator operation, ExprTy rhs, int startOffset, int endOffset) {
        return new StmtTy.AugAssign(lhs, operation, rhs, startOffset, endOffset);
    }

    @Override
    public ExprTy createBinaryOp(ExprTy.BinOp.Operator op, ExprTy left, ExprTy right, int startOffset, int endOffset) {
        return new ExprTy.BinOp(left, op, right, startOffset, endOffset);
    }

    @Override
    public ModTy createModule(StmtTy[] statements, int startOffset, int endOffset) {
        return new ModTy.Module(statements, null, startOffset, endOffset);
    }

    @Override
    public ExprTy createBooleanLiteral(boolean value, int startOffset, int endOffset) {
        return new ExprTy.Constant(value, ExprTy.Constant.Kind.BOOLEAN, startOffset, endOffset);
    }

    @Override
    public ExprTy createNone(int startOffset, int endOffset) {
        return new ExprTy.Constant(null, ExprTy.Constant.Kind.NONE, startOffset, endOffset);
    }

    @Override
    public ExprTy createEllipsis(int startOffset, int endOffset) {
        return new ExprTy.Constant(null, ExprTy.Constant.Kind.ELLIPSIS, startOffset, endOffset);
    }

    @Override
    public ExprTy createGetAttribute(ExprTy receiver, String name, ExprContext context, int startOffset, int endOffset) {
        return new ExprTy.Attribute(receiver, name, context, startOffset, endOffset);
    }

    @Override
    public StmtTy createPass(int startOffset, int endOffset) {
        return new StmtTy.Pass(startOffset, endOffset);
    }

    @Override
    public StmtTy createBreak(int startOffset, int endOffset) {
        return new StmtTy.Break(startOffset, endOffset);
    }

    @Override
    public StmtTy createExpression(ExprTy expr) {
        return new StmtTy.Expr(expr);
    }


    @Override
    public ExprTy createCall(ExprTy target, ExprTy[] args, KeywordTy[] kwargs, int startOffset, int endOffset) {
        return new ExprTy.Call(target, args, kwargs, startOffset, endOffset);
    }

    @Override
    public StmtTy createContinue(int startOffset, int endOffset) {
        return new StmtTy.Continue(startOffset, endOffset);
    }

    @Override
    public ExprTy createYield(ExprTy value, boolean isFrom, int startOffset, int endOffset) {
        if (isFrom) {
            return new ExprTy.YieldFrom(value, startOffset, endOffset);
        } else {
            return new ExprTy.Yield(value, startOffset, endOffset);
        }
    }

    private static int digitValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            assert ch >= 'A' && ch <= 'f';
            return ch - 'A' + 10;
        }
    }

    @Override
    public ExprTy createNumber(String number, int startOffset, int endOffset) {
        int base = 10;
        int start = 0;
        boolean isFloat = false;
        boolean isComplex = false;

        if (number.startsWith("0")) {
            if (number.startsWith("0x") || number.startsWith("0X")) {
                base = 16;
                start = 2;
            } else if (number.startsWith("0o") || number.startsWith("0O")) {
                base = 8;
                start = 2;
            } else if (number.startsWith("0o") || number.startsWith("0O")) {
                base = 2;
                start = 2;
            }
        }
        if (base == 10) {
            isComplex = number.endsWith("j") || number.endsWith("J");
            if (!isComplex) {
                isFloat = number.contains(".") || number.contains("e") || number.contains("E");
            }
        }
        String value = number.replace("_", "");

        if (isComplex) {
            return new ExprTy.Constant(Double.parseDouble(value.substring(0, value.length() - 1)),
                            ExprTy.Constant.Kind.COMPLEX,
                            startOffset, endOffset);
        } else if (isFloat) {
            return new ExprTy.Constant(Double.parseDouble(value),
                            ExprTy.Constant.Kind.DOUBLE,
                            startOffset, endOffset);
        } else {
            final long max = Long.MAX_VALUE;
            final long moltmax = max / base;
            int i = start;
            long result = 0;
            int lastD;
            boolean overunder = false;
            while (i < value.length()) {
                lastD = digitValue(value.charAt(i));

                long next = result;
                if (next > moltmax) {
                    overunder = true;
                } else {
                    next *= base;
                    if (next > (max - lastD)) {
                        overunder = true;
                    } else {
                        next += lastD;
                    }
                }
                if (overunder) {
                    // overflow
                    BigInteger bigResult = BigInteger.valueOf(result);
                    BigInteger bigBase = BigInteger.valueOf(base);
                    while (i < value.length()) {
                        bigResult = bigResult.multiply(bigBase).add(BigInteger.valueOf(digitValue(value.charAt(i))));
                        i++;
                    }
                    return new ExprTy.Constant(bigResult, ExprTy.Constant.Kind.BIGINTEGER, startOffset, endOffset);
                }
                result = next;
                i++;
            }
            return new ExprTy.Constant(result, startOffset, endOffset);
        }
    }

    @Override
    public ExprTy createString(String[] values, int startOffset, int endOffset, FExprParser exprParser, ParserErrorCallback errorCb) {
        return StringLiteralUtils.createStringLiteral(values, startOffset, endOffset, this, exprParser, errorCb);
    }

    @Override
    public ExprTy createUnaryOp(ExprTy.UnaryOp.Operator op, ExprTy value, int startOffset, int endOffset) {
        return new ExprTy.UnaryOp(op, value, startOffset, endOffset);
    }

    @Override
    public ExprTy.Name createVariable(String name, int startOffset, int endOffset, ExprContext context) {
        return new ExprTy.Name(name, context, startOffset, endOffset);
    }

    @Override
    public ExprTy createStarred(ExprTy value, ExprContext context, int startOffset, int endOffset) {
        return new ExprTy.Starred(value, context, startOffset, endOffset);
    }

    @Override
    public KeywordTy createKeyword(String arg, ExprTy value, int startOffset, int endOffset) {
        return new KeywordTy(arg, value, startOffset, endOffset);
    }

    @Override
    public ArgTy createArgument(String argument, ExprTy annotation, String typeComment, int startOffset, int endOffset) {
        return new ArgTy(argument, annotation, typeComment, startOffset, endOffset);
    }

    @Override
    public ArgumentsTy createArguments(ArgTy[] slashWithoutDefault, SlashWithDefault slashWithDefault, ArgTy[] paramWithoutDefault, NameDefaultPair[] paramWithDefault, StarEtc starEtc) {
        ArgTy[] posOnlyArgs;
        if (slashWithoutDefault != null) {
            posOnlyArgs = slashWithoutDefault;
        } else if (slashWithDefault != null) {
            posOnlyArgs = Arrays.copyOf(slashWithDefault.plainNames,
                            slashWithDefault.plainNames.length +
                            slashWithDefault.namesWithDefaults.length);
            int i = slashWithDefault.plainNames.length;
            for (NameDefaultPair p : slashWithDefault.namesWithDefaults) {
                posOnlyArgs[i++] = p.name;
            }
        } else {
            posOnlyArgs = new ArgTy[0];
        }

        ArgTy[] posArgs;
        if (paramWithDefault != null) {
            int i;
            if (paramWithoutDefault != null) {
                posArgs = Arrays.copyOf(paramWithoutDefault,
                                paramWithoutDefault.length +
                                paramWithDefault.length);
                i = paramWithoutDefault.length;
            } else {
                posArgs = new ArgTy[paramWithDefault.length];
                i = 0;
            }
            for (NameDefaultPair p : paramWithDefault) {
                posArgs[i++] = p.name;
            }
        } else if (paramWithoutDefault != null) {
            posArgs = paramWithoutDefault;
        } else {
            posArgs = new ArgTy[0];
        }

        ExprTy[] posDefaults;
        int posDefaultsLen = 0;
        if (slashWithDefault != null) {
            posDefaultsLen = slashWithDefault.namesWithDefaults.length;
        }
        if (paramWithDefault != null) {
            posDefaultsLen += paramWithDefault.length;
        }
        posDefaults = new ExprTy[posDefaultsLen];
        int i = 0;
        if (slashWithDefault != null) {
            for (NameDefaultPair p : slashWithDefault.namesWithDefaults) {
                posDefaults[i++] = p.def;
            }
        }
        if (paramWithDefault != null) {
            for (NameDefaultPair p : paramWithDefault) {
                posDefaults[i++] = p.def;
            }
        }

        ArgTy[] kwOnlyArgs;
        ExprTy[] kwDefaults;
        if (starEtc != null && starEtc.kwOnlyArgs != null) {
            kwOnlyArgs = new ArgTy[starEtc.kwOnlyArgs.length];
            kwDefaults = new ExprTy[kwOnlyArgs.length];
            for (int j = 0; j < kwOnlyArgs.length; j++) {
                kwOnlyArgs[j] = starEtc.kwOnlyArgs[j].name;
                kwDefaults[j] = starEtc.kwOnlyArgs[j].def;
            }
        } else {
            kwOnlyArgs = new ArgTy[0];
            kwDefaults = new ExprTy[0];
        }

        return new ArgumentsTy(posOnlyArgs, posArgs, starEtc != null ? starEtc.varArg : null, kwOnlyArgs, kwDefaults, starEtc != null ? starEtc.kwArg : null, posDefaults, 0, 0);
    }

    @Override
    public ExprTy createComparison(ExprTy left, AbstractParser.CmpopExprPair[] pairs, int startOffset, int endOffset) {
        ExprTy.Compare.Operator[] ops = new ExprTy.Compare.Operator[pairs.length];
        ExprTy[] rights = new ExprTy[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            ops[i] = pairs[i].op;
            rights[i] = pairs[i].expr;
        }
        return new ExprTy.Compare(left, ops, rights, startOffset, endOffset);
    }

    @Override
    public ExprTy createSubscript(ExprTy receiver, ExprTy subscript, ExprContext context, int startOffset, int endOffset) {
        return new ExprTy.Subscript(receiver, subscript, context, startOffset, endOffset);
    }

    @Override
    public ExprTy createTuple(ExprTy[] values, ExprContext context, int startOffset, int endOffset) {
        return new ExprTy.Tuple(values, context, startOffset, endOffset);
    }

    @Override
    public ExprTy createList(ExprTy[] values, ExprContext context, int startOffset, int endOffset) {
        return new ExprTy.List(values, context, startOffset, endOffset);
    }

    @Override
    public ExprTy createDict(ExprTy[] keys, ExprTy[] values, int startOffset, int endOffset) {
        return new ExprTy.Dict(keys, values, startOffset, endOffset);
    }

    @Override
    public ExprTy createSet(ExprTy[] values, int startOffset, int endOffset) {
        return new ExprTy.Set(values, startOffset, endOffset);
    }

    @Override
    public ComprehensionTy createComprehension(ExprTy target, ExprTy iter, ExprTy[] ifs, boolean isAsync, int startOffset, int endOffset) {
        return new ComprehensionTy(target, iter, ifs, isAsync, startOffset, endOffset);
    }

    @Override
    public ExprTy createListComprehension(ExprTy name, ComprehensionTy[] generators, int startOffset, int endOffset) {
        return new ExprTy.ListComp(name, generators, startOffset, endOffset);
    }

    @Override
    public ExprTy createDictComprehension(AbstractParser.KeyValuePair name, ComprehensionTy[] generators, int startOffset, int endOffset) {
        return new ExprTy.DictComp(name.key, name.value, generators, startOffset, endOffset);
    }

    @Override
    public ExprTy createSetComprehension(ExprTy name, ComprehensionTy[] generators, int startOffset, int endOffset) {
        return new ExprTy.SetComp(name, generators, startOffset, endOffset);
    }

    @Override
    public ExprTy createGenerator(ExprTy name, ComprehensionTy[] generators, int startOffset, int endOffset) {
        return new ExprTy.GeneratorExp(name, generators, startOffset, endOffset);
    }

    @Override
    public StmtTy createFunctionDef(String name, ArgumentsTy args, StmtTy[] body, ExprTy returns, String typeComment, int startOffset, int endOffset) {
        return new StmtTy.FunctionDef(name, args, body, null, returns, typeComment, startOffset, endOffset);
    }

    @Override
    public StmtTy createFunctionDef(StmtTy funcDef, ExprTy[] decorators) {
        return ((StmtTy.FunctionDef) funcDef).copyWithDecorators(decorators);
    }

    @Override
    public StmtTy createWhile(ExprTy condition, StmtTy[] block, StmtTy[] elseBlock, int startOffset, int endOffset) {
        return new StmtTy.While(condition, block, elseBlock, startOffset, endOffset);
    }

    @Override
    public StmtTy createFor(ExprTy target, ExprTy iter, StmtTy[] block, StmtTy[] elseBlock, String typeComment, int startOffset, int endOffset) {
        return new StmtTy.For(target, iter, block, elseBlock, typeComment, startOffset, endOffset);
    }

    @Override
    public StmtTy createReturn(ExprTy value, int startOffset, int endOffset) {
        return new StmtTy.Return(value, startOffset, endOffset);
    }

    @Override
    public ExprTy createSlice(ExprTy start, ExprTy stop, ExprTy step, int startOffset, int endOffset) {
        return new ExprTy.Slice(step, step, step, startOffset, endOffset);
    }

    @Override
    public StmtTy createIf(ExprTy condition, StmtTy[] block, StmtTy[] orElse, int startOffset, int endOffset) {
        return new StmtTy.If(condition, block, orElse, startOffset, endOffset);
    }

    @Override
    public ExprTy createIfExpression(ExprTy condition, ExprTy then, ExprTy orElse, int startOffset, int endOffset) {
        return new ExprTy.IfExp(condition, then, orElse, startOffset, endOffset);
    }

    @Override
    public ExprTy createLambda(ArgumentsTy args, ExprTy body, int startOffset, int endOffset) {
        return new ExprTy.Lambda(args, body, startOffset, endOffset);
    }

    @Override
    public StmtTy createClassDef(ExprTy name, ExprTy call, StmtTy[] body, int startOffset, int endOffset) {
        return new StmtTy.ClassDef(((ExprTy.Name) name).id,
                        call == null ? AbstractParser.EMPTY_EXPR : ((ExprTy.Call) call).args,
                        call == null ? AbstractParser.EMPTY_KWDS : ((ExprTy.Call) call).keywords,
                        body, null, startOffset, endOffset);
    }

    @Override
    public StmtTy createClassDef(StmtTy proto, ExprTy[] decorators, int startOffset, int endOffset) {
        StmtTy.ClassDef classdef = (StmtTy.ClassDef) proto;
        return new StmtTy.ClassDef(classdef.name, classdef.bases, classdef.keywords, classdef.body, decorators, startOffset, endOffset);
    }

    @Override
    public StmtTy createNonLocal(String[] names, int startOffset, int endOffset) {
        return new StmtTy.NonLocal(names, startOffset, endOffset);
    }

    @Override
    public StmtTy createGlobal(String[] names, int startOffset, int endOffset) {
        return new StmtTy.Global(names, startOffset, endOffset);
    }

    @Override
    public ExprTy createAnd(ExprTy[] values, int startOffset, int endOffset) {
        return new ExprTy.BoolOp(ExprTy.BoolOp.Type.And, values, startOffset, endOffset);
    }

    @Override
    public ExprTy createOr(ExprTy[] values, int startOffset, int endOffset) {
        return new ExprTy.BoolOp(ExprTy.BoolOp.Type.Or, values, startOffset, endOffset);
    }
}
